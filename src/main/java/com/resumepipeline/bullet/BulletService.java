package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.github.RepoMapRenderer;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.obs.LogText;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.render.PdfCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BulletService {

    private static final Logger log = LoggerFactory.getLogger(BulletService.class);

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
    private final StoryRepository storyRepo;
    // A story build's bullets and story rows commit together (see saveStoryBullets).
    private final TransactionOperations tx;
    // Projects with a generateBank in flight. In memory, like ApplicationService.scoring: one
    // instance, and a restart ends the run anyway.
    private final Set<UUID> generating = ConcurrentHashMap.newKeySet();

    /** Live stories a project may hold; a build on a full bank makes no LLM call. */
    public static final int STORY_CAP = 12;
    /** Most new stories one build asks for. */
    static final int MAX_NEW_STORIES = 8;
    /** Evidence quotes shorter than this prove too little to call two stories the same work. */
    static final int MIN_OVERLAP_QUOTE = 40;
    /** Wordings the model writes per (story, lens) slot. */
    static final int CANDIDATES_PER_SLOT = 15;
    /** Wordings kept per slot. Two picks that restate each other never reach here: candidates are deduped first. */
    static final int KEEP_PER_SLOT = 2;
    /** Top candidates by code score that the judge sees. */
    static final int JUDGE_TOP_N = 5;
    /** Similarity to pick #1 costs this many judge points when choosing pick #2. */
    static final double VARIETY_PENALTY = 2.0;
    /** Pick #2 must be less similar than this to pick #1 (wordings at or above it are near-twins). */
    static final double ANGLE_SIMILARITY_MAX = 0.35;
    /** Pick #2 may trail pick #1 by at most this many judge points. */
    static final double PAIR_SCORE_MARGIN = 1.5;
    /** Code-score weights, 0-100 in total. Length fit is constant: every candidate reaching rating is in band. */
    static final int SCORE_LENGTH = 30;
    static final int SCORE_OUTCOME = 30;
    static final int SCORE_TECH = 20;
    static final int SCORE_OPENER = 20;
    private static final Pattern RESULT_VERB = Pattern.compile(
            "(?i)\\b(cut|reduc|increas|improv|sped|speed|sav|reach|halv|doubl|grew|grow|eliminat|boost|lower|raise|achiev)");

    public BulletService(BulletRepository repo, ProjectService projectService, LlmClient llm,
                         LlmUsageService llmUsageService, GenerationConfigService configService,
                         ProjectRepository projectRepo, ApplicationRenderer renderer, PdfCompiler compiler,
                         BulletLineMeasurer measurer, BulletMeasureDiagnosticRepository diagnosticRepo,
                         ApplicationRepository applicationRepo, StoryRepository storyRepo,
                         TransactionOperations tx) {
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
        this.storyRepo = storyRepo;
        this.tx = tx;
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
        markPdfStaleFor(userId, bulletIds);
    }

    /** The stored PDF of every page printing one of these bullets no longer matches. Never fails the write. */
    private void markPdfStaleFor(UUID userId, List<UUID> bulletIds) {
        try {
            String joined = bulletIds.stream().map(UUID::toString).collect(Collectors.joining(","));
            applicationRepo.markPdfStaleForBullets(userId, joined);
        } catch (RuntimeException e) {
            log.warn("Could not flag application PDFs stale after bullet edit: {}", e.getMessage());
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

    /** Story titles of this project, by story id. */
    public Map<UUID, String> storyTitles(UUID userId, UUID projectId) {
        projectService.get(userId, projectId); // verify ownership
        return storyRepo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .collect(Collectors.toMap(Story::getId, Story::getTitle));
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
        // ...but tags do print in a project heading when its tech stack is blank, so the PDF can.
        else if (tags != null) markPdfStaleFor(userId, List.of(bulletId));
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
        // Dedup is against every OTHER bullet on the project, so a rewrite cannot converge onto
        // a bullet that already exists -- including one that was in band and never sent.
        List<String> otherTexts = new ArrayList<>(all.stream().map(Bullet::getText).toList());

        // Batch-measure every proposed rewrite in one compile up front, same as the off-band
        // pass above — rejectRefit then reads the real fit instead of re-deciding per rewrite.
        Map<String, String> rewriteTexts = new LinkedHashMap<>();
        for (LlmClient.BulletToRefit r : result.bullets()) {
            if (!byId.containsKey(r.id())) continue;
            String t = BulletTextRules.ensureTerminalPeriod(r.text());
            rewriteTexts.put(r.id(), BulletTextRules.capBoldSpans(t, BulletTextRules.maxBoldSpans(cfg, t)));
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
                progress.emit("Kept original (" + reject + "): " + LogText.abbreviate(b.getText(), 60));
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


    /** Single un-categorized generation. Persists bullets with category="general". */
    public List<Bullet> generateForProject(UUID userId, UUID projectId) {
        return generateForProjectAndCategory(userId, projectId, "general", ProgressLog.noOp());
    }

    /** Everything the project row says, as one generation request. */
    private static LlmClient.GenerateBulletsRequest sourceRequest(Project p, String category, List<String> existing,
                                                                 List<String> siblings, String lensFocus) {
        LlmClient.SourceKind sk = p.getKind() == Project.Kind.EXPERIENCE
                ? LlmClient.SourceKind.EXPERIENCE
                : LlmClient.SourceKind.PROJECT;
        return new LlmClient.GenerateBulletsRequest(
                p.getUserId(), sk, category,
                p.getName(), p.getDescription(), p.getContextDescription(), p.getRepoContext(),
                p.getTechStack(), p.getYourRole(), p.getOwnership(),
                p.getScaleImpact(), p.getHardestProblem(),
                p.getTechnicalDecisions(), p.getUserImpact(), p.getSecurityPosture(),
                p.getTitle(), p.getCompany(), p.getLocation(), p.getDates(),
                existing, siblings, lensFocus);
    }

    private record RawGeneration(String category, LlmClient.BulletGenerationResult result) {}

    /** Call the LLM for one project/category. No shared state — safe to run concurrently. */
    private RawGeneration generateBulletsOnly(UUID userId, UUID projectId, String category,
                                              List<String> siblingCategories, List<String> subsystems,
                                              ProgressLog progress) {
        Project p = projectService.get(userId, projectId);
        String cat = (category == null || category.isBlank()) ? "general" : category;

        // Shown to the model so it writes something new instead of re-deriving what the bank
        // already holds and losing it to saveDeduped afterwards.
        List<String> existing = repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(Bullet::getText)
                .toList();

        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.BulletGenerationResult result;
        try {
            result = llm.generateBullets(sourceRequest(p, cat, existing, siblingCategories,
                            RepoMapRenderer.lensFocus(RepoMapRenderer.parse(p.getRepoMap()), cat, subsystems)),
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
        GenerationConfig cfg = configService.get(userId);
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
            String text = BulletTextRules.capBoldSpans(g.text(), BulletTextRules.maxBoldSpans(cfg, g.text()));
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
        RawGeneration gen = generateBulletsOnly(userId, projectId, category, List.of(), List.of(), progress);
        // Fetched fresh here (not passed in) so this standalone entry point still sees any
        // bullets saved by other calls in the meantime — same behavior as before the split.
        List<String> bankTexts = new ArrayList<>(
                repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(Bullet::getText).toList());
        // No siblings in flight, so nothing to compare at the cross-lens floor.
        return saveDeduped(userId, projectId, gen, bankTexts, new ArrayList<>(), progress);
    }

    public List<Bullet> generateBank(UUID userId, UUID projectId, List<String> categories, ProgressLog progress) {
        return generateBank(userId, projectId, categories, List.of(), progress);
    }

    /**
     * @param subsystems repo-map subsystem names the user ticked; generation then writes from
     *                   those instead of the subsystems tagged for the lenses. Empty = use the tags.
     *
     * <p>Two calls, whatever the lens count: {@code findStories} picks the project's strongest
     * pieces of work (each backed by quotes verified against the source, each tagged with the
     * requested lenses it really fits), then each new story runs one slot on its best lens: see
     * {@link #runSlot}. A lens no story fits now gets no bullets.
     *
     * <p>Additive like before: nothing already in the bank, APPROVED or not, is changed.
     *
     * <p>An unreadable story reply fails the run. There is no per-lens fallback any more.
     */
    public List<Bullet> generateBank(UUID userId, UUID projectId, List<String> categories, List<String> subsystems,
                                     ProgressLog progress) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories cannot be empty");
        }
        for (String c : categories) {
            if (!CategoryLenses.LENSES.containsKey(c)) {
                throw new IllegalArgumentException("Unknown category: " + c);
            }
        }
        log.info("Generating bank for project {} lenses {}", projectId, categories);
        progress.emit("Lenses: " + String.join(", ", categories));

        Project p = projectService.get(userId, projectId);
        if (!generating.add(projectId)) {
            throw new IllegalStateException("Bullets are already being generated for this project — wait for that run to finish.");
        }
        try {
            return generateStories(userId, projectId, p, categories, subsystems, progress);
        } finally {
            generating.remove(projectId);
        }
    }

    private List<Bullet> generateStories(UUID userId, UUID projectId, Project p, List<String> categories,
                                         List<String> subsystems, ProgressLog progress) {
        List<Bullet> existing = repo.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<Story> storyRows = storyRepo.findByProjectIdOrderByCreatedAtAsc(projectId);
        Set<UUID> liveIds = liveStoryIds(existing);
        int room = Math.min(MAX_NEW_STORIES, STORY_CAP - liveIds.size());
        if (room <= 0) {
            progress.emit("Story bank full (" + STORY_CAP + ") — nothing generated. Reject a story's wordings to make room.");
            return List.of();
        }
        LlmClient.GenerateBulletsRequest source = sourceRequest(p, "general",
                existing.stream().map(Bullet::getText).toList(), List.of(),
                RepoMapRenderer.lensFocus(RepoMapRenderer.parse(p.getRepoMap()), categories, subsystems));
        LlmClient.StoryRequest req = new LlmClient.StoryRequest(source, categories,
                bankCoverage(existing, storyRows), room);

        TokenAccumulator tokens = new TokenAccumulator();
        List<LlmClient.Story> stories;
        try {
            List<LlmClient.Story> found = llm.findStories(req, progress, tokens).stories();
            if (found.isEmpty()) {
                progress.emit("No new stories found — nothing generated.");
                return List.of();
            }
            // Deleted (fully rejected) stories count too: a dismissed story is never re-found.
            stories = dropRepeats(found, storyRows);
            if (stories.isEmpty()) {
                progress.emit("All stories duplicated existing ones — nothing generated.");
                return List.of();
            }
            if (stories.size() < found.size()) {
                progress.emit("Dropped " + (found.size() - stories.size()) + " story(ies) repeating the bank.");
            }
            if (stories.size() > room) stories = stories.subList(0, room);
        } finally {
            llmUsageService.record(userId, "bullet_generation", tokens, null, projectId);
        }

        // One slot per new story, on its best lens. Each slot dedups against the bank and against
        // the wordings kept so far in this run.
        List<String> bankTexts = existing.stream().map(Bullet::getText).toList();
        List<String> seen = new ArrayList<>(bankTexts);
        List<LlmClient.GeneratedBullet> kept = new ArrayList<>();
        TokenAccumulator candidateTokens = new TokenAccumulator();
        TokenAccumulator judgeTokens = new TokenAccumulator();
        try {
            for (LlmClient.Story s : stories) {
                List<LlmClient.GeneratedBullet> slot = runSlot(source, s, s.lenses().get(0), seen, List.of(),
                        p.getTechStack(), candidateTokens, judgeTokens, progress);
                slot.forEach(g -> seen.add(g.text()));
                kept.addAll(slot);
            }
        } finally {
            llmUsageService.record(userId, "story_candidates", candidateTokens, null, projectId);
            llmUsageService.record(userId, "story_judge", judgeTokens, null, projectId);
        }

        // Grouped by lens so the shadow-mode measurement keeps its per-category ids.
        Map<String, List<LlmClient.GeneratedBullet>> byLens = new LinkedHashMap<>();
        kept.forEach(g -> byLens.computeIfAbsent(g.lens(), k -> new ArrayList<>()).add(g));
        recordMeasureDiagnostics(userId, projectId, byLens.entrySet().stream()
                .map(e -> new RawGeneration(e.getKey(), new LlmClient.BulletGenerationResult(e.getValue())))
                .toList(), progress);

        List<Bullet> saved = saveStoryBullets(userId, projectId, kept, stories, bankTexts, progress);
        progress.emit("Done — generated " + saved.size() + " bullets from " + stories.size() + " stories.");
        return saved;
    }

    /** The project's live stories, oldest first. A story with no live wording is deleted and not listed. */
    public List<Story> listStories(UUID userId, UUID projectId) {
        projectService.get(userId, projectId);
        Set<UUID> live = liveStoryIds(repo.findByProjectIdOrderByCreatedAtAsc(projectId));
        return storyRepo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(st -> live.contains(st.getId())).toList();
    }

    /**
     * Synchronous checks for a wordings run, so a bad request is answered before a job starts:
     * unknown lens (400), a story not in this project (404), a deleted story or a busy project (409).
     */
    public void checkWordings(UUID userId, UUID projectId, UUID storyId, List<String> lenses) {
        if (lenses == null || lenses.isEmpty()) throw new IllegalArgumentException("Pick at least one lens.");
        for (String l : lenses) {
            if (!CategoryLenses.LENSES.containsKey(l)) throw new IllegalArgumentException("Unknown lens: " + l);
        }
        projectService.get(userId, projectId);
        storyRepo.findById(storyId).filter(st -> projectId.equals(st.getProjectId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found: " + storyId));
        if (liveWordings(projectId, storyId).isEmpty()) {
            throw new IllegalStateException("That story is deleted. Undo a wording or add a new story.");
        }
        if (generating.contains(projectId)) {
            throw new IllegalStateException("Bullets are already being generated for this project — wait for that run to finish.");
        }
    }

    /**
     * More wordings for one story: one slot per chosen lens (repeats run again; a lens the story
     * does not carry is a weak fit and still runs). Existing live wordings are passed as already
     * written. The new wordings join the story and widen its lenses; no story row is added.
     */
    public List<Bullet> generateWordings(UUID userId, UUID projectId, UUID storyId, List<String> lenses,
                                         List<String> subsystems, ProgressLog progress) {
        checkWordings(userId, projectId, storyId, lenses);
        if (!generating.add(projectId)) {
            throw new IllegalStateException("Bullets are already being generated for this project — wait for that run to finish.");
        }
        try {
            Project p = projectService.get(userId, projectId);
            Story story = storyRepo.findById(storyId).orElseThrow();
            List<Bullet> bank = repo.findByProjectIdOrderByCreatedAtAsc(projectId);
            List<String> bankTexts = bank.stream().map(Bullet::getText).toList();
            List<String> liveTexts = liveWordings(projectId, storyId).stream().map(Bullet::getText).toList();
            LlmClient.Story llmStory = new LlmClient.Story(storyId.toString(), story.getTitle(),
                    story.getEvidence().length > 0 ? List.of(story.getEvidence()) : liveTexts,
                    List.of(story.getLenses()));
            LlmClient.GenerateBulletsRequest source = sourceRequest(p, "general", bankTexts, List.of(),
                    RepoMapRenderer.lensFocus(RepoMapRenderer.parse(p.getRepoMap()), lenses, subsystems));

            List<String> seen = new ArrayList<>(bankTexts);
            List<LlmClient.GeneratedBullet> kept = new ArrayList<>();
            TokenAccumulator candidateTokens = new TokenAccumulator();
            TokenAccumulator judgeTokens = new TokenAccumulator();
            try {
                for (String lens : lenses) {
                    List<String> written = new ArrayList<>(liveTexts);
                    kept.forEach(g -> written.add(g.text()));
                    List<LlmClient.GeneratedBullet> slot = runSlot(source, llmStory, lens, seen, written,
                            p.getTechStack(), candidateTokens, judgeTokens, progress);
                    slot.forEach(g -> seen.add(g.text()));
                    kept.addAll(slot);
                }
            } finally {
                llmUsageService.record(userId, "story_candidates", candidateTokens, null, projectId);
                llmUsageService.record(userId, "story_judge", judgeTokens, null, projectId);
            }

            Map<String, List<LlmClient.GeneratedBullet>> byLens = new LinkedHashMap<>();
            kept.forEach(g -> byLens.computeIfAbsent(g.lens(), k -> new ArrayList<>()).add(g));
            recordMeasureDiagnostics(userId, projectId, byLens.entrySet().stream()
                    .map(e -> new RawGeneration(e.getKey(), new LlmClient.BulletGenerationResult(e.getValue())))
                    .toList(), progress);

            List<Bullet> saved = saveStoryBullets(userId, projectId, kept, List.of(llmStory), bankTexts, progress, story);
            progress.emit("Done — added " + saved.size() + " wording(s) to the story.");
            return saved;
        } finally {
            generating.remove(projectId);
        }
    }

    private List<Bullet> liveWordings(UUID projectId, UUID storyId) {
        return repo.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(b -> storyId.equals(b.getStoryId()) && !"REJECTED".equals(b.getStatus()))
                .toList();
    }

    /** Progress prefix for one slot, e.g. {@code [backend · "Ledger service"]}. */
    static String slotTag(String lens, String title) {
        return "[" + lens + " · \"" + title + "\"]";
    }

    /**
     * One (story, lens) slot: write candidates, code-filter them (in the LLM layer), drop repeats of
     * {@code seen}, rate by code score, let the judge pick from the top {@link #JUDGE_TOP_N}, and keep
     * up to {@link #KEEP_PER_SLOT}. A judge that cannot be read falls back to the top by score.
     */
    private List<LlmClient.GeneratedBullet> runSlot(LlmClient.GenerateBulletsRequest source, LlmClient.Story story,
                                                    String lens, List<String> seen, List<String> alreadyWritten,
                                                    String stack, TokenAccumulator candidateTokens,
                                                    TokenAccumulator judgeTokens, ProgressLog progress) {
        String tag = slotTag(lens, story.title());
        LlmClient.SlotCandidates batch = llm.writeSlotCandidates(source, story, lens, CANDIDATES_PER_SLOT,
                alreadyWritten, progress, candidateTokens);
        progress.emit(tag + " written " + batch.written() + " / filtered -" + batch.filtered());

        List<String> known = new ArrayList<>(seen);
        List<LlmClient.Candidate> unique = new ArrayList<>();
        int dups = 0;
        for (LlmClient.Candidate c : batch.kept()) {
            if (BulletTextRules.isNearDuplicate(c.text(), known)) {
                dups++;
                continue;
            }
            known.add(c.text());
            unique.add(c);
        }

        Set<String> bankOpeners = seen.stream().map(BulletTextRules::openingVerb).collect(Collectors.toSet());
        Map<String, Integer> batchOpeners = new HashMap<>();
        unique.forEach(c -> batchOpeners.merge(BulletTextRules.openingVerb(c.text()), 1, Integer::sum));
        List<LlmClient.Candidate> ranked = unique.stream()
                .sorted(Comparator.comparingInt((LlmClient.Candidate c) -> codeScore(c, stack, bankOpeners, batchOpeners)).reversed())
                .toList();
        List<LlmClient.Candidate> top = ranked.stream().limit(JUDGE_TOP_N).toList();
        progress.emit(tag + " duplicates -" + dups + " / scored " + unique.size() + ", judging top " + top.size());

        List<LlmClient.Candidate> chosen = new ArrayList<>();
        if (!top.isEmpty()) {
            chosen.addAll(judgeTop(story, lens, top, judgeTokens, progress));
            if (chosen.isEmpty()) {
                progress.emit(tag + " judge unreadable - top by score");
                chosen.addAll(top.stream().limit(KEEP_PER_SLOT).toList());
            }
        }
        progress.emit(tag + " kept " + chosen.size());
        return chosen.stream()
                .map(c -> new LlmClient.GeneratedBullet(c.text(), c.tags(), story.id(), lens, c.angle(), c.note()))
                .toList();
    }

    /**
     * Two judge runs over the top candidates: one in a seeded shuffle (the seed comes from the slot,
     * so a rerun asks in the same order) and one in reverse, so position bias cancels out. Scores
     * are averaged. Pick #1 is the best average. Pick #2 is drawn only from candidates within one
     * point of the best, and is the best of those after a variety penalty for resembling pick #1.
     * Empty when neither run is readable. Logs one line per slot: pick #1's code rank and whether
     * the two runs agreed on the best candidate.
     */
    private List<LlmClient.Candidate> judgeTop(LlmClient.Story story, String lens, List<LlmClient.Candidate> top,
                                               TokenAccumulator judgeTokens, ProgressLog progress) {
        int n = top.size();
        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < n; i++) shuffled.add(i);
        Collections.shuffle(shuffled, new Random((story.id() + "|" + lens).hashCode()));
        List<Integer> reversed = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) reversed.add(i);

        double[] sum = new double[n];
        // Each candidate's note comes from the first run that gave one.
        String[] notes = new String[n];
        List<Integer> bests = new ArrayList<>();
        for (List<Integer> order : List.of(shuffled, reversed)) {
            List<LlmClient.JudgeScore> scores = llm.scoreCandidates(story, order.stream().map(top::get).toList(),
                    progress, judgeTokens);
            if (scores == null || scores.size() != n) continue;
            int best = -1;
            int bestScore = 0;
            for (int k = 0; k < n; k++) {
                int idx = order.get(k);
                int s = scores.get(k).score();
                sum[idx] += s;
                if (notes[idx] == null) notes[idx] = scores.get(k).note();
                if (best < 0 || s > bestScore || (s == bestScore && idx < best)) {
                    best = idx;
                    bestScore = s;
                }
            }
            bests.add(best);
        }
        int runs = bests.size();
        if (runs == 0) return List.of();

        double[] avg = new double[n];
        for (int i = 0; i < n; i++) avg[i] = sum[i] / runs;
        int first = 0;
        for (int i = 1; i < n; i++) if (avg[i] > avg[first]) first = i;
        log.info("BULLET_JUDGE story={} pick_code_rank={} runs={} agreed={}", story.id(), first + 1, runs,
                runs == 2 && bests.get(0).equals(bests.get(1)));

        List<LlmClient.Candidate> out = new ArrayList<>();
        LlmClient.Candidate one = top.get(first);
        out.add(new LlmClient.Candidate(one.text(), one.tags(), one.angle(), notes[first]));
        if (KEEP_PER_SLOT >= 2) {
            int second = -1;
            double secondVariety = 0;
            int sameAngle = 0, nearTwin = 0, outOfMargin = 0;
            for (int i = 0; i < n; i++) {
                if (i == first) continue;
                LlmClient.Candidate c = top.get(i);
                if (avg[i] < avg[first] - PAIR_SCORE_MARGIN) { outOfMargin++; continue; }
                double sim = BulletTextRules.similarity(c.text(), one.text());
                if (sim >= ANGLE_SIMILARITY_MAX) { nearTwin++; continue; }
                // A missing angle on either side cannot prove a difference, so similarity alone decides.
                if (c.angle() != null && one.angle() != null && c.angle().equals(one.angle())) { sameAngle++; continue; }
                double variety = avg[i] - VARIETY_PENALTY * sim;
                if (second < 0 || avg[i] > avg[second] || (avg[i] == avg[second] && variety > secondVariety)) {
                    second = i;
                    secondVariety = variety;
                }
            }
            if (second >= 0) {
                LlmClient.Candidate two = top.get(second);
                out.add(new LlmClient.Candidate(two.text(), two.tags(), two.angle(), notes[second]));
            } else {
                log.info("BULLET_PAIR story={} single: no second wording qualifies (same_angle={} near_twin={} out_of_margin={})",
                        story.id(), sameAngle, nearTwin, outOfMargin);
            }
        }
        return out;
    }


    /**
     * Code score 0-100: length fit (constant, see SCORE_LENGTH), outcome (a number, or a result
     * verb without one), specific tech (a tag the project's stack names; some tag but none of
     * them; or none), and an opener that is fresh against the bank and this batch.
     */
    static int codeScore(LlmClient.Candidate c, String stack, Set<String> bankOpeners, Map<String, Integer> batchOpeners) {
        String opener = BulletTextRules.openingVerb(c.text());
        int opening = bankOpeners.contains(opener) ? 0
                : batchOpeners.getOrDefault(opener, 0) > 1 ? SCORE_OPENER / 2 : SCORE_OPENER;
        int tech = c.tags().isEmpty() ? 0
                : c.tags().stream().anyMatch(t -> KeywordScorer.mentions(stack == null ? "" : stack, t))
                        ? SCORE_TECH : SCORE_TECH / 2;
        int outcome = c.text().matches("(?s).*\\d.*") ? SCORE_OUTCOME
                : RESULT_VERB.matcher(c.text()).find() ? SCORE_OUTCOME / 2 : 0;
        return SCORE_LENGTH + outcome + tech + opening;
    }

    /** How evidence quotes are compared: lowercased, markdown stripped, whitespace collapsed. */
    static String normalizeQuote(String quote) {
        return quote.toLowerCase(Locale.ROOT).replaceAll("[*_`#>]", "").replaceAll("\\s+", " ").strip();
    }

    /**
     * Drops found stories that repeat a saved story (live or dismissed) or an earlier story of this run: at
     * least half of its quotes of {@value #MIN_OVERLAP_QUOTE}+ chars sit inside one of their
     * quotes, or its title is a near-duplicate of theirs ({@link BulletTextRules#isNearDuplicate}).
     */
    static List<LlmClient.Story> dropRepeats(List<LlmClient.Story> found, List<Story> live) {
        List<String> seenQuotes = new ArrayList<>();
        List<String> seenTitles = new ArrayList<>();
        for (Story s : live) {
            for (String e : s.getEvidence()) seenQuotes.add(normalizeQuote(e));
            seenTitles.add(s.getTitle());
        }
        List<LlmClient.Story> kept = new ArrayList<>();
        for (LlmClient.Story s : found) {
            List<String> quotes = s.evidence().stream().map(BulletService::normalizeQuote)
                    .filter(q -> q.length() >= MIN_OVERLAP_QUOTE).toList();
            long inside = quotes.stream().filter(q -> seenQuotes.stream().anyMatch(o -> o.contains(q))).count();
            if ((!quotes.isEmpty() && inside * 2 >= quotes.size())
                    || BulletTextRules.isNearDuplicate(s.title(), seenTitles)) {
                continue;
            }
            kept.add(s);
            s.evidence().forEach(e -> seenQuotes.add(normalizeQuote(e)));
            seenTitles.add(s.title());
        }
        return kept;
    }

    static final int COVERED_WORK_MAX = 30;
    static final int COVERED_WORK_CHARS = 200;

    /**
     * A story is live while at least one of its wordings is not REJECTED. The one definition of
     * "live": used for what findStories is shown, the overlap check and the story cap.
     */
    static Set<UUID> liveStoryIds(List<Bullet> bullets) {
        return bullets.stream()
                .filter(b -> b.getStoryId() != null && !"REJECTED".equals(b.getStatus()))
                .map(Bullet::getStoryId)
                .collect(Collectors.toSet());
    }

    /** What findStories is shown of the bank: live stories, storyless bullets, dismissed stories. */
    static LlmClient.BankCoverage bankCoverage(List<Bullet> bank, List<Story> storyRows) {
        Set<UUID> live = liveStoryIds(bank);
        Set<UUID> withBullets = bank.stream().map(Bullet::getStoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new LlmClient.BankCoverage(
                storyRows.stream().filter(s -> live.contains(s.getId()))
                        .map(s -> new LlmClient.KnownStory(s.getTitle(), List.of(s.getLenses()))).toList(),
                bank.stream().filter(b -> b.getStoryId() == null && !"REJECTED".equals(b.getStatus()))
                        .map(b -> LogText.abbreviate(b.getText(), COVERED_WORK_CHARS))
                        .limit(COVERED_WORK_MAX).toList(),
                // Every wording rejected: the user dismissed the work itself.
                storyRows.stream().filter(s -> withBullets.contains(s.getId()) && !live.contains(s.getId()))
                        .map(Story::getTitle).toList());
    }

    /**
     * Persist a story run. Each model story id becomes one fresh UUID shared by its wordings,
     * and a story row under that UUID once at least one of its wordings is kept. One
     * transaction, bullets first: bullet.story_id's foreign key is checked at commit (V41).
     *
     * <p>Dedup against the stored bank and against OTHER stories is the normal strict check —
     * a different story restating a claim is a repeat. Against the same story only near-identical
     * prose is dropped ({@link BulletTextRules#CROSS_LENS_THRESHOLD}): two wordings of one story
     * are meant to share facts, and selection keeps them off the same resume by storyId.
     */
    List<Bullet> saveStoryBullets(UUID userId, UUID projectId, List<LlmClient.GeneratedBullet> bullets,
                                  List<LlmClient.Story> stories, List<String> bankTexts, ProgressLog progress) {
        return saveStoryBullets(userId, projectId, bullets, stories, bankTexts, progress, null);
    }

    /** {@code attachTo}: an existing story the wordings join; null = each story id gets a new row. */
    List<Bullet> saveStoryBullets(UUID userId, UUID projectId, List<LlmClient.GeneratedBullet> bullets,
                                  List<LlmClient.Story> stories, List<String> bankTexts, ProgressLog progress,
                                  Story attachTo) {
        return tx.execute(status -> saveStoryBulletsNow(userId, projectId, bullets, stories, bankTexts, progress, attachTo));
    }

    private List<Bullet> saveStoryBulletsNow(UUID userId, UUID projectId, List<LlmClient.GeneratedBullet> bullets,
                                             List<LlmClient.Story> stories, List<String> bankTexts,
                                             ProgressLog progress, Story attachTo) {
        GenerationConfig cfg = configService.get(userId);
        Map<String, UUID> storyUuid = new LinkedHashMap<>();
        Map<String, List<String>> textsByStory = new LinkedHashMap<>();
        List<Bullet> saved = new ArrayList<>();
        int dupDropped = 0;
        for (LlmClient.GeneratedBullet g : bullets) {
            List<String> sameStory = textsByStory.computeIfAbsent(g.storyId(), k -> new ArrayList<>());
            List<String> others = new ArrayList<>(bankTexts);
            textsByStory.forEach((k, v) -> { if (!k.equals(g.storyId())) others.addAll(v); });
            if (BulletTextRules.isNearDuplicate(g.text(), others)
                    || BulletTextRules.isNearDuplicate(g.text(), sameStory, BulletTextRules.CROSS_LENS_THRESHOLD)) {
                dupDropped++;
                continue;
            }
            String text = BulletTextRules.capBoldSpans(g.text(), BulletTextRules.maxBoldSpans(cfg, g.text()));
            sameStory.add(text);
            Bullet b = new Bullet(projectId, text, g.tags().toArray(new String[0]), g.lens());
            b.setAngle(g.angle());
            b.setStoryId(storyUuid.computeIfAbsent(g.storyId(),
                    k -> attachTo == null ? UUID.randomUUID() : attachTo.getId()));
            saved.add(repo.save(b));
        }
        Map<String, LlmClient.Story> storyById = new LinkedHashMap<>();
        stories.forEach(s -> storyById.put(s.id(), s));
        List<Story> rows = new ArrayList<>();
        storyUuid.forEach((key, uuid) -> {
            LlmClient.Story s = storyById.get(key);
            if (s == null) return;
            rows.add(new Story(uuid, projectId, s.title(), s.evidence().toArray(new String[0]),
                    s.lenses().toArray(new String[0])));
        });
        if (attachTo != null) {
            if (!saved.isEmpty()) {
                Set<String> lenses = new java.util.LinkedHashSet<>(List.of(attachTo.getLenses()));
                saved.forEach(b -> lenses.add(b.getCategory()));
                attachTo.setLenses(lenses.toArray(new String[0]));
                storyRepo.save(attachTo);
            }
        } else {
            storyRepo.saveAll(rows);
        }
        if (dupDropped > 0) progress.emit("Dedup: dropped " + dupDropped + " near-duplicate bullet(s)");
        log.info("BULLET_PERSIST project={} stories={} generated={} saved={} dup_dropped={}",
                projectId, storyUuid.size(), bullets.size(), saved.size(), dupDropped);
        return saved;
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
