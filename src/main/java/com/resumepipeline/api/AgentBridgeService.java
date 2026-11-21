package com.resumepipeline.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.api.dto.AgentDtos.AgentBullet;
import com.resumepipeline.api.dto.AgentDtos.BulletCheck;
import com.resumepipeline.api.dto.AgentDtos.BulletChecksResponse;
import com.resumepipeline.api.dto.AgentDtos.CheckBulletsRequest;
import com.resumepipeline.api.dto.AgentDtos.CheckCoverageRequest;
import com.resumepipeline.api.dto.AgentDtos.CoverageItem;
import com.resumepipeline.api.dto.AgentDtos.CoverageResponse;
import com.resumepipeline.api.dto.AgentDtos.CreateAgentApplicationRequest;
import com.resumepipeline.api.dto.AgentDtos.CreateBulletsRequest;
import com.resumepipeline.api.dto.AgentDtos.CreateStoryRequest;
import com.resumepipeline.api.dto.AgentDtos.RankEntry;
import com.resumepipeline.api.dto.AgentDtos.UpdateAgentApplicationRequest;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.application.JdCoverage;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.bullet.Story;
import com.resumepipeline.bullet.StoryRepository;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.quota.QuotaService;
import com.resumepipeline.render.PdfCompiler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Write/check path for an external AI agent. The agent does the thinking; this service
 * persists through AnvilCV's own entities, renderer, compiler and quota checks, so the
 * results are ordinary AnvilCV rows and show up in the UI.
 *
 * <p>Every entry point verifies project/bullet ownership against {@code userId}. Deliberately
 * narrow: it does not run any LLM call — generation and judging belong to the caller.
 */
@Service
public class AgentBridgeService {

    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> VALID_ANGLES = Set.of("outcome", "decision", "scale", "failure");

    private final StoryRepository stories;
    private final BulletRepository bullets;
    private final ApplicationRepository applications;
    private final ProjectService projects;
    private final GenerationConfigService configService;
    private final ApplicationRenderer renderer;
    private final PdfCompiler compiler;
    private final QuotaService quota;
    private final ObjectMapper mapper;

    public AgentBridgeService(StoryRepository stories, BulletRepository bullets,
                              ApplicationRepository applications, ProjectService projects,
                              GenerationConfigService configService, ApplicationRenderer renderer,
                              PdfCompiler compiler, QuotaService quota, ObjectMapper mapper) {
        this.stories = stories;
        this.bullets = bullets;
        this.applications = applications;
        this.projects = projects;
        this.configService = configService;
        this.renderer = renderer;
        this.compiler = compiler;
        this.quota = quota;
        this.mapper = mapper;
    }

    /** Save an agent-authored story. Lenses are normalized to the four current software slugs. */
    public UUID createStory(UUID userId, CreateStoryRequest req) {
        projects.get(userId, req.projectId());
        quota.checkGeneration(userId, req.projectId());
        long live = bullets.findByProjectIdOrderByCreatedAtAsc(req.projectId()).stream()
                .map(Bullet::getStoryId).filter(Objects::nonNull).distinct().count();
        if (live >= BulletService.STORY_CAP) {
            throw new IllegalStateException("Story bank full: " + live + "/" + BulletService.STORY_CAP);
        }
        String[] evidence = req.evidence() == null
                ? new String[0] : req.evidence().stream().filter(s -> s != null && !s.isBlank())
                        .toArray(String[]::new);
        String[] lenses = req.lenses() == null
                ? new String[]{"general"} : req.lenses().stream().map(AgentBridgeService::lens)
                        .distinct().limit(4).toArray(String[]::new);
        UUID id = UUID.randomUUID();
        stories.save(new Story(id, req.projectId(), req.title(), evidence, lenses));
        return id;
    }

    /** Save agent-authored wordings onto an existing story, with angle and judge note intact. */
    public List<UUID> createBullets(UUID userId, UUID storyId, CreateBulletsRequest req) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown story: " + storyId));
        projects.get(userId, story.getProjectId());
        GenerationConfig cfg = configService.get(userId);

        List<Bullet> toSave = new ArrayList<>();
        for (AgentBullet item : req.bullets()) {
            String text = BulletTextRules.ensureTerminalPeriod(item.text());
            text = BulletTextRules.capBoldSpans(text, BulletTextRules.maxBoldSpans(cfg, text));
            String status = item.status() == null || item.status().isBlank()
                    ? "PENDING" : item.status().trim().toUpperCase();
            if (!VALID_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + item.status());
            }
            String angle = item.angle() == null ? null : item.angle().trim().toLowerCase();
            if (!VALID_ANGLES.contains(angle)) angle = null;
            String[] tags = item.tags() == null
                    ? new String[0] : item.tags().stream().filter(Objects::nonNull).toArray(String[]::new);

            Bullet b = new Bullet(story.getProjectId(), text, tags, lens(item.lens()));
            b.setStoryId(storyId);
            b.setAngle(angle);
            b.setJudgeNote(item.judgeNote());
            b.setStatus(status);
            toSave.add(b);
        }
        List<UUID> saved = bullets.saveAll(toSave).stream().map(Bullet::getId).toList();
        quota.recordGeneration(userId, story.getProjectId());
        return saved;
    }

    /** Run AnvilCV's deterministic bullet checks on raw text before the agent saves it. */
    public BulletChecksResponse checkBullets(UUID userId, CheckBulletsRequest req) {
        GenerationConfig cfg = configService.get(userId);
        List<String> existing = new ArrayList<>(req.existingTexts() == null ? List.of() : req.existingTexts());
        if (req.projectId() != null) {
            projects.get(userId, req.projectId());
            bullets.findByProjectIdOrderByCreatedAtAsc(req.projectId()).stream()
                    .map(Bullet::getText).forEach(existing::add);
        }
        String source = req.sourceContext() == null ? "" : req.sourceContext();
        List<BulletCheck> checks = new ArrayList<>();
        for (String text : req.texts()) {
            int chars = BulletTextRules.charCount(text);
            checks.add(new BulletCheck(
                    text,
                    BulletTextRules.decide(chars, cfg).name(),
                    chars,
                    BulletTextRules.estimatedLines(text),
                    BulletTextRules.vanityCount(text),
                    BulletTextRules.fabricatedNumbers(text, source),
                    BulletTextRules.hasForbiddenOpener(text),
                    BulletTextRules.isPadded(text),
                    BulletTextRules.isNearDuplicate(text, existing),
                    BulletTextRules.capBoldSpans(text, BulletTextRules.maxBoldSpans(cfg, text)),
                    BulletTextRules.autoSelectable("PENDING", text)));
        }
        return new BulletChecksResponse(checks,
                BulletTextRules.singleLowChars(cfg), BulletTextRules.singleHighChars(cfg),
                BulletTextRules.deadZoneLowChars(cfg), BulletTextRules.deadZoneHighChars(cfg),
                BulletTextRules.doubleLowChars(cfg), BulletTextRules.doubleHighChars(cfg));
    }

    /** Score a JD against the text that will actually render (hard gates, gaps, keywords). */
    public CoverageResponse checkCoverage(CheckCoverageRequest req) {
        JdCoverage.Matrix matrix = JdCoverage.of(
                req.jdText(),
                req.renderedText() == null ? "" : req.renderedText(),
                req.keywords() == null ? List.of() : req.keywords());
        List<CoverageItem> items = matrix.items().stream()
                .map(i -> new CoverageItem(i.text(), i.kind().name(), i.status().name())).toList();
        return new CoverageResponse(items, matrix.coverageGaps(), matrix.hardGateMisses());
    }

    /**
     * Persist a finished application from the agent's own selection, cover letter and JD,
     * running AnvilCV's renderer, tectonic compile, ATS/coverage scoring and quota accounting.
     */
    public Application createApplication(UUID userId, CreateAgentApplicationRequest req) {
        quota.checkTailor(userId);

        UUID[] ids = req.selectedBulletIds().toArray(UUID[]::new);
        Map<UUID, Bullet> byId = bullets.findByIdsAndProjectUserId(ids, userId).stream()
                .collect(Collectors.toMap(Bullet::getId, b -> b));
        List<Bullet> ordered = new ArrayList<>();
        for (UUID id : ids) {
            Bullet b = byId.get(id);
            if (b == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown bullet: " + id);
            }
            ordered.add(b);
        }
        Map<UUID, Project> projectById = new LinkedHashMap<>();
        for (UUID pid : ordered.stream().map(Bullet::getProjectId).distinct().toList()) {
            projectById.put(pid, projects.get(userId, pid));
        }

        List<String> keywords = req.keywords() == null ? List.of() : req.keywords();
        List<String> courses = req.selectedCourses() == null ? List.of() : req.selectedCourses();
        Map<String, List<String>> skills = req.selectedSkills() == null ? Map.of() : req.selectedSkills();

        String tex = renderer.render(userId, ordered, projectById, courses, skills, keywords);
        String rendered = renderedText(ordered, skills, courses, projectById, keywords);
        JdCoverage.Matrix coverage = JdCoverage.of(req.jdText(), rendered, keywords);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String k : keywords) {
            if (k != null && !k.isBlank()) {
                (KeywordScorer.mentions(rendered, k) ? matched : missing).add(k);
            }
        }

        PdfCompiler.Result result = compiler.compile(tex);
        if (!result.success()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "tectonic failed: " + result.error() + "\n" + result.log());
        }

        Application a = new Application();
        a.setUserId(userId);
        a.setJdText(req.jdText());
        a.setJdUrl(req.jdUrl());
        a.setRoleEmphasis(req.roleEmphasis());
        a.setCompany(req.company());
        a.setRole(req.role());
        a.setCoverLetter(req.coverLetter());
        String source = String.join(" ", ordered.stream().map(Bullet::getText).toList())
                + " " + (req.jdText() == null ? "" : req.jdText());
        List<String> flags = req.coverLetter() == null
                ? List.of() : BulletTextRules.fabricatedNumbers(req.coverLetter(), source);
        a.setCoverLetterFlags(flags.toArray(new String[0]));
        a.setSelectedBulletIds(ids);
        a.setSelectedCourses(courses.toArray(new String[0]));
        try {
            a.setSelectedSkills(mapper.writeValueAsString(skills));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize selected skills", e);
        }
        // The application UI groups bullets by project from this JSON. Without it the page
        // renders but shows "N of 0 included" and no per-entry controls work.
        List<RankEntry> ranking = req.ranking() != null ? req.ranking()
                : java.util.stream.IntStream.range(0, ids.length)
                        .mapToObj(i -> new RankEntry(ids[i], i + 1, "Selected by the agent tailoring run."))
                        .toList();
        try {
            a.setBulletRanking(mapper.writeValueAsString(ranking));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize ranking", e);
        }
        a.setAtsMatched(matched.toArray(new String[0]));
        a.setAtsMissing(missing.toArray(new String[0]));
        a.setTexBlob(tex.getBytes(StandardCharsets.UTF_8));
        a.setPdfBlob(result.pdf());
        a.setTectonicLog(result.log());
        a.setPageCount(result.pageCount());
        a.setFitScore(req.fitScore());
        a.setFitVerdict(req.fitVerdict());
        if (req.fitStrengths() != null) a.setFitStrengths(req.fitStrengths().toArray(new String[0]));
        if (req.fitGaps() != null) a.setFitGaps(req.fitGaps().toArray(new String[0]));
        a.setPdfStale(false);
        a.setRecruiterStale(false);

        Application saved = applications.save(a);
        quota.recordTailor(userId, saved.getId());
        return saved;
    }

    /** Patch the agent's own fit assessment and ranking onto an existing application. */
    public Application updateScoring(UUID userId, UUID appId, UpdateAgentApplicationRequest req) {
        Application a = applications.findByUserIdAndId(userId, appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown application: " + appId));
        try {
            if (req.ranking() != null) a.setBulletRanking(mapper.writeValueAsString(req.ranking()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize ranking", e);
        }
        if (req.fitScore() != null) a.setFitScore(req.fitScore());
        if (req.fitVerdict() != null) a.setFitVerdict(req.fitVerdict());
        if (req.fitStrengths() != null) a.setFitStrengths(req.fitStrengths().toArray(new String[0]));
        if (req.fitGaps() != null) a.setFitGaps(req.fitGaps().toArray(new String[0]));
        // The agent can also supply the recruiter/page score when the server-side LLM is
        // unavailable, so the page-score card is not left blank.
        if (req.recruiterScore() != null) {
            a.setRecruiterScore(req.recruiterScore());
            a.setRecruiterStale(false);
        }
        if (req.recruiterVerdict() != null) a.setRecruiterVerdict(req.recruiterVerdict());
        if (req.recruiterWeaknesses() != null) a.setRecruiterWeaknesses(req.recruiterWeaknesses().toArray(new String[0]));
        if (req.recruiterThinnestRequirement() != null) a.setRecruiterThinnestRequirement(req.recruiterThinnestRequirement());
        return applications.save(a);
    }

    /** The text the template emits, shared by ATS and coverage scoring. */
    private static String renderedText(List<Bullet> selected, Map<String, List<String>> skills,
                                       List<String> courses, Map<UUID, Project> projectById,
                                       Collection<String> keywords) {
        List<String> listed = new ArrayList<>(
                ApplicationRenderer.projectHeadingTechLines(selected, projectById, keywords));
        if (skills != null) skills.values().forEach(listed::addAll);
        List<String> parts = new ArrayList<>(selected.stream().map(Bullet::getText).toList());
        parts.addAll(listed);
        parts.addAll(courses);
        return String.join("\n", parts);
    }

    /** Retired lens slugs map to general; unknown slugs never reach the DB. */
    private static String lens(String raw) {
        if (raw == null || raw.isBlank()) return "general";
        String s = raw.trim().toLowerCase();
        return CategoryLenses.LENSES.containsKey(s) ? s : "general";
    }
}
