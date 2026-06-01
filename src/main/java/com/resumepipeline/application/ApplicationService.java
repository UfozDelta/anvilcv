package com.resumepipeline.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.jd.JdFetcher;
import com.resumepipeline.llm.LlmClient;
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
    private static final int MAX_TOTAL = 8;
    private static final int MAX_PER_PROJECT = 3;
    private static final ExecutorService PARALLEL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ApplicationRepository repo;
    private final BulletRepository bulletRepo;
    private final ProjectRepository projectRepo;
    private final JdFetcher jdFetcher;
    private final LlmClient llm;
    private final ApplicationRenderer renderer;
    private final PdfCompiler compiler;
    private final ProfileService profileService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApplicationService(ApplicationRepository repo, BulletRepository bulletRepo,
                              ProjectRepository projectRepo, JdFetcher jdFetcher, LlmClient llm,
                              ApplicationRenderer renderer, PdfCompiler compiler,
                              ProfileService profileService) {
        this.repo = repo;
        this.bulletRepo = bulletRepo;
        this.projectRepo = projectRepo;
        this.jdFetcher = jdFetcher;
        this.llm = llm;
        this.renderer = renderer;
        this.compiler = compiler;
        this.profileService = profileService;
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

        // Stage: clean JD — strips boilerplate and extracts role/company/keywords
        PipelineTimer tClean = PipelineTimer.start("cleanJd");
        LlmClient.JdCleanResult clean = llm.cleanJd(jdText, progress);
        tClean.stop();

        // Stage: rank bullets — sends top candidates to LLM for scoring against the JD
        List<Bullet> allBullets = bulletRepo.findByProjectUserId(userId);
        if (allBullets.isEmpty()) {
            throw new IllegalStateException("No bullets in the bank — generate or add some first.");
        }

        // Pre-filter: score each bullet by how many of its tags overlap with JD keywords,
        // then take the top 25. Keeps the match prompt focused and output size small —
        // LLM ranking accuracy degrades significantly past ~25 items.
        Set<String> kwLower = clean.keywords().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        List<Bullet> candidates = allBullets.stream()
                .sorted(Comparator.comparingLong((Bullet b) ->
                        Arrays.stream(b.getTags() == null ? new String[0] : b.getTags())
                              .filter(t -> kwLower.contains(t.toLowerCase()))
                              .count()
                ).reversed())
                .limit(25)
                .toList();

        progress.emit("Pre-filter: " + allBullets.size() + " total bullets → top " + candidates.size()
                + " by tag overlap with JD keywords (" + clean.keywords().size() + " keywords)");

        // Only fetch projects that actually appear in the candidate set.
        Set<UUID> projectIds = candidates.stream().map(Bullet::getProjectId).collect(Collectors.toSet());
        Map<UUID, Project> projectById = projectRepo.findByIdIn(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        List<LlmClient.BulletForMatch> bulletsForMatch = candidates.stream()
                .map(b -> new LlmClient.BulletForMatch(
                        b.getId().toString(),
                        b.getText(),
                        Arrays.asList(b.getTags() == null ? new String[0] : b.getTags()),
                        projectById.containsKey(b.getProjectId()) ? projectById.get(b.getProjectId()).getName() : ""))
                .toList();

        // Fire ranking (always) and cover letter (optional) in parallel.
        progress.emit("Ranking " + candidates.size() + " candidates against JD...");

        // Collect all courses from profile education entries (split comma-separated strings).
        List<String> allCourses = profileService.readEducation(profileService.get(userId)).stream()
                .filter(e -> e.coursework() != null && !e.coursework().isBlank())
                .flatMap(e -> Arrays.stream(e.coursework().split(",")))
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .distinct()
                .toList();

        LlmClient.RankRequest rankReq = new LlmClient.RankRequest(
                clean.cleanJd(), clean.company(), clean.role(),
                clean.keywords(), roleEmphasis, bulletsForMatch, allCourses);

        PipelineTimer tRank = PipelineTimer.start("rank (" + candidates.size() + " bullets)");
        LlmClient.RankResult rank = llm.rankBullets(rankReq, progress);
        tRank.stop();

        // Server-side selection: top 8 overall, cap 3 per project.
        Map<UUID, Bullet> bulletById = candidates.stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        List<LlmClient.RankedBullet> rankedSorted = rank.rankedBullets().stream()
                .sorted(Comparator.comparingInt(LlmClient.RankedBullet::rank))
                .toList();

        progress.emit("Selecting top " + MAX_TOTAL + " bullets (max " + MAX_PER_PROJECT + " per project)...");
        LinkedHashMap<UUID, Integer> perProject = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> perProjectName = new LinkedHashMap<>();
        List<Bullet> selected = new ArrayList<>();
        for (LlmClient.RankedBullet rb : rankedSorted) {
            if (selected.size() >= MAX_TOTAL) break;
            UUID bid;
            try { bid = UUID.fromString(rb.bulletId()); } catch (Exception e) { continue; }
            Bullet b = bulletById.get(bid);
            if (b == null) continue;
            int count = perProject.getOrDefault(b.getProjectId(), 0);
            String proj = projectById.containsKey(b.getProjectId())
                    ? projectById.get(b.getProjectId()).getName() : "unknown";
            if (count >= MAX_PER_PROJECT) {
                progress.emit("Skipped: cap reached for " + proj + " (" + MAX_PER_PROJECT + "/" + MAX_PER_PROJECT + ")");
                continue;
            }
            perProject.put(b.getProjectId(), count + 1);
            perProjectName.merge(proj, 1, Integer::sum);
            selected.add(b);
        }
        progress.emit("Selection complete - " + selected.size() + " bullets:");
        perProjectName.forEach((proj, cnt) ->
                progress.emit("  " + proj + " - " + cnt + " bullet" + (cnt > 1 ? "s" : "")));

        List<String> selectedCourses = rank.selectedCourses() == null ? List.of() : rank.selectedCourses();

        // Stage: render LaTeX
        progress.emit("Rendering LaTeX...");
        PipelineTimer tRender = PipelineTimer.start("LaTeX render");
        String tex = renderer.render(userId, selected, projectById, selectedCourses);
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
                        progress), PARALLEL_EXECUTOR)
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

        Application a = new Application();
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
        return repo.save(a);
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

        progress.emit("Re-rendering LaTeX with " + selected.size() + " selected bullets...");
        List<String> selectedCourses = a.getSelectedCourses() == null ? List.of() : Arrays.asList(a.getSelectedCourses());
        String tex = renderer.render(userId, selected, projectById, selectedCourses);
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
        return repo.save(a);
    }

    // Short text preview for log messages — keeps lines readable.
    // private static String abbreviate(String s) {
    //     if (s == null) return "";
    //     return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    // }
}
