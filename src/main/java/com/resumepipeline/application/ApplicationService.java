package com.resumepipeline.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.jd.JdFetcher;
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
    private final BulletRepository bulletRepo;
    private final ProjectRepository projectRepo;
    private final JdFetcher jdFetcher;
    private final LlmClient llm;
    private final ApplicationRenderer renderer;
    private final PdfCompiler compiler;
    private final ProfileService profileService;
    private final LlmUsageService llmUsageService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApplicationService(ApplicationRepository repo, BulletRepository bulletRepo,
                              ProjectRepository projectRepo, JdFetcher jdFetcher, LlmClient llm,
                              ApplicationRenderer renderer, PdfCompiler compiler,
                              ProfileService profileService, LlmUsageService llmUsageService) {
        this.repo = repo;
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
        a.setOutcome(outcome);
        return repo.save(a);
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
        List<Bullet> allBullets = bulletRepo.findByProjectUserId(userId);
        if (allBullets.isEmpty()) {
            throw new IllegalStateException("No bullets in the bank — generate or add some first.");
        }

        // Fetch all user projects up front — needed for kind-aware pre-filter and selection.
        Map<UUID, Project> projectById = projectRepo.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        // Pre-filter: round-robin across projects (top 4 bullets per project by tag overlap),
        // then global top-25. Prevents bullet-heavy projects from crowding out all other entries.
        Set<String> kwLower = clean.keywords().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        java.util.function.ToLongFunction<Bullet> tagScore = b ->
                Arrays.stream(b.getTags() == null ? new String[0] : b.getTags())
                      .filter(t -> kwLower.contains(t.toLowerCase()))
                      .count();
        List<Bullet> candidates = allBullets.stream()
                .collect(Collectors.groupingBy(Bullet::getProjectId))
                .values().stream()
                .flatMap(group -> group.stream()
                        .sorted(Comparator.comparingLong(tagScore).reversed())
                        .limit(4))
                .sorted(Comparator.comparingLong(tagScore).reversed())
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

        LlmClient.RankRequest rankReq = new LlmClient.RankRequest(
                clean.cleanJd(), clean.company(), clean.role(),
                clean.keywords(), roleEmphasis, bulletsForMatch, allCourses, skillCategories);

        PipelineTimer tRank = PipelineTimer.start("rank (" + candidates.size() + " bullets)");
        LlmClient.RankResult rank = llm.rankBullets(rankReq, progress, tokens);
        tRank.stop();

        // Server-side selection: greedy top-N capped per project, then kind-floor + min-fill.
        // Logic lives in BulletSelector so it can be unit-tested without the LLM/DB stubs.
        Map<UUID, Bullet> bulletById = candidates.stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        List<LlmClient.RankedBullet> rankedSorted = rank.rankedBullets().stream()
                .sorted(Comparator.comparingInt(LlmClient.RankedBullet::rank))
                .toList();

        progress.emit("Selecting top " + BulletSelector.MAX_TOTAL + " bullets (max "
                + BulletSelector.MAX_PER_PROJECT + " per project)...");
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

        if (selected.size() > 12) {
            progress.emit("Warning: " + selected.size() + " bullets selected — PDF may exceed one page.");
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
        a.setAtsMatched(rank.atsMatched().toArray(new String[0]));
        a.setAtsMissing(rank.atsMissing().toArray(new String[0]));
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
