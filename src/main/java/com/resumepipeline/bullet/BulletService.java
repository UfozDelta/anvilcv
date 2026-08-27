package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.render.PdfCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class BulletService {

    private static final Logger log = LoggerFactory.getLogger(BulletService.class);

    // Fans out per-category LLM calls in generateBank; blocking I/O, so virtual threads.
    private static final ExecutorService PARALLEL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final BulletRepository repo;
    private final ProjectService projectService;
    private final LlmClient llm;
    private final LlmUsageService llmUsageService;
    // Read at persist time for the user's bold ceiling — see BulletTextRules.maxBoldSpans.
    private final GenerationConfigService configService;

    // Bullet preview only: render the selected bullets on a real page, no DB write.
    private final ProjectRepository projectRepo;
    private final ApplicationRenderer renderer;
    private final PdfCompiler compiler;

    public BulletService(BulletRepository repo, ProjectService projectService, LlmClient llm,
                         LlmUsageService llmUsageService, GenerationConfigService configService,
                         ProjectRepository projectRepo, ApplicationRenderer renderer, PdfCompiler compiler) {
        this.repo = repo;
        this.projectService = projectService;
        this.llm = llm;
        this.llmUsageService = llmUsageService;
        this.configService = configService;
        this.projectRepo = projectRepo;
        this.renderer = renderer;
        this.compiler = compiler;
    }

    /**
     * Compiles just these bullets onto a real resume page - no header, education or
     * skills - so a selection can be eyeballed without re-rendering (and overwriting)
     * a saved application. Nothing is persisted.
     *
     * <p>Ownership gate is the repository query: it only returns bullets whose project
     * belongs to {@code userId}, so the project ids derived from them are safe to look
     * up unscoped.
     */
    public PdfCompiler.Result preview(UUID userId, List<UUID> bulletIds) {
        if (bulletIds == null || bulletIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No bullets to preview");
        }
        Map<UUID, Bullet> byId = repo.findByIdsAndProjectUserId(bulletIds.toArray(new UUID[0]), userId)
                .stream().collect(Collectors.toMap(Bullet::getId, b -> b));
        // Caller order is display order - keep it, so the PDF matches what is on screen.
        List<Bullet> ordered = bulletIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching bullets");
        }
        Map<UUID, Project> projectById = projectRepo.findByIdIn(
                        ordered.stream().map(Bullet::getProjectId).distinct().toList()).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        return compiler.compile(renderer.renderSnippet(ordered, projectById));
    }

    public List<Bullet> listForProject(UUID userId, UUID projectId) {
        projectService.get(userId, projectId); // verify ownership
        return repo.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public Bullet create(UUID userId, UUID projectId, String text, String[] tags, String category) {
        projectService.get(userId, projectId); // verify ownership
        return repo.save(new Bullet(projectId, text, tags, category));
    }

    public Bullet update(UUID userId, UUID bulletId, String text, String[] tags) {
        Bullet b = repo.findById(bulletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found: " + bulletId));
        projectService.get(userId, b.getProjectId()); // verify ownership
        if (text != null) b.setText(text);
        if (tags != null) b.setTags(tags);
        return repo.save(b);
    }

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of("PENDING", "APPROVED", "REJECTED");

    public Bullet updateStatus(UUID userId, UUID bulletId, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
        Bullet b = repo.findById(bulletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found: " + bulletId));
        projectService.get(userId, b.getProjectId()); // verify ownership
        b.setStatus(status);
        return repo.save(b);
    }

    public void delete(UUID userId, UUID bulletId) {
        Bullet b = repo.findById(bulletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found: " + bulletId));
        projectService.get(userId, b.getProjectId()); // verify ownership
        repo.deleteById(bulletId);
    }

    /**
     * Outcome of a refit run: how many bullets were off-band to begin with, how many came back
     * rewritten into a valid band, and how many were left exactly as they were.
     */
    /** Status that marks a bullet as finished; refit skips these.  */
    private static final String APPROVED = "APPROVED";

    public record RefitOutcome(int checked, int offBand, int rewritten, int unchanged, List<Bullet> bullets) {}

    /**
     * Re-measure every bullet on a project against the user's current length bands and rewrite
     * the ones that miss. This is the post-hoc counterpart to the generation-time recovery pass:
     * bullets that were hand-added, hand-edited, or generated under different band settings have
     * never been measured, and an over-length one costs whole projects their place on the page
     * (see {@code BulletSelector}'s line budget).
     *
     * <p>A rewrite is accepted only if it is an improvement on every axis that matters. It must
     * land in a valid band, must not open weakly, and must not carry a quantity the original did
     * not already state -- the original bullet is the source context for
     * {@link BulletTextRules#fabricatedNumbers}, so a refit can shorten and rephrase but can
     * never invent a metric to reach a band. Anything failing those checks leaves the stored
     * bullet untouched: the worst case of this button is that nothing changes, never that a
     * bullet gets worse.
     */
    public RefitOutcome refit(UUID userId, UUID projectId, ProgressLog progress) {
        projectService.get(userId, projectId); // verify ownership
        GenerationConfig cfg = configService.get(userId);
        List<Bullet> all = repo.findByProjectIdOrderByCreatedAtAsc(projectId);

        // Approving a bullet is the user saying it is finished, so refit leaves it alone even
        // when it misses a band. `all` stays unfiltered below: the dedup guard must still see
        // approved text, or a rewrite could converge onto an approved bullet.
        List<Bullet> eligible = all.stream()
                .filter(b -> !APPROVED.equals(b.getStatus()))
                .toList();

        List<Bullet> offBand = eligible.stream()
                .filter(b -> BulletTextRules.decide(BulletTextRules.charCount(b.getText()), cfg)
                        != BulletTextRules.Decision.KEPT)
                .toList();

        int skipped = all.size() - eligible.size();
        String approvedNote = skipped == 0 ? "" : " (" + skipped + " approved, left alone)";
        if (offBand.isEmpty()) {
            progress.emit("All " + eligible.size() + " bullet(s) already fit the length bands" + approvedNote + ".");
            log.info("BULLET_REFIT project={} checked={} approved_skipped={} off_band=0 (no LLM call)",
                    projectId, eligible.size(), skipped);
            return new RefitOutcome(eligible.size(), 0, 0, 0, all);
        }
        progress.emit(offBand.size() + " of " + eligible.size() + " bullet(s) miss the length bands" + approvedNote + ".");

        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.RefitResult result;
        try {
            result = llm.refitBullets(new LlmClient.RefitRequest(userId, offBand.stream()
                    .map(b -> new LlmClient.BulletToRefit(b.getId().toString(), b.getText()))
                    .toList()), progress, tokens);
        } finally {
            llmUsageService.record(userId, "bullet_refit", tokens, null, projectId);
        }

        // By id, not by position: a model that drops or reorders entries must not be able to
        // write one bullet's rewrite over a different bullet's row.
        Map<String, Bullet> byId = offBand.stream()
                .collect(Collectors.toMap(b -> b.getId().toString(), b -> b));
        int maxBold = BulletTextRules.maxBoldSpans(cfg);
        // Dedup is against every OTHER bullet on the project, so a rewrite cannot converge onto
        // a bullet that already exists -- including one that was in band and never sent.
        List<String> otherTexts = new ArrayList<>(all.stream().map(Bullet::getText).toList());

        int rewritten = 0;
        for (LlmClient.BulletToRefit r : result.bullets()) {
            Bullet b = byId.remove(r.id());
            if (b == null) continue;                       // unknown or duplicated id
            String text = BulletTextRules.capBoldSpans(BulletTextRules.ensureTerminalPeriod(r.text()), maxBold);
            String reject = rejectRefit(text, b.getText(), cfg, otherTexts);
            if (reject != null) {
                progress.emit("Kept original (" + reject + "): " + abbreviate(b.getText()));
                continue;
            }
            int before = BulletTextRules.charCount(b.getText());
            otherTexts.remove(b.getText());
            otherTexts.add(text);
            b.setText(text);
            repo.save(b);
            rewritten++;
            progress.emit("Refit " + before + "c -> " + BulletTextRules.charCount(text) + "c");
        }

        int unchanged = offBand.size() - rewritten;
        log.info("BULLET_REFIT project={} checked={} approved_skipped={} off_band={} rewritten={} unchanged={}",
                projectId, eligible.size(), skipped, offBand.size(), rewritten, unchanged);
        progress.emit("Refit done: " + rewritten + " rewritten, " + unchanged + " left as they were.");
        return new RefitOutcome(eligible.size(), offBand.size(), rewritten, unchanged,
                repo.findByProjectIdOrderByCreatedAtAsc(projectId));
    }

    /**
     * Why a proposed rewrite is not good enough to replace {@code original}, or null to accept it.
     * Every check that guards generated bullets applies here too, plus one that only makes sense
     * for a rewrite: the replacement must not be measurably worse than what it replaces.
     */
    private static String rejectRefit(String text, String original, GenerationConfig cfg, List<String> others) {
        if (text.isBlank()) return "empty rewrite";
        if (text.equals(original)) return "unchanged by model";
        if (BulletTextRules.decide(BulletTextRules.charCount(text), cfg) != BulletTextRules.Decision.KEPT) {
            return "rewrite still off-band at " + BulletTextRules.charCount(text) + "c";
        }
        if (BulletTextRules.hasForbiddenOpener(text)) return "rewrite opens weakly";
        // Source context is the ORIGINAL bullet: a refit may only restate numbers it was given.
        List<String> fabricated = BulletTextRules.fabricatedNumbers(text, original);
        if (!fabricated.isEmpty()) return "rewrite invented " + String.join(", ", fabricated);
        List<String> rivals = new ArrayList<>(others);
        rivals.remove(original);
        if (BulletTextRules.isNearDuplicate(text, rivals)) return "rewrite duplicates another bullet";
        return null;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 60 ? s : s.substring(0, 57) + "...";
    }

    /** Single un-categorized generation. Persists bullets with category="general". */
    public List<Bullet> generateForProject(UUID userId, UUID projectId) {
        return generateForProjectAndCategory(userId, projectId, "general", ProgressLog.noOp());
    }

    private record RawGeneration(String category, LlmClient.BulletGenerationResult result) {}

    /** Call the LLM for one project/category. No shared state — safe to run concurrently. */
    private RawGeneration generateBulletsOnly(UUID userId, UUID projectId, String category,
                                              List<String> siblingCategories, ProgressLog progress) {
        Project p = projectService.get(userId, projectId);

        LlmClient.SourceKind sk = p.getKind() == Project.Kind.EXPERIENCE
                ? LlmClient.SourceKind.EXPERIENCE
                : LlmClient.SourceKind.PROJECT;

        String cat = (category == null || category.isBlank()) ? "general" : category;

        // Shown to the model so it writes something new instead of re-deriving what the bank
        // already holds and losing it to saveDeduped afterwards. In a generateBank run the
        // categories are in flight together, so each one sees only what was already persisted,
        // never its siblings' output — dedup still backstops that overlap.
        List<String> existing = repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(Bullet::getText)
                .toList();

        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.BulletGenerationResult result;
        try {
            result = llm.generateBullets(
                    new LlmClient.GenerateBulletsRequest(
                            userId, sk, cat,
                            p.getName(), p.getDescription(), p.getRepoContext(),
                            p.getTechStack(), p.getYourRole(), p.getOwnership(),
                            p.getScaleImpact(), p.getHardestProblem(),
                            p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(),
                            existing, siblingCategories),
                    progress, tokens);
        } finally {
            llmUsageService.record(userId, "bullet_generation", tokens, null, projectId);
        }
        return new RawGeneration(cat, result);
    }

    /**
     * Dedup + persist one category's generated bullets against {@code seenTexts}, which is
     * mutated in place so callers can chain this across categories in a batch. Must be called
     * serially — it is not safe to call concurrently against a shared seenTexts list.
     */
    private List<Bullet> saveDeduped(UUID userId, UUID projectId, RawGeneration gen,
                                     List<String> seenTexts, ProgressLog progress) {
        int maxBold = BulletTextRules.maxBoldSpans(configService.get(userId));
        List<Bullet> saved = new ArrayList<>();
        int dupDropped = 0;
        for (LlmClient.GeneratedBullet g : gen.result().bullets()) {
            if (BulletTextRules.isNearDuplicate(g.text(), seenTexts)) {
                dupDropped++;
                continue;
            }
            // Capped here, once, right before storage: every generation path (single-category
            // and the parallel bank fan-out) funnels through this method, so this is the one
            // place that guarantees every persisted bullet has been bold-capped regardless of
            // which caller produced it. Applied after the dedup check on purpose — isNearDuplicate
            // already strips ** internally (see wordSet/quantityTokens), so capping first vs.
            // after cannot change a dedup decision either way.
            String text = BulletTextRules.capBoldSpans(g.text(), maxBold);
            seenTexts.add(text);
            saved.add(repo.save(new Bullet(projectId, text, g.tags().toArray(new String[0]), gen.category())));
        }
        if (dupDropped > 0) {
            progress.emit("Dedup: dropped " + dupDropped + " near-duplicate bullet(s)");
        }
        // The companion to BULLET_GEN in BaseLlmClient: that line reports what survived the
        // filter, this one what survived dedup against the bank and its sibling categories.
        // dup_dropped is the number that decides whether the parallel-category collision is
        // worth restructuring generateBank for — it is bullets we paid full output tokens to
        // generate and then binned.
        log.info("BULLET_PERSIST project={} category={} generated={} saved={} dup_dropped={}",
                projectId, gen.category(), gen.result().bullets().size(), saved.size(), dupDropped);
        return saved;
    }

    /** Generate bullets for one project and one category lens. */
    public List<Bullet> generateForProjectAndCategory(UUID userId, UUID projectId, String category, ProgressLog progress) {
        // No siblings: a standalone generation is the only call in flight.
        RawGeneration gen = generateBulletsOnly(userId, projectId, category, List.of(), progress);
        // Fetched fresh here (not passed in) so this standalone entry point still sees any
        // bullets saved by other calls in the meantime — same behavior as before the split.
        List<String> seenTexts = new ArrayList<>(
                repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(Bullet::getText).toList());
        return saveDeduped(userId, projectId, gen, seenTexts, progress);
    }

    public List<Bullet> generateBank(UUID userId, UUID projectId, List<String> categories, ProgressLog progress) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories cannot be empty");
        }
        for (String c : categories) {
            if (!CategoryLenses.LENSES.containsKey(c)) {
                throw new IllegalArgumentException("Unknown category: " + c);
            }
        }
        int total = categories.size();
        for (int i = 0; i < total; i++) {
            progress.emit("[" + (i + 1) + "/" + total + "] Starting category: " + categories.get(i));
        }
        log.info("Generating bank for project {} categories {}", projectId, categories);

        List<CompletableFuture<RawGeneration>> futures = categories.stream()
                .map(c -> CompletableFuture.supplyAsync(
                        () -> generateBulletsOnly(userId, projectId, c,
                                categories.stream().filter(o -> !o.equals(c)).toList(),
                                tagged(progress, c)), PARALLEL_EXECUTOR))
                .toList();

        List<RawGeneration> results;
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            results = futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException e) {
            // Unwrap so e.g. ResponseStatusException(404) from projectService.get() still
            // surfaces as 404 through the synchronous endpoint, not a wrapped 500.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause.getMessage(), cause);
        }

        List<String> seenTexts = new ArrayList<>(
                repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(Bullet::getText).toList());
        List<Bullet> combined = new ArrayList<>();
        for (RawGeneration gen : results) {
            combined.addAll(saveDeduped(userId, projectId, gen, seenTexts, progress));
        }
        progress.emit("Done — generated " + combined.size() + " bullets across " + total + " categories.");
        return combined;
    }

    private static ProgressLog tagged(ProgressLog progress, String category) {
        return msg -> progress.emit("[" + category + "] " + msg);
    }
}
