package com.resumepipeline.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.jd.JdFetcher;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.obs.Mdc;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.progress.PipelineTimer;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.render.PdfCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final ExecutorService PARALLEL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ApplicationRepository repo;
    private final OutcomeHistoryRepository outcomeHistoryRepo;
    private final BulletRepository bulletRepo;
    private final ProjectRepository projectRepo;
    private final JdFetcher jdFetcher;
    private final LlmClient llm;
    private final ApplicationRenderer renderer;
    private final PdfCompiler compiler;
    private final ProfileService profileService;
    private final LlmUsageService llmUsageService;
    private final SkillRowMeasurer skillRowMeasurer;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> SKILL_LABELS = Map.of(
            "languages",  "Languages",
            "frameworks", "Frameworks",
            "databases",  "Databases \\& AI",
            "devops",     "DevOps \\& Tools"
    );

    public ApplicationService(ApplicationRepository repo, OutcomeHistoryRepository outcomeHistoryRepo,
                              BulletRepository bulletRepo,
                              ProjectRepository projectRepo, JdFetcher jdFetcher, LlmClient llm,
                              ApplicationRenderer renderer, PdfCompiler compiler,
                              ProfileService profileService, LlmUsageService llmUsageService,
                              SkillRowMeasurer skillRowMeasurer) {
        this.repo = repo;
        this.outcomeHistoryRepo = outcomeHistoryRepo;
        this.bulletRepo = bulletRepo;
        this.projectRepo = projectRepo;
        this.jdFetcher = jdFetcher;
        this.llm = llm;
        this.renderer = renderer;
        this.compiler = compiler;
        this.skillRowMeasurer = skillRowMeasurer;
        this.profileService = profileService;
        this.llmUsageService = llmUsageService;
    }

    public List<Application> list(UUID userId, String outcome) {
        return outcome == null || outcome.isBlank()
                ? repo.findAllByUserIdOrderByCreatedAtDesc(userId)
                : repo.findByUserIdAndOutcomeOrderByCreatedAtDesc(userId, outcome);
    }

    public Application get(UUID userId, UUID id) {
        return repo.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }

    public void delete(UUID userId, UUID id) {
        Application a = get(userId, id);
        repo.deleteById(a.getId());
    }

    public Application updateOutcome(UUID userId, UUID id, String outcome) {
        Application a = get(userId, id);
        // Re-marking the outcome already in effect is not a transition. Logging it anyway
        // piles up rows the flow diagram has to collapse again on every read.
        boolean changed = !outcome.equals(a.getOutcome());
        a.setOutcome(outcome);
        Application saved = repo.save(a);
        if (changed) outcomeHistoryRepo.save(new OutcomeHistory(a.getId(), outcome));
        return saved;
    }

    public List<OutcomeHistory> outcomeHistory(UUID userId) {
        return outcomeHistoryRepo.findAllByUserId(userId);
    }

    public Application create(UUID userId, String jdText, String jdUrl, String roleEmphasis, boolean includeCoverLetter, ProgressLog progress) {
        if ((jdText == null || jdText.isBlank()) && (jdUrl == null || jdUrl.isBlank())) {
            throw new IllegalArgumentException("Provide jdText or jdUrl");
        }
        if (jdUrl != null && !jdUrl.isBlank() && (jdText == null || jdText.isBlank())) {
            progress.emit("Fetching JD from URL: " + jdUrl);
            PipelineTimer tFetch = PipelineTimer.start("JD fetch");
            jdText = jdFetcher.fetch(jdUrl);
            tFetch.stop(jdText.length() + " chars");
            progress.emit("Fetched JD (" + jdText.length() + " chars)");
        }

        TokenAccumulator tokens = new TokenAccumulator();
        PipelineTimer tTotal = PipelineTimer.start("total pipeline");
        Application a = new Application();
        try {

        // Stage: clean JD — strips boilerplate and extracts role/company/keywords
        PipelineTimer tClean = PipelineTimer.start("cleanJd");
        LlmClient.JdCleanResult clean = llm.cleanJd(jdText, progress, tokens);
        tClean.stop();

        // Stage: rank bullets — sends top candidates to LLM for scoring against the JD
        List<Bullet> allBullets = bulletRepo.findSelectableByProjectUserId(userId);
        if (allBullets.isEmpty()) {
            throw new IllegalStateException("No bullets in the bank — generate or add some first.");
        }

        // Fetch all user projects up front — needed for kind-aware pre-filter and selection.
        Map<UUID, Project> projectById = projectRepo.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        // Pre-filter: round-robin across projects (top 4 bullets per project by keyword score),
        // then global top-25. Prevents bullet-heavy projects from crowding out all other entries.
        Set<String> kwLower = clean.keywords().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        java.util.function.ToLongFunction<Bullet> keywordScore = KeywordScorer.score(kwLower);
        List<Bullet> candidates = allBullets.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId))
                .values().stream()
                .flatMap(group -> collapseVariants(group.stream()
                        .sorted(Comparator.comparingLong(keywordScore).reversed())
                        .toList()).stream()
                        .limit(4))
                .sorted(Comparator.comparingLong(keywordScore).reversed())
                .limit(25)
                .toList();

        progress.emit("Pre-filter: " + allBullets.size() + " total bullets → top " + candidates.size()
                + " by tag overlap with JD keywords (" + clean.keywords().size() + " keywords)"
                + " across " + candidates.stream().map(Bullet::getProjectId).distinct().count() + " projects");

        List<LlmClient.BulletForMatch> bulletsForMatch = candidates.stream()
                .map(b -> new LlmClient.BulletForMatch(
                        b.getId().toString(),
                        b.getText(),
                        Arrays.asList(b.getTags() == null ? new String[0] : b.getTags()),
                        projectById.containsKey(b.getProjectId()) ? projectById.get(b.getProjectId()).getName() : ""))
                .toList();

        // Fire ranking (always) and cover letter (optional) in parallel.
        progress.emit("Ranking " + candidates.size() + " candidates against JD...");

        // Fetch profile once — used for both courses and skills extraction below.
        com.resumepipeline.profile.Profile profile = profileService.get(userId);

        // Collect all courses from profile education entries (split comma-separated strings).
        List<String> allCourses = profileService.readEducation(profile).stream()
                .filter(e -> e.coursework() != null && !e.coursework().isBlank())
                .flatMap(e -> Arrays.stream(e.coursework().split(",")))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .distinct()
                .toList();

        // Collect the 4 selectable skill categories (interests excluded — personal, not JD-matchable).
        List<LlmClient.SkillCategory> skillCategories = buildSkillCategories(profile);

        // Fit score runs against the whole profile and project history, so it does not depend
        // on the ranking. Fire it now so its latency hides inside the ranking call.
        List<LlmClient.ProjectSummary> projectSummaries = projectById.values().stream()
                .map(p -> new LlmClient.ProjectSummary(
                        nz(p.getName()), p.getKind().name(), nz(p.getTitle()), nz(p.getDates()), nz(p.getDescription())))
                .toList();
        CompletableFuture<LlmClient.FitResult> fitFuture = CompletableFuture.supplyAsync(Mdc.wrap(() ->
                llm.scoreFit(new LlmClient.FitRequest(clean.cleanJd(), clean.company(), clean.role(),
                        clean.keywords(), roleEmphasis, skillCategories, projectSummaries), progress, tokens)),
                PARALLEL_EXECUTOR);

        LlmClient.RankRequest rankReq = new LlmClient.RankRequest(
                clean.cleanJd(), clean.company(), clean.role(),
                clean.keywords(), roleEmphasis, bulletsForMatch, allCourses, skillCategories);

        PipelineTimer tRank = PipelineTimer.start("rank (" + candidates.size() + " bullets)");
        LlmClient.RankResult rank = llm.rankBullets(rankReq, progress, tokens);
        tRank.stop();

        // A missing badge is a nuisance; a lost resume is a bug — so a failed or malformed
        // fit score never fails the pipeline. Score and verdict stay null, arrays stay empty.
        LlmClient.FitResult fit = null;
        try {
            fit = fitFuture.join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Fit scoring failed: {}", cause.getMessage());
            progress.emit("Fit score unavailable: " + cause.getMessage());
        }

        // Server-side selection: greedy top-N capped per project, then kind-floor + min-fill.
        // Logic lives in BulletSelector so it can be unit-tested without the LLM/DB stubs.
        Map<UUID, Bullet> bulletById = candidates.stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        List<LlmClient.RankedBullet> rankedSorted = rank.rankedBullets().stream()
                .sorted(Comparator.comparingInt(LlmClient.RankedBullet::rank))
                .toList();

        progress.emit("Selecting up to " + BulletSelector.MAX_ENTRIES + " entries at "
                + BulletSelector.MAX_PER_PROJECT + " bullets each...");
        List<Bullet> selected = BulletSelector.select(rankedSorted, bulletById, projectById, allBullets, kwLower);

        // Rebuild the per-project / per-kind summary for the progress stream.
        LinkedHashMap<String, Integer> perProjectName = new LinkedHashMap<>();
        for (Bullet b : selected) {
            Project p = projectById.get(b.getProjectId());
            perProjectName.merge(p != null ? p.getName() : "unknown", 1, Integer::sum);
        }
        long expDistinct = selected.stream().map(Bullet::getProjectId).distinct()
                .filter(pid -> { Project p = projectById.get(pid); return p != null && p.getKind() == Project.Kind.EXPERIENCE; })
                .count();
        long projDistinct = selected.stream().map(Bullet::getProjectId).distinct()
                .filter(pid -> { Project p = projectById.get(pid); return p != null && p.getKind() == Project.Kind.PROJECT; })
                .count();

        // Bullet count alone doesn't predict page overflow — a bullet can render as 1-4+
        // lines (BulletTextRules.estimatedLines) — so warn off the same rendered-line
        // budget BulletSelector selects against, not a raw count threshold.
        int estimatedLines = selected.stream()
                .mapToInt(b -> BulletTextRules.estimatedLines(b.getText())).sum();
        if (estimatedLines > BulletSelector.MAX_TOTAL_LINES) {
            progress.emit("Warning: ~" + estimatedLines + " bullet lines selected — PDF may exceed one page.");
        }

        progress.emit("Selection complete - " + selected.size() + " bullets"
                + " (" + expDistinct + " exp, " + projDistinct + " proj):");
        perProjectName.forEach((proj, cnt) ->
                progress.emit("  " + proj + " - " + cnt + " bullet" + (cnt > 1 ? "s" : "")));

        List<String> selectedCourses = rank.selectedCourses() == null ? List.of() : rank.selectedCourses();

        // Skill-floor pass: pad each category up to the minimum from raw profile skills.
        Map<String, List<String>> rawSkills = Map.of(
                "languages",  splitCsv(profile.getSkillsLanguages()),
                "frameworks", splitCsv(profile.getSkillsFrameworks()),
                "databases",  splitCsv(profile.getSkillsDatabases()),
                "devops",     splitCsv(profile.getSkillsDevops())
        );
        Map<String, List<String>> floorFilledSkills = BulletSelector.fillSkills(rank.selectedSkills(), rawSkills);
        Map<String, List<String>> filledSkills = stretchSkillsToWidth(floorFilledSkills, rawSkills);
        progress.emit("Skills filled: languages=" + filledSkills.get("languages").size()
                + " fw=" + filledSkills.get("frameworks").size()
                + " db=" + filledSkills.get("databases").size()
                + " devops=" + filledSkills.get("devops").size());

        // Recruiter pass grades the page, so it needs exactly what lands on it — the post-select
        // bullets, the filled skills and the selected courses, never the bank or the rank order.
        // Fired here so its latency hides inside the LaTeX render + tectonic compile.
        List<LlmClient.RenderedBullet> renderedBullets = selected.stream()
                .map(b -> new LlmClient.RenderedBullet(
                        b.getId().toString(), b.getText(),
                        projectById.containsKey(b.getProjectId()) ? projectById.get(b.getProjectId()).getName() : ""))
                .toList();
        // orTimeout: callJsonWithRetry retries over a 120s provider timeout, so an unbounded
        // join can add minutes AFTER the PDF is already compiled — the user would sit on a
        // finished resume waiting for a badge. A timeout is treated as any other failure.
        //
        // 90s, not the 15s this shipped with. This is the largest generation in the pipeline —
        // a verdict plus a written reason for every rendered bullet — and 15s killed it on
        // every single run: observed 42.6s to a valid 4474-char response, against 48.2s for
        // rank and 12.2s for fit on the same provider. Every application ever generated had a
        // null recruiterScore because of it. Note orTimeout abandons the future without
        // cancelling the HTTP call, so an over-tight bound still pays for the tokens and then
        // discards the answer — the cap has to clear real p99 latency, not merely exist.
        CompletableFuture<LlmClient.RecruiterResult> recruiterFuture = CompletableFuture.supplyAsync(Mdc.wrap(() ->
                llm.reviewResume(new LlmClient.RecruiterRequest(clean.cleanJd(), clean.company(), clean.role(),
                        clean.keywords(), roleEmphasis, renderedBullets, filledSkills, selectedCourses),
                        progress, tokens)), PARALLEL_EXECUTOR)
                .orTimeout(90, java.util.concurrent.TimeUnit.SECONDS);

        // ATS report, narrowed to what actually lands on the page.
        //
        // The LLM is asked for keywords appearing in "the top 8 bullets", but the rendered
        // set is whatever BulletSelector returns — never literally the top 8, since passes
        // 2-4 evict, pad from the raw bank, and drop whole entries. The LLM also matches
        // semantically ("React" from "front-end work"), which overstates what an ATS scanner
        // — a literal keyword matcher — will find. So keep its list and intersect it with a
        // literal match against the rendered text.
        //
        // The corpus is everything the template emits, not just bullets: the skills block and
        // coursework render too (see ApplicationRenderer), so a keyword living only in
        // skills_devops is on the PDF and must not be reported missing.
        Set<String> llmMatched = rank.atsMatched().stream()
                .map(String::toLowerCase).collect(Collectors.toSet());
        AtsReport ats = atsReport(clean.keywords(), llmMatched, selected, filledSkills, selectedCourses);
        progress.emit("ATS on rendered page: " + ats.matched().size() + "/" + clean.keywords().size()
                + " matched (LLM claimed " + rank.atsMatched().size() + ")");

        // Stage: render LaTeX
        progress.emit("Rendering LaTeX...");
        PipelineTimer tRender = PipelineTimer.start("LaTeX render");
        String tex = renderer.render(userId, selected, projectById, selectedCourses, filledSkills);
        tRender.stop();

        // Fire cover letter in parallel with tectonic compile — cover letter gets
        // actual selected bullet texts, and tectonic (5-15s) hides most of the LLM latency.
        if (includeCoverLetter) {
            progress.emit("Compiling PDF + generating cover letter in parallel...");
        } else {
            progress.emit("Compiling PDF via tectonic...");
            progress.emit("Cover letter: skipped");
        }

        List<String> selectedTexts = selected.stream().map(Bullet::getText).toList();
        CompletableFuture<PdfCompiler.Result> pdfFuture = CompletableFuture
                .supplyAsync(Mdc.wrap(() -> compiler.compile(tex)), PARALLEL_EXECUTOR);
        CompletableFuture<String> coverLetterFuture = includeCoverLetter
                ? CompletableFuture.supplyAsync(Mdc.wrap(() -> llm.coverLetter(
                        new LlmClient.CoverLetterRequest(clean.cleanJd(), clean.company(), clean.role(), roleEmphasis, selectedTexts),
                        progress, tokens)), PARALLEL_EXECUTOR)
                : CompletableFuture.completedFuture(null);

        PipelineTimer tPdf = PipelineTimer.start("tectonic + cover letter");
        PdfCompiler.Result r;
        String coverLetterText;
        try {
            r = pdfFuture.get();
            coverLetterText = coverLetterFuture.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Pipeline failed: " + cause.getMessage(), cause);
        }
        tPdf.stop("success=" + r.success());

        // Same policy as the fit score: a missing scorecard is a nuisance, a lost resume is a
        // bug — so a failed, malformed or timed-out recruiter pass never fails the pipeline.
        // The null check matters as much as the catch: a mocked/unstubbed client returns null
        // from join() without ever throwing.
        LlmClient.RecruiterResult recruiter = null;
        try {
            recruiter = recruiterFuture.join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Recruiter pass failed: {}", cause.getMessage());
            progress.emit("Recruiter pass unavailable: " + cause.getMessage());
        }

        // Estimate and truth side by side, so BulletTextRules.estimatedLines can be
        // recalibrated later against real compiles instead of guesses.
        log.info("Page budget: estimated {} lines (max {}), tectonic reported {} page(s)",
                estimatedLines, BulletSelector.MAX_TOTAL_LINES, r.pageCount());
        // Ground truth from the engine's own font metrics, not a char-count guess: XeTeX
        // itself couldn't fit a line in the column. The skills-row macro in resume.tex
        // auto-shrinks to dodge this, but flag it regardless of which section triggered it.
        if (r.success() && r.overfullHbox()) {
            log.warn("tectonic reported an overfull hbox — a line may be clipped or spill past the margin.");
            progress.emit("Warning: a line in the rendered PDF is too wide for its column (overfull hbox).");
        }

        a.setUserId(userId);
        a.setJdText(jdText);
        a.setJdUrl(jdUrl);
        a.setRoleEmphasis(roleEmphasis);
        a.setCompany(clean.company());
        a.setRole(clean.role());
        a.setCoverLetter(coverLetterText);

        // Same no-fabrication rule the bullets go through, applied to the one artifact the
        // LLM writes as free prose. Source context is the selected bullets plus the JD: a
        // letter may restate a metric it was given, and may cite the employer's own figures
        // ("your 500-person org") because those come from the posting. Anything else is
        // invented. Flagged rather than dropped -- a cover letter is a single artifact, and
        // binning it wholesale is worse for the user than showing them which figure to check.
        List<String> coverFlags = coverLetterText == null ? List.of()
                : BulletTextRules.fabricatedNumbers(
                        coverLetterText, String.join(" ", selectedTexts) + " " + clean.cleanJd());
        if (!coverFlags.isEmpty()) {
            progress.emit("Cover letter states figures not in your bullets or the JD ("
                    + String.join(", ", coverFlags) + ") - verify before sending");
        }
        a.setCoverLetterFlags(coverFlags.toArray(new String[0]));
        if (fit != null) {
            a.setFitScore(fit.overall());
            a.setFitVerdict(fit.verdict());
            a.setFitStrengths(fit.strengths().toArray(new String[0]));
            a.setFitGaps(fit.gaps().toArray(new String[0]));
            try {
                a.setFitDimensions(mapper.writeValueAsString(
                        Map.of("technical", fit.technical(), "experience", fit.experience())));
            } catch (JsonProcessingException e) {
                a.setFitDimensions("{}");
            }
        }
        applyRecruiter(a, recruiter);
        a.setPageCount(r.success() ? r.pageCount() : null);
        a.setAtsMatched(ats.matched().toArray(new String[0]));
        a.setAtsMissing(ats.missing().toArray(new String[0]));
        a.setSelectedBulletIds(selected.stream().map(Bullet::getId).toArray(UUID[]::new));
        a.setSelectedCourses(selectedCourses.toArray(new String[0]));
        try {
            a.setSelectedSkills(mapper.writeValueAsString(filledSkills));
        } catch (JsonProcessingException e) {
            a.setSelectedSkills("{}");
        }
        a.setTexBlob(tex.getBytes(StandardCharsets.UTF_8));
        try {
            a.setBulletRanking(mapper.writeValueAsString(rankedSorted));
        } catch (JsonProcessingException e) {
            a.setBulletRanking("[]");
        }
        if (r.success()) {
            a.setPdfBlob(r.pdf());
            a.setTectonicLog(r.log());
            progress.emit("Done - PDF compiled (" + r.pdf().length / 1024 + " KB).");
        } else {
            log.warn("tectonic failed: {}", r.error());
            a.setTectonicLog("FAILED: " + r.error() + "\n\n" + r.log());
            progress.emit("PDF compile failed: " + r.error());
            // Emit last few non-blank tectonic log lines so the user can debug without opening backend logs.
            if (r.log() != null && !r.log().isBlank()) {
                String[] tecLines = r.log().split("\n");
                int start = Math.max(0, tecLines.length - 6);
                for (int i = start; i < tecLines.length; i++) {
                    String l = tecLines[i].strip();
                    if (!l.isBlank()) progress.emit("tectonic: " + l);
                }
            }
        }
        a.setLlmPromptTokens(tokens.getPromptTokens());
        a.setLlmCandidatesTokens(tokens.getCandidatesTokens());
        a.setLlmCostUsd(tokens.getCostUsd());
        a.setPipelineDurationMs(tTotal.stop());
        progress.emit("LLM cost: $" + tokens.getCostUsd().toPlainString()
                + " (" + tokens.getPromptTokens() + " in / " + tokens.getCandidatesTokens() + " out)"
                + " pipeline: " + a.getPipelineDurationMs() + "ms");
        Application saved = repo.save(a);
        outcomeHistoryRepo.save(new OutcomeHistory(saved.getId(), saved.getOutcome()));
        llmUsageService.record(userId, "application_pipeline", tokens, saved.getId(), null);
        log.info("APP_CREATE app={} jd_chars={} bullets={} cover={} ms={}",
                shortId(saved.getId()), jdText.length(), selected.size(),
                includeCoverLetter, a.getPipelineDurationMs());
        return saved;

        } catch (RuntimeException e) {
            tTotal.stop("FAILED");
            throw e;
        }
    }

    /**
     * Writes a recruiter scorecard onto the application, or records that the page is unscored
     * when the pass failed.
     *
     * <p>The stale flag is part of the same decision, which is why it lives here rather than at
     * the call site. It used to be set to false unconditionally, so a failed, malformed or
     * timed-out recruiter pass stored a null score against a not-stale flag - the UI then drew a
     * bare em dash with no alert tone, indistinguishable from an application that had never been
     * scored at all. A failure is now stale: the score is absent AND known to be out of date,
     * which is exactly the state the re-score action exists to clear.
     */
    private void applyRecruiter(Application a, LlmClient.RecruiterResult recruiter) {
        if (recruiter == null) {
            a.setRecruiterStale(true);
            return;
        }
        a.setRecruiterScore(recruiter.overall());
        a.setRecruiterVerdict(recruiter.verdict());
        try {
            a.setRecruiterDimensions(mapper.writeValueAsString(Map.of(
                    "evidenceStrength", recruiter.evidenceStrength(),
                    "relevanceDensity", recruiter.relevanceDensity())));
        } catch (JsonProcessingException e) {
            a.setRecruiterDimensions("{}");
        }
        try {
            a.setRecruiterBulletVerdicts(mapper.writeValueAsString(recruiter.bulletVerdicts()));
        } catch (JsonProcessingException e) {
            a.setRecruiterBulletVerdicts("[]");
        }
        a.setRecruiterWeaknesses(recruiter.weaknesses() == null
                ? new String[0] : recruiter.weaknesses().toArray(new String[0]));
        a.setRecruiterThinnestRequirement(recruiter.thinnestRequirement());
        a.setRecruiterWeakestBulletId(parseUuid(recruiter.weakestBulletId()));
        a.setRecruiterStale(false);
    }

    /**
     * Cap on the stored JD text fed back into a re-score. The generate pipeline scores against
     * {@code cleanJd}, the LLM-condensed JD, but only the raw paste is persisted - so a re-score
     * has to work from that. Mirrors BaseLlmClient's own JD cap (package-private there) so a
     * 200k-character careers-page paste cannot push the recruiter call past its timeout.
     */
    private static final int RESCORE_MAX_JD_CHARS = 30_000;

    /**
     * Re-run the recruiter pass against the selection currently on the page.
     *
     * <p>This is the only way to score an application after it is created: the generate pipeline
     * is the sole other caller of {@link LlmClient#reviewResume}, and rerender/refit deliberately
     * make no LLM calls. Without it a scorecard that failed at generation time - or one marked
     * stale by a later edit - stayed that way permanently, and the UI told the user to rebuild
     * the PDF to re-score, which did nothing.
     *
     * <p>Makes one LLM call and no PDF compile: the page is not re-selected or re-rendered, only
     * re-judged. Callers must run it off the request thread (the pass has been observed at 42.6s).
     */
    public Application rescore(UUID userId, UUID applicationId, ProgressLog progress) {
        Application a = get(userId, applicationId);
        UUID[] selectedIds = a.getSelectedBulletIds();
        if (selectedIds.length == 0) {
            throw new IllegalStateException("Nothing on the page to score - render a selection first.");
        }

        Map<UUID, Bullet> bulletById = bulletRepo.findByIdsAndProjectUserId(selectedIds, userId).stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        // Preserve page order, and drop ids whose bullet has since been deleted from the bank.
        List<Bullet> selected = Arrays.stream(selectedIds)
                .map(bulletById::get).filter(Objects::nonNull).toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("Every bullet on this page has been deleted - re-render first.");
        }
        Set<UUID> projectIds = selected.stream().map(Bullet::getProjectId).collect(Collectors.toSet());
        Map<UUID, Project> projectById = projectRepo.findByIdIn(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        List<LlmClient.RenderedBullet> renderedBullets = selected.stream()
                .map(b -> new LlmClient.RenderedBullet(
                        b.getId().toString(), b.getText(),
                        projectById.containsKey(b.getProjectId()) ? projectById.get(b.getProjectId()).getName() : ""))
                .toList();

        // Same reconstruction rerender uses: the original keyword list is the union of the two
        // ATS buckets, since every keyword landed in exactly one of them.
        Set<String> keywords = new LinkedHashSet<>(Arrays.asList(a.getAtsMatched()));
        keywords.addAll(Arrays.asList(a.getAtsMissing()));

        String jd = a.getJdText() == null ? "" : a.getJdText();
        if (jd.length() > RESCORE_MAX_JD_CHARS) jd = jd.substring(0, RESCORE_MAX_JD_CHARS);

        List<String> selectedCourses = a.getSelectedCourses() == null ? List.of() : Arrays.asList(a.getSelectedCourses());
        Map<String, List<String>> selectedSkills = parseSelectedSkills(a.getSelectedSkills());

        PipelineTimer tRescore = PipelineTimer.start("rescore");
        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.RecruiterResult recruiter = null;
        try {
            recruiter = llm.reviewResume(new LlmClient.RecruiterRequest(jd, a.getCompany(), a.getRole(),
                    List.copyOf(keywords), a.getRoleEmphasis(), renderedBullets, selectedSkills, selectedCourses),
                    progress, tokens);
        } catch (RuntimeException e) {
            // Same policy as the generate pipeline: a missing scorecard is a nuisance, and a
            // 500 here would lose the user the page they were editing. Report it and leave the
            // application stale so the button stays available.
            log.warn("Re-score failed: {}", e.getMessage());
            progress.emit("Re-score failed: " + e.getMessage());
        }
        tRescore.stop("scored=" + (recruiter != null));

        applyRecruiter(a, recruiter);
        Application saved = repo.save(a);
        llmUsageService.record(userId, "application_rescore", tokens, saved.getId(), null);
        return saved;
    }

    /** Override selection and re-render. Does NOT re-call the LLM. */
    public Application rerender(UUID userId, UUID applicationId, List<UUID> selectedBulletIds, ProgressLog progress) {
        Application a = get(userId, applicationId);
        Map<UUID, Bullet> bulletById = bulletRepo.findByIdsAndProjectUserId(
                selectedBulletIds.toArray(new UUID[0]), userId).stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        List<Bullet> selected = selectedBulletIds.stream()
                .map(bulletById::get).filter(Objects::nonNull).toList();
        // Only fetch projects referenced by the selected bullets.
        Set<UUID> projectIds = selected.stream().map(Bullet::getProjectId).collect(Collectors.toSet());
        Map<UUID, Project> projectById = projectRepo.findByIdIn(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        PipelineTimer tRerender = PipelineTimer.start("rerender pipeline");
        progress.emit("Re-rendering LaTeX with " + selected.size() + " selected bullets...");
        List<String> selectedCourses = a.getSelectedCourses() == null ? List.of() : Arrays.asList(a.getSelectedCourses());
        Map<String, List<String>> selectedSkills = parseSelectedSkills(a.getSelectedSkills());
        String tex = renderer.render(userId, selected, projectById, selectedCourses, selectedSkills);
        progress.emit("Compiling PDF via tectonic...");
        PdfCompiler.Result r = compiler.compile(tex);

        a.setSelectedBulletIds(selected.stream().map(Bullet::getId).toArray(UUID[]::new));
        a.setTexBlob(tex.getBytes(StandardCharsets.UTF_8));

        // The deterministic half of the scorecard is recomputed here for free. The LLM half is
        // not — rerender makes no LLM calls (POST /{id}/rerender blocks a request thread) — so
        // the old score is kept and flagged stale rather than blanked, which would pull the
        // feedback away at exactly the moment the user is editing against it.
        a.setPageCount(r.success() ? r.pageCount() : null);
        if (r.success() && r.overfullHbox()) {
            progress.emit("Warning: a line in the rendered PDF is too wide for its column (overfull hbox).");
        }
        a.setRecruiterStale(true);
        Set<String> priorKeywords = new LinkedHashSet<>(Arrays.asList(a.getAtsMatched()));
        priorKeywords.addAll(Arrays.asList(a.getAtsMissing()));
        Set<String> priorLlmMatched = Arrays.stream(a.getAtsMatched())
                .map(String::toLowerCase).collect(Collectors.toSet());
        AtsReport ats = atsReport(List.copyOf(priorKeywords), priorLlmMatched, selected,
                selectedSkills, selectedCourses);
        a.setAtsMatched(ats.matched().toArray(new String[0]));
        a.setAtsMissing(ats.missing().toArray(new String[0]));

        if (r.success()) {
            a.setPdfBlob(r.pdf());
            a.setTectonicLog(r.log());
            progress.emit("Done - PDF compiled (" + r.pdf().length / 1024 + " KB).");
        } else {
            a.setTectonicLog("FAILED: " + r.error() + "\n\n" + r.log());
            progress.emit("PDF compile failed: " + r.error());
            if (r.log() != null && !r.log().isBlank()) {
                String[] tecLines = r.log().split("\n");
                int start = Math.max(0, tecLines.length - 6);
                for (int i = start; i < tecLines.length; i++) {
                    String l = tecLines[i].strip();
                    if (!l.isBlank()) progress.emit("tectonic: " + l);
                }
            }
        }
        a.setPipelineDurationMs(tRerender.stop());
        Application saved = repo.save(a);
        log.info("APP_RERENDER app={} ms={}", shortId(saved.getId()), a.getPipelineDurationMs());
        return saved;
    }

    /**
     * Replace this application's locked-bullet set. Silently drops any id that doesn't resolve
     * to a bullet the user actually owns (deleted since, or never theirs) — the caller can't
     * tell the difference from a race, and either way it's not a lockable bullet.
     */
    public Application setLocked(UUID userId, UUID applicationId, List<UUID> lockedBulletIds) {
        Application a = get(userId, applicationId);
        List<UUID> ids = lockedBulletIds == null ? List.of() : lockedBulletIds;
        Set<UUID> owned = bulletRepo.findByIdsAndProjectUserId(ids.toArray(new UUID[0]), userId).stream()
                .map(Bullet::getId).collect(Collectors.toSet());
        a.setLockedBulletIds(ids.stream().filter(owned::contains).distinct().toArray(UUID[]::new));
        return repo.save(a);
    }

    /**
     * Re-pick the selection from the current bank without calling the LLM again: reuses the
     * ranking already stored from creation (bank drift only — the JD hasn't changed, so the
     * rank order is still valid for the bullets that were ranked), and the same ATS keyword set
     * already stored as {@code atsMatched}/{@code atsMissing}. Locked bullets are pinned via
     * {@link BulletSelector#select(List, Map, Map, List, Set, List)} — the same four-pass
     * selection algorithm the initial generation uses, so per-project caps, entry caps, the
     * kind floor and dedup all still hold; refit only adds the guarantee that locked bullets
     * survive every pass.
     */
    public Application refitSelection(UUID userId, UUID applicationId, ProgressLog progress) {
        return refitSelection(userId, applicationId, null, progress);
    }

    /**
     * Same, optionally scoped to a single entry.
     *
     * <p>When {@code onlyProjectId} is non-null, only that project's bullets may change; every
     * other entry on the page comes back byte-identical. This is <b>not</b> done by running the
     * selector over one project — the entry cap, kind floor, line budget and dedup check are all
     * whole-page rules, and a per-project run would violate every one of them. Instead the same
     * global four-pass selection runs with every <i>other</i> selected bullet handed to it as
     * {@code locked}, which the selector already guarantees survives all four passes, and only
     * the target entry's currently-shown bullets are excluded so passes 1-3 look elsewhere.
     *
     * <p>The result is then filtered back down to {pinned} ∪ {target entry}. That filter is the
     * actual guarantee, and it is not redundant: pass 4's floor tops up <i>every</i> surviving
     * project to {@link BulletSelector#MAX_PER_PROJECT}, so without it a scoped refit could
     * quietly add bullets to entries the caller never asked about.
     *
     * <p>Two consequences the caller should surface rather than hide. The target entry only gets
     * whatever line budget the pinned entries leave, so a full page can hand back a
     * <i>smaller</i> entry than before. And pass 4 does not honour {@code excluded}, so an entry
     * whose bank is thin can legitimately return the same bullets it started with.
     *
     * @param onlyProjectId project to re-pick, or null to re-pick the whole page
     */
    public Application refitSelection(UUID userId, UUID applicationId, UUID onlyProjectId, ProgressLog progress) {
        Application a = get(userId, applicationId);
        List<Bullet> allBullets = bulletRepo.findSelectableByProjectUserId(userId);
        Map<UUID, Bullet> bulletById = allBullets.stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        Map<UUID, Project> projectById = projectRepo.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        List<LlmClient.RankedBullet> rankedSorted;
        try {
            rankedSorted = mapper.readValue(a.getBulletRanking(), new com.fasterxml.jackson.core.type.TypeReference<List<LlmClient.RankedBullet>>() {})
                    .stream().sorted(Comparator.comparingInt(LlmClient.RankedBullet::rank)).toList();
        } catch (JsonProcessingException e) {
            rankedSorted = List.of();
        }

        // Reject an unknown or someone else's project before anything expensive runs; silently
        // falling back to a whole-page refit would rewrite entries the caller never named.
        if (onlyProjectId != null && !projectById.containsKey(onlyProjectId)) {
            throw new IllegalArgumentException("No such project on this account: " + onlyProjectId);
        }

        List<Bullet> userLocked = Arrays.stream(a.getLockedBulletIds())
                .map(bulletById::get).filter(Objects::nonNull).toList();
        Set<UUID> lockedIds = userLocked.stream().map(Bullet::getId).collect(Collectors.toSet());

        List<Bullet> onPage = Arrays.stream(a.getSelectedBulletIds())
                .map(bulletById::get).filter(Objects::nonNull).toList();

        final List<Bullet> locked;
        final Set<UUID> excluded;
        if (onlyProjectId == null) {
            locked = userLocked;
            // Steer passes 1-3 away from whatever was already on the page and isn't locked, so a
            // refit with nothing newly pinned doesn't just reproduce the same deterministic pick.
            excluded = onPage.stream().map(Bullet::getId)
                    .filter(id -> !lockedIds.contains(id)).collect(Collectors.toSet());
        } else {
            // Pin the rest of the page. LinkedHashMap so a bullet that is both user-locked and
            // outside the target entry is pinned once, in a stable order.
            LinkedHashMap<UUID, Bullet> pinned = new LinkedHashMap<>();
            userLocked.forEach(b -> pinned.put(b.getId(), b));
            onPage.stream().filter(b -> !onlyProjectId.equals(b.getProjectId()))
                    .forEach(b -> pinned.put(b.getId(), b));
            locked = List.copyOf(pinned.values());
            excluded = onPage.stream()
                    .filter(b -> onlyProjectId.equals(b.getProjectId()))
                    .map(Bullet::getId)
                    .filter(id -> !lockedIds.contains(id))
                    .collect(Collectors.toSet());
        }

        // Same JD keyword set the original ranking pass used, recovered from what was stored
        // rather than re-derived — matched + missing together are the full keyword list.
        Set<String> keywordsLower = new LinkedHashSet<>();
        Arrays.stream(a.getAtsMatched()).map(String::toLowerCase).forEach(keywordsLower::add);
        Arrays.stream(a.getAtsMissing()).map(String::toLowerCase).forEach(keywordsLower::add);

        String scope = onlyProjectId == null
                ? "whole page"
                : "entry \"" + projectById.get(onlyProjectId).getName() + "\" only, "
                  + locked.size() + " bullets pinned elsewhere";
        progress.emit("Refitting " + scope + " from " + allBullets.size() + " bank bullets ("
                + userLocked.size() + " locked)...");
        List<Bullet> selected = BulletSelector.select(rankedSorted, bulletById, projectById, allBullets, keywordsLower, locked, excluded);

        if (onlyProjectId != null) {
            // The guarantee: pass 4's floor tops up every surviving project, so drop anything it
            // added outside the entry the caller named. Pinned bullets are in `selected` already
            // (the selector seeds them before pass 1), so this only ever removes.
            Set<UUID> pinnedIds = locked.stream().map(Bullet::getId).collect(Collectors.toSet());
            selected = selected.stream()
                    .filter(b -> pinnedIds.contains(b.getId()) || onlyProjectId.equals(b.getProjectId()))
                    .toList();
            long inEntry = selected.stream().filter(b -> onlyProjectId.equals(b.getProjectId())).count();
            progress.emit("Entry re-picked: " + inEntry + " bullet(s) now included.");
        }

        List<String> selectedCourses = a.getSelectedCourses() == null ? List.of() : Arrays.asList(a.getSelectedCourses());
        Map<String, List<String>> selectedSkills = parseSelectedSkills(a.getSelectedSkills());
        String tex = renderer.render(userId, selected, projectById, selectedCourses, selectedSkills);
        progress.emit("Compiling PDF via tectonic...");
        PdfCompiler.Result r = compiler.compile(tex);

        a.setSelectedBulletIds(selected.stream().map(Bullet::getId).toArray(UUID[]::new));
        a.setTexBlob(tex.getBytes(StandardCharsets.UTF_8));
        a.setPageCount(r.success() ? r.pageCount() : null);
        if (r.success() && r.overfullHbox()) {
            progress.emit("Warning: a line in the rendered PDF is too wide for its column (overfull hbox).");
        }
        a.setRecruiterStale(true);

        Set<String> priorKeywords = new LinkedHashSet<>(Arrays.asList(a.getAtsMatched()));
        priorKeywords.addAll(Arrays.asList(a.getAtsMissing()));
        Set<String> priorLlmMatched = Arrays.stream(a.getAtsMatched())
                .map(String::toLowerCase).collect(Collectors.toSet());
        AtsReport ats = atsReport(List.copyOf(priorKeywords), priorLlmMatched, selected, selectedSkills, selectedCourses);
        a.setAtsMatched(ats.matched().toArray(new String[0]));
        a.setAtsMissing(ats.missing().toArray(new String[0]));

        if (r.success()) {
            a.setPdfBlob(r.pdf());
            a.setTectonicLog(r.log());
            progress.emit("Done - PDF compiled (" + r.pdf().length / 1024 + " KB).");
        } else {
            a.setTectonicLog("FAILED: " + r.error() + "\n\n" + r.log());
            progress.emit("PDF compile failed: " + r.error());
        }
        return repo.save(a);
    }

    private record AtsReport(List<String> matched, List<String> missing) {}

    /**
     * ATS report narrowed to what actually lands on the page: a keyword counts as matched only
     * if the LLM claimed it AND the rendered text literally contains it. The corpus is
     * everything the template emits — bullets, the skills block and coursework all render (see
     * ApplicationRenderer), so a keyword living only in skills_devops is on the PDF and must
     * not be reported missing. Shared by create and rerender so a hand-edited selection is
     * scored by the identical rule.
     */
    private static AtsReport atsReport(List<String> keywords, Set<String> llmMatchedLower,
                                       List<Bullet> selected, Map<String, List<String>> skills,
                                       List<String> courses) {
        List<String> renderedParts = new ArrayList<>(selected.stream().map(Bullet::getText).toList());
        if (skills != null) skills.values().forEach(renderedParts::addAll);
        renderedParts.addAll(courses);
        String renderedText = String.join("\n", renderedParts);

        // Iterate the caller's keywords, not a lowercased copy — these strings render as chips
        // in the UI and should keep the JD's own casing ("PostgreSQL", not "postgresql").
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String k : keywords) {
            if (llmMatchedLower.contains(k.toLowerCase()) && KeywordScorer.mentions(renderedText, k)) {
                matched.add(k);
            } else {
                missing.add(k);
            }
        }
        return new AtsReport(matched, missing);
    }

    private List<LlmClient.SkillCategory> buildSkillCategories(com.resumepipeline.profile.Profile p) {
        List<LlmClient.SkillCategory> cats = new ArrayList<>();
        addSkillCategory(cats, "languages", p.getSkillsLanguages());
        addSkillCategory(cats, "frameworks", p.getSkillsFrameworks());
        addSkillCategory(cats, "databases", p.getSkillsDatabases());
        addSkillCategory(cats, "devops", p.getSkillsDevops());
        return cats;
    }

    /**
     * Grows each skill category past {@link BulletSelector#fillSkills}'s floor with more raw
     * profile skills, one at a time, stopping at the largest prefix that still renders within
     * {@link BulletSelector#MAX_SKILLS_FILL} of {@code \linewidth} at {@code \skillrow}'s
     * {@code \small} size (see {@link SkillRowMeasurer}). This is what stretches a short skills
     * row toward the right margin instead of leaving it wherever the fixed 6-item floor happened
     * to land, while staying clear of {@code \skillrow}'s own shrink-to-fit fallback.
     *
     * <p>One compile covers every category and every candidate count at once. A compile failure
     * (or any category with no headroom to grow) falls back to {@code floorFilled} unchanged —
     * a resume with a shorter-than-ideal skills row is fine; losing the render is not.
     */
    private Map<String, List<String>> stretchSkillsToWidth(Map<String, List<String>> floorFilled,
                                                            Map<String, List<String>> rawSkills) {
        Map<String, List<String>> candidates = new LinkedHashMap<>();
        for (String key : BulletSelector.SKILL_KEYS) {
            candidates.put(key, BulletSelector.paddingCandidates(floorFilled.get(key), rawSkills.get(key)));
        }

        Map<String, SkillRowMeasurer.Row> rows = new LinkedHashMap<>();
        for (String key : BulletSelector.SKILL_KEYS) {
            List<String> seq = candidates.get(key);
            int floorCount = floorFilled.getOrDefault(key, List.of()).size();
            for (int count = Math.max(floorCount, 1); count <= seq.size(); count++) {
                rows.put(key + "_" + count,
                        new SkillRowMeasurer.Row(SKILL_LABELS.get(key), String.join(", ", seq.subList(0, count))));
            }
        }
        if (rows.isEmpty()) return floorFilled;

        Map<String, SkillRowMeasurer.Measured> measured = skillRowMeasurer.measure(rows);
        if (measured.isEmpty()) return floorFilled;

        Map<String, List<String>> stretched = new LinkedHashMap<>();
        for (String key : BulletSelector.SKILL_KEYS) {
            List<String> seq = candidates.get(key);
            int floorCount = floorFilled.getOrDefault(key, List.of()).size();
            int best = floorCount;
            for (int count = Math.max(floorCount, 1); count <= seq.size(); count++) {
                SkillRowMeasurer.Measured m = measured.get(key + "_" + count);
                if (m == null) break;
                // Widths only grow with more items, so the first count that no longer fits
                // ends the search — everything beyond it would fit even less.
                if (!m.fits(BulletSelector.MAX_SKILLS_FILL)) break;
                best = count;
            }
            stretched.put(key, seq.subList(0, best));
        }
        return stretched;
    }

    /**
     * Drop bullets that restate a claim an earlier bullet in {@code byScoreDesc} already makes,
     * keeping the first — which, given the caller sorts by keyword score, is the framing that
     * matches THIS job description best.
     *
     * <p>The bank deliberately holds several framings of the same work, one per category lens
     * (see {@code BulletTextRules.CROSS_LENS_THRESHOLD}). That is what makes a project reusable
     * across different jobs, but all of those framings score similarly on raw keyword overlap,
     * so without this the per-project top-4 could be four wordings of one achievement — spending
     * the ranking LLM's candidate slots on a choice it has already been made for it, and starving
     * the other work on the project.
     *
     * <p>This is where the variant set collapses, and it is the right place: the JD is known
     * here and it is what decides which framing survives. {@code BulletSelector} still runs its
     * own near-duplicate check, so this is an efficiency pass, not the correctness guard.
     */
    private static List<Bullet> collapseVariants(List<Bullet> byScoreDesc) {
        List<Bullet> kept = new ArrayList<>();
        List<String> keptTexts = new ArrayList<>();
        for (Bullet b : byScoreDesc) {
            if (BulletTextRules.isNearDuplicate(b.getText(), keptTexts)) continue;
            kept.add(b);
            keptTexts.add(b.getText());
        }
        return kept;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** First 8 chars of the id for log events — short enough to grep, long enough not to collide. */
    private static String shortId(UUID id) { return id == null ? "?" : id.toString().substring(0, 8); }

    /** The id is validated against the rendered set before it gets here; parse defensively anyway. */
    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static void addSkillCategory(List<LlmClient.SkillCategory> cats, String name, String csv) {
        if (csv == null || csv.isBlank()) return;
        List<String> items = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (!items.isEmpty()) cats.add(new LlmClient.SkillCategory(name, items));
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseSelectedSkills(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return Map.of();
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse selectedSkills JSON: {}", json);
            return Map.of();
        }
    }

    // Short text preview for log messages — keeps lines readable.
    // private static String abbreviate(String s) {
    //     if (s == null) return "";
    //     return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    // }
}
