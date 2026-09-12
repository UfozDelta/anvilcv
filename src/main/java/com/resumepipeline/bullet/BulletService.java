package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
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
import java.util.LinkedHashMap;
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
    // Ground-truth line-fit check used at persist/refit time (batch, not the hot generation
    // retry loop — see BulletLineMeasurer's class javadoc for why).
    private final BulletLineMeasurer measurer;
    private final BulletMeasureDiagnosticRepository diagnosticRepo;
    // Editing a bullet changes what is printed on every page that already renders it, so the
    // recruiter scorecard for those pages stops describing reality — see markStaleFor below.
    private final ApplicationRepository applicationRepo;

    public BulletService(BulletRepository repo, ProjectService projectService, LlmClient llm,
                         LlmUsageService llmUsageService, GenerationConfigService configService,
                         ProjectRepository projectRepo, ApplicationRenderer renderer, PdfCompiler compiler,
                         BulletLineMeasurer measurer, BulletMeasureDiagnosticRepository diagnosticRepo,
                         ApplicationRepository applicationRepo) {
        this.repo = repo;
        this.projectService = projectService;
        this.llm = llm;
        this.llmUsageService = llmUsageService;
        this.configService = configService;
        this.measurer = measurer;
        this.diagnosticRepo = diagnosticRepo;
        this.projectRepo = projectRepo;
        this.renderer = renderer;
        this.compiler = compiler;
        this.applicationRepo = applicationRepo;
    }

    /**
     * Mark every scored application that renders one of these bullets as out of date.
     *
     * <p>A bullet edit keeps the bullet's id, so an application's {@code selectedBulletIds} still
     * resolves and the page keeps rendering — with new text sitting beside a recruiter verdict
     * written about the old text, under a score that graded the old text, with nothing telling
     * the user. Flagging here is what makes that visible and what makes the re-score action
     * reachable. Only the selected set matters: a bullet sitting unselected in the bank never
     * reached the recruiter pass, so editing it cannot invalidate anything.
     *
     * <p>Never fails the write it follows. The edit is the user's actual request; losing it
     * because a bookkeeping flag could not be set would be the worse outcome.
     */
    private void markStaleFor(UUID userId, List<UUID> bulletIds) {
        if (bulletIds.isEmpty()) return;
        try {
            String joined = bulletIds.stream().map(UUID::toString).collect(Collectors.joining(","));
            int flagged = applicationRepo.markRecruiterStaleForBullets(userId, joined);
            if (flagged > 0) {
                log.info("Bullet edit invalidated {} scored application(s) for user {}", flagged, userId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not flag applications stale after bullet edit: {}", e.getMessage());
        }
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
        Bullet saved = repo.save(b);
        // Tags do not print, but text does — only a text change can invalidate a scorecard.
        if (text != null) markStaleFor(userId, List.of(bulletId));
        return saved;
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
        // Note this leaves any application's selectedBulletIds pointing at a row that no longer
        // exists — a separate, pre-existing bug. Flagging stale does not fix that; it only stops
        // the score claiming to describe a page that has lost a bullet.
        markStaleFor(userId, List.of(bulletId));
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

        // Real render check first (see BulletLineMeasurer): falls back to the char-count band
        // per-bullet for anything the compile didn't return (compile failure, or a bullet the
        // parser couldn't match) so a measurement outage never blocks a refit.
        Map<String, String> eligibleTexts = eligible.stream()
                .collect(Collectors.toMap(b -> b.getId().toString(), Bullet::getText));
        Map<String, BulletLineMeasurer.Measured> measured = measurer.measure(eligibleTexts);
        List<Bullet> offBand = eligible.stream()
                .filter(b -> !fitsCleanly(b.getId().toString(), b.getText(), measured, cfg))
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

        // Batch-measure every proposed rewrite in one compile up front, same as the off-band
        // pass above — rejectRefit then reads the real fit instead of re-deciding per rewrite.
        Map<String, String> rewriteTexts = new LinkedHashMap<>();
        for (LlmClient.BulletToRefit r : result.bullets()) {
            if (!byId.containsKey(r.id())) continue;
            rewriteTexts.put(r.id(), BulletTextRules.capBoldSpans(BulletTextRules.ensureTerminalPeriod(r.text()), maxBold));
        }
        Map<String, BulletLineMeasurer.Measured> rewriteMeasured = measurer.measure(rewriteTexts);

        int rewritten = 0;
        List<UUID> rewrittenIds = new ArrayList<>();
        for (LlmClient.BulletToRefit r : result.bullets()) {
            Bullet b = byId.remove(r.id());
            if (b == null) continue;                       // unknown or duplicated id
            String text = rewriteTexts.get(r.id());
            String reject = rejectRefit(text, b.getText(), cfg, otherTexts, rewriteMeasured.get(r.id()));
            if (reject != null) {
                progress.emit("Kept original (" + reject + "): " + abbreviate(b.getText()));
                continue;
            }
            int before = BulletTextRules.charCount(b.getText());
            int after = BulletTextRules.charCount(text);
            int linesBefore = BulletTextRules.estimatedLines(b.getText());
            int linesAfter = BulletTextRules.estimatedLines(text);
            boolean inBand = fitsCleanly(r.id(), text, rewriteMeasured, cfg);
            otherTexts.remove(b.getText());
            otherTexts.add(text);
            b.setText(text);
            repo.save(b);
            rewrittenIds.add(b.getId());
            rewritten++;
            progress.emit("Refit " + before + "c -> " + after + "c"
                    + (inBand ? "" : " (still off-band, but " + linesAfter
                                     + " lines instead of " + linesBefore + ")"));
        }

        int unchanged = offBand.size() - rewritten;
        log.info("BULLET_REFIT project={} checked={} approved_skipped={} off_band={} rewritten={} unchanged={}",
                projectId, eligible.size(), skipped, offBand.size(), rewritten, unchanged);
        progress.emit("Refit done: " + rewritten + " rewritten, " + unchanged + " left as they were.");
        // One statement for the whole batch, not one per bullet.
        markStaleFor(userId, rewrittenIds);
        return new RefitOutcome(eligible.size(), offBand.size(), rewritten, unchanged,
                repo.findByProjectIdOrderByCreatedAtAsc(projectId));
    }

    /**
     * Why a proposed rewrite is not good enough to replace {@code original}, or null to accept it.
     * Every check that guards generated bullets applies here too, plus one that only makes sense
     * for a rewrite: the replacement must not be measurably worse than what it replaces.
     */
    private String rejectRefit(String text, String original, GenerationConfig cfg, List<String> others,
                                BulletLineMeasurer.Measured measured) {
        if (text.isBlank()) return "empty rewrite";
        if (text.equals(original)) return "unchanged by model";
        // Band misses are graded, not fatal. A rewrite that lands just outside the band is
        // still worth taking when it renders on fewer lines than what it replaces: rejecting
        // a 203c/2-line rewrite reinstates the 318c/4-line original, which is the worse
        // bullet by the only measure that costs page space. The target stays strict -- the
        // model is still told the exact ceiling -- but the fallback prefers the better of the
        // two rather than demanding perfection.
        //
        // "Fits" prefers the real render (measured) over the char-count band whenever the
        // batch compile produced a value for this rewrite; see BulletLineMeasurer.
        boolean fits = measured != null ? measurer.isCleanFit(measured)
                : BulletTextRules.decide(BulletTextRules.charCount(text), cfg) == BulletTextRules.Decision.KEPT;
        int linesAfter = measured != null ? measured.lines() : BulletTextRules.estimatedLines(text);
        if (!fits && linesAfter >= BulletTextRules.estimatedLines(original)) {
            return "rewrite still off-band at " + BulletTextRules.charCount(text)
                    + "c and no shorter on the page";
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

    /** Real render (when the batch compile covered this id) with a char-count fallback. */
    private boolean fitsCleanly(String id, String text, Map<String, BulletLineMeasurer.Measured> measured,
                                 GenerationConfig cfg) {
        BulletLineMeasurer.Measured m = measured.get(id);
        if (m != null) return measurer.isCleanFit(m);
        return BulletTextRules.decide(BulletTextRules.charCount(text), cfg) == BulletTextRules.Decision.KEPT;
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
                            p.getTechnicalDecisions(), p.getUserImpact(), p.getSecurityPosture(),
                            p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(),
                            existing, siblingCategories),
                    progress, tokens);
        } finally {
            llmUsageService.record(userId, "bullet_generation", tokens, null, projectId);
        }
        return new RawGeneration(cat, result);
    }

    /**
     * Dedup + persist one category's generated bullets.
     *
     * <p>Two dedup strengths, because the two collisions are not the same thing:
     *
     * <ul>
     *   <li>{@code bankTexts} — what is already stored, plus what this same lens has produced
     *       so far in this call. A collision here is a genuine repeat, rejected at the normal
     *       {@link BulletTextRules#NEAR_DUPLICATE_THRESHOLD}. Mutated in place, so the list a
     *       caller passes must not be shared with a concurrent call.
     *   <li>{@code siblingTexts} — what OTHER lenses produced in this same run. A collision here
     *       is usually the fan-out working: the same work seen through two lenses, which is the
     *       choice {@code roleEmphasis} makes at application time. Only near-identical prose is
     *       rejected ({@link BulletTextRules#CROSS_LENS_THRESHOLD}); see that constant for why
     *       the resume itself is protected without deleting the variant.
     * </ul>
     *
     * <p>Must be called serially — neither list is safe to mutate concurrently.
     */
    private List<Bullet> saveDeduped(UUID userId, UUID projectId, RawGeneration gen,
                                     List<String> bankTexts, List<String> siblingTexts,
                                     ProgressLog progress) {
        int maxBold = BulletTextRules.maxBoldSpans(configService.get(userId));
        List<Bullet> saved = new ArrayList<>();
        int dupDropped = 0;
        int variantsKept = 0;
        for (LlmClient.GeneratedBullet g : gen.result().bullets()) {
            if (BulletTextRules.isNearDuplicate(g.text(), bankTexts)) {
                dupDropped++;
                continue;
            }
            if (BulletTextRules.isNearDuplicate(g.text(), siblingTexts,
                    BulletTextRules.CROSS_LENS_THRESHOLD)) {
                dupDropped++;
                continue;
            }
            // Overlaps a sibling lens but is not near-identical prose: a real alternative framing
            // of the same work, kept on purpose. Counted so the log can show the fan-out earning
            // its cost rather than just producing collisions.
            if (BulletTextRules.isNearDuplicate(g.text(), siblingTexts)) variantsKept++;
            // Capped here, once, right before storage: every generation path (single-category
            // and the parallel bank fan-out) funnels through this method, so this is the one
            // place that guarantees every persisted bullet has been bold-capped regardless of
            // which caller produced it. Applied after the dedup check on purpose — isNearDuplicate
            // already strips ** internally (see wordSet/quantityTokens), so capping first vs.
            // after cannot change a dedup decision either way.
            String text = BulletTextRules.capBoldSpans(g.text(), maxBold);
            bankTexts.add(text);
            saved.add(repo.save(new Bullet(projectId, text, g.tags().toArray(new String[0]), gen.category())));
        }
        // Published to the siblings only after this lens is done, so a lens is never compared
        // against its own output twice (bankTexts already covers that at the stricter floor).
        siblingTexts.addAll(saved.stream().map(Bullet::getText).toList());
        if (dupDropped > 0) {
            progress.emit("Dedup: dropped " + dupDropped + " near-duplicate bullet(s)");
        }
        // The companion to BULLET_GEN in BaseLlmClient: that line reports what survived the
        // filter, this one what survived dedup against the bank and its sibling categories.
        // dup_dropped is now only true repeats and near-identical prose; variants_kept is the
        // number that says whether the fan-out is producing alternative framings worth paying
        // for, and it is the pair to watch when tuning CROSS_LENS_THRESHOLD.
        log.info("BULLET_PERSIST project={} category={} generated={} saved={} dup_dropped={} variants_kept={}",
                projectId, gen.category(), gen.result().bullets().size(), saved.size(),
                dupDropped, variantsKept);
        return saved;
    }

    /** Generate bullets for one project and one category lens. */
    public List<Bullet> generateForProjectAndCategory(UUID userId, UUID projectId, String category, ProgressLog progress) {
        // No siblings: a standalone generation is the only call in flight.
        RawGeneration gen = generateBulletsOnly(userId, projectId, category, List.of(), progress);
        // Fetched fresh here (not passed in) so this standalone entry point still sees any
        // bullets saved by other calls in the meantime — same behavior as before the split.
        List<String> bankTexts = new ArrayList<>(
                repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(Bullet::getText).toList());
        // No siblings in flight, so nothing to compare at the cross-lens floor.
        return saveDeduped(userId, projectId, gen, bankTexts, new ArrayList<>(), progress);
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

        recordMeasureDiagnostics(userId, projectId, results, progress);

        // Every lens judges itself against the SAME stored-bank snapshot at the strict floor —
        // hence a fresh copy per lens, not one shared mutable list. Sharing it would put lens 1's
        // output into lens 2's strict comparison and reinstate exactly the cross-lens deletion
        // this split exists to stop. Sibling output travels in siblingTexts instead, judged at
        // CROSS_LENS_THRESHOLD, so a second framing of the same work survives.
        List<String> bankSnapshot =
                repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(Bullet::getText).toList();
        List<String> siblingTexts = new ArrayList<>();
        List<Bullet> combined = new ArrayList<>();
        for (RawGeneration gen : results) {
            combined.addAll(saveDeduped(userId, projectId, gen,
                    new ArrayList<>(bankSnapshot), siblingTexts, progress));
        }
        progress.emit("Done — generated " + combined.size() + " bullets across " + total + " categories.");
        return combined;
    }

    private static ProgressLog tagged(ProgressLog progress, String category) {
        return msg -> progress.emit("[" + category + "] " + msg);
    }

    /**
     * Shadow mode only: compares the real tectonic measurement against the char-count band
     * every one of these bullets already passed (they only reach here because callAndFilter
     * already returned {@code KEPT}), and records the comparison -- agree and disagree alike,
     * so a real disagreement RATE is computable later. Never drops or rewrites a bullet; see
     * BulletLineMeasurer's class javadoc for why generation isn't allowed to act on this yet.
     *
     * <p>One compile for the whole bank across every category, not one per category -- a
     * per-category compile would queue N deep on PdfCompiler's Semaphore(2), which is shared
     * with the user-facing bullet-preview endpoint.
     *
     * <p>Skipped entirely when the user has turned length filtering off: that setting means
     * they don't want a length gate applied to their bullets, and shadow-mode telemetry about
     * a gate they explicitly disabled is not information worth collecting.
     */
    private void recordMeasureDiagnostics(UUID userId, UUID projectId, List<RawGeneration> results,
                                          ProgressLog progress) {
        Map<String, String> textsById = new LinkedHashMap<>();
        Map<String, String> categoryById = new LinkedHashMap<>();
        for (RawGeneration gen : results) {
            List<LlmClient.GeneratedBullet> bullets = gen.result().bullets();
            for (int i = 0; i < bullets.size(); i++) {
                // Underscore, not "#": confirmed against a real compile that TeX's \write
                // family DOUBLES a literal "#" for round-trip safety ("backend#0" becomes
                // "backend##0" in the log), so BulletLineMeasurer.parse's id would never match
                // this map's keys again -- every lookup silently missed despite the compile and
                // regex both succeeding. "_" carries no such special meaning to \write.
                String id = gen.category() + "_" + i;
                textsById.put(id, bullets.get(i).text());
                categoryById.put(id, gen.category());
            }
        }
        if (textsById.isEmpty()) return;
        if (!configService.get(userId).isWordFilterEnabled()) return;

        Map<String, BulletLineMeasurer.Measured> measured = measurer.measure(textsById);
        if (measured.isEmpty() && !textsById.isEmpty()) {
            progress.emit("Measurement diagnostics unavailable this run (compile failed) — skipped.");
            return;
        }

        GenerationConfig cfg = configService.get(userId);
        List<BulletMeasureDiagnostic> rows = new ArrayList<>();
        int disagreements = 0;
        for (var e : textsById.entrySet()) {
            String id = e.getKey();
            String text = e.getValue();
            int cc = BulletTextRules.charCount(text);
            // Always true today: only KEPT bullets reach saveDeduped. Stored anyway so the
            // column means something if this is ever called on a wider set of candidates.
            boolean declaredKept = BulletTextRules.decide(cc, cfg) == BulletTextRules.Decision.KEPT;
            BulletLineMeasurer.Measured m = measured.get(id);
            boolean isMeasured = m != null;
            boolean agree = isMeasured && measurer.isCleanFit(m) == declaredKept;
            if (isMeasured && !agree) disagreements++;
            rows.add(new BulletMeasureDiagnostic(projectId, userId, categoryById.get(id), text, cc,
                    declaredKept, isMeasured, isMeasured ? m.lines() : null,
                    isMeasured ? m.lastLineFill() : null, agree));
        }
        diagnosticRepo.saveAll(rows);
        if (disagreements > 0) {
            progress.emit("Measurement diagnostics: " + disagreements + "/" + rows.size()
                    + " bullet(s) disagreed with the real render (shadow mode — nothing dropped).");
        }
    }
}
