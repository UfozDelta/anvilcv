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
    private final ObjectMapper mapper = new ObjectMapper();

    public ApplicationService(ApplicationRepository repo, OutcomeHistoryRepository outcomeHistoryRepo,
                              BulletRepository bulletRepo,
                              ProjectRepository projectRepo, JdFetcher jdFetcher, LlmClient llm,
                              ApplicationRenderer renderer, PdfCompiler compiler,
                              ProfileService profileService, LlmUsageService llmUsageService) {
        this.repo = repo;
        this.outcomeHistoryRepo = outcomeHistoryRepo;
        this.bulletRepo = bulletRepo;
        this.projectRepo = projectRepo;
        this.jdFetcher = jdFetcher;
        this.llm = llm;
        this.renderer = renderer;
        this.compiler = compiler;
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
        CompletableFuture<LlmClient.FitResult> fitFuture = CompletableFuture.supplyAsync(() ->
                llm.scoreFit(new LlmClient.FitRequest(clean.cleanJd(), clean.company(), clean.role(),
                        clean.keywords(), roleEmphasis, skillCategories, projectSummaries), progress, tokens),
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
        Map<String, List<String>> filledSkills = BulletSelector.fillSkills(rank.selectedSkills(), rawSkills);
        progress.emit("Skills filled: languages=" + filledSkills.get("languages").size()
                + " fw=" + filledSkills.get("frameworks").size()
                + " db=" + filledSkills.get("databases").size()
                + " devops=" + filledSkills.get("devops").size());

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
        List<String> renderedParts = new ArrayList<>(selected.stream().map(Bullet::getText).toList());
        filledSkills.values().forEach(renderedParts::addAll);
        renderedParts.addAll(selectedCourses);
        String renderedText = String.join("\n", renderedParts);

        Set<String> llmMatched = rank.atsMatched().stream()
                .map(String::toLowerCase).collect(Collectors.toSet());
        // Iterate clean.keywords(), not kwLower — these strings render as chips in the UI and
        // should keep the JD's own casing ("PostgreSQL", not "postgresql").
        List<String> atsMatched = new ArrayList<>();
        List<String> atsMissing = new ArrayList<>();
        for (String k : clean.keywords()) {
            if (llmMatched.contains(k.toLowerCase()) && KeywordScorer.mentions(renderedText, k)) {
                atsMatched.add(k);
            } else {
                atsMissing.add(k);
            }
        }
        progress.emit("ATS on rendered page: " + atsMatched.size() + "/" + clean.keywords().size()
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
                .supplyAsync(() -> compiler.compile(tex), PARALLEL_EXECUTOR);
        CompletableFuture<String> coverLetterFuture = includeCoverLetter
                ? CompletableFuture.supplyAsync(() -> llm.coverLetter(
                        new LlmClient.CoverLetterRequest(clean.cleanJd(), clean.company(), clean.role(), roleEmphasis, selectedTexts),
                        progress, tokens), PARALLEL_EXECUTOR)
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
        a.setAtsMatched(atsMatched.toArray(new String[0]));
        a.setAtsMissing(atsMissing.toArray(new String[0]));
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
        return saved;

        } catch (RuntimeException e) {
            tTotal.stop("FAILED");
            throw e;
        }
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
        return repo.save(a);
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
