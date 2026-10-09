package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.JobProgressResponse;
import com.resumepipeline.api.dto.ApplicationDtos.SubmitResponse;
import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.ProjectDtos.CreateProjectRequest;
import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.api.dto.ProjectDtos.UpdateProjectRequest;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.obs.Mdc;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projects;
    private final BulletService bullets;
    private final JobProgressStore jobStore;

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ProjectController(ProjectService projects, BulletService bullets, JobProgressStore jobStore) {
        this.projects = projects;
        this.bullets = bullets;
        this.jobStore = jobStore;
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication auth, @RequestParam(required = false) Project.Kind kind) {
        UUID userId = AuthUtils.userId(auth);
        var rows = kind == null ? projects.list(userId) : projects.listByKind(userId, kind);
        var counts = projects.bulletCounts(rows.stream().map(Project::getId).toList());
        return rows.stream().map(p -> ProjectResponse.from(p, counts.getOrDefault(p.getId(), 0L))).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(Authentication auth, @PathVariable UUID id) {
        return ProjectResponse.from(projects.get(AuthUtils.userId(auth), id), projects.bulletCount(id),
                projects.usableStoryCount(id));
    }

    @PostMapping
    public ProjectResponse create(Authentication auth, @RequestBody @Valid CreateProjectRequest req) {
        UUID userId = AuthUtils.userId(auth);
        Project p = projects.create(userId,
                req.kind() == null ? Project.Kind.PROJECT : req.kind(),
                req.name(), req.description(), req.githubUrl(),
                req.title(), req.company(), req.location(), req.dates(), req.current());
        return ProjectResponse.from(p);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(Authentication auth, @PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        Project saved = projects.update(AuthUtils.userId(auth), id,
                req.name(), req.description(), req.contextDescription(), req.githubUrl(),
                req.techStack(), req.yourRole(), req.ownership(), req.scaleImpact(), req.hardestProblem(),
                req.technicalDecisions(), req.userImpact(), req.securityPosture(),
                req.title(), req.company(), req.location(), req.dates(), req.current());
        return ProjectResponse.from(saved, projects.bulletCount(id));
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable UUID id) {
        projects.delete(AuthUtils.userId(auth), id);
    }

    @PostMapping("/{id}/duplicate")
    public ProjectResponse duplicate(Authentication auth, @PathVariable UUID id) {
        Project copy = projects.duplicate(AuthUtils.userId(auth), id);
        return ProjectResponse.from(copy, projects.bulletCount(copy.getId()));
    }

    @PostMapping("/{id}/bullets/generate")
    public List<BulletResponse> generateBullets(Authentication auth, @PathVariable UUID id) {
        return bullets.generateForProject(AuthUtils.userId(auth), id).stream().map(BulletResponse::from).toList();
    }

    /**
     * Re-measure this project's bullets against the user's length bands and rewrite the ones
     * that miss. Synchronous: it is a single LLM call over one batch, and it makes no call at
     * all when every bullet already fits.
     */
    @PostMapping("/{id}/bullets/refit")
    public RefitResponse refitBullets(Authentication auth, @PathVariable UUID id) {
        BulletService.RefitOutcome r = bullets.refit(AuthUtils.userId(auth), id, ProgressLog.noOp());
        return new RefitResponse(r.checked(), r.offBand(), r.rewritten(), r.unchanged(),
                r.bullets().stream().map(BulletResponse::from).toList());
    }

    public record RefitResponse(int checked, int offBand, int rewritten, int unchanged,
                                List<BulletResponse> bullets) {}

    /** Runs {@code work} as a background job with the live progress panel; returns the job id at once. */
    private SubmitResponse submitJob(UUID userId, UUID projectId, java.util.function.Consumer<ProgressLog> work) {
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                work.accept(progress);
                jobStore.complete(jobId, projectId);
            } catch (Exception e) {
                log.error("APP_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }));
        return new SubmitResponse(jobId);
    }

    public record StoriesResponse(int cap, List<StoryResponse> stories) {}
    public record StoryResponse(UUID id, String title, List<String> evidence, List<String> lenses, Instant createdAt) {}
    public record SubsystemsRequest(List<String> subsystems) {}
    public record WordingsRequest(List<String> lenses, List<String> subsystems) {}

    @GetMapping("/{id}/stories")
    public StoriesResponse stories(Authentication auth, @PathVariable UUID id) {
        List<StoryResponse> out = bullets.listStories(AuthUtils.userId(auth), id).stream()
                .map(s -> new StoryResponse(s.getId(), s.getTitle(), List.of(s.getEvidence()),
                        List.of(s.getLenses()), s.getCreatedAt()))
                .toList();
        return new StoriesResponse(BulletService.STORY_CAP, out);
    }

    /** "New stories": find stories across all lenses, one slot each. */
    @PostMapping("/{id}/stories/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse storiesSubmit(Authentication auth, @PathVariable UUID id,
                                        @RequestBody(required = false) SubsystemsRequest req) {
        UUID userId = AuthUtils.userId(auth);
        bullets.checkStories(userId, id);
        List<String> subs = req == null || req.subsystems() == null ? List.of() : req.subsystems();
        List<String> lenses = List.copyOf(CategoryLenses.LENSES.keySet());
        return submitJob(userId, id, progress -> bullets.generateBank(userId, id, lenses, subs, progress));
    }

    /** "More wordings" for one story. Bad input is answered now: 400, 404 or 409. */
    @PostMapping("/{id}/stories/{storyId}/wordings/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse wordingsSubmit(Authentication auth, @PathVariable UUID id, @PathVariable UUID storyId,
                                         @RequestBody WordingsRequest req) {
        UUID userId = AuthUtils.userId(auth);
        bullets.checkWordings(userId, id, storyId, req.lenses());
        List<String> subs = req.subsystems() == null ? List.of() : req.subsystems();
        return submitJob(userId, id, progress -> bullets.generateWordings(userId, id, storyId, req.lenses(), subs, progress));
    }

    @GetMapping("/jobs/{jobId}/progress")
    public JobProgressResponse bulletJobProgress(Authentication auth, @PathVariable UUID jobId) {
        if (!jobStore.isOwner(jobId, AuthUtils.userId(auth))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId);
        }
        JobProgressStore.Snapshot snap = jobStore.getSnapshot(jobId);
        return new JobProgressResponse(snap.lines(), snap.status().name(), snap.appId(), snap.error());
    }
}
