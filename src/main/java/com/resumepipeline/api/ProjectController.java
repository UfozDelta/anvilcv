package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.JobProgressResponse;
import com.resumepipeline.api.dto.ApplicationDtos.SubmitResponse;
import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.ProjectDtos.CreateProjectRequest;
import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.api.dto.ProjectDtos.UpdateProjectRequest;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

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
        return rows.stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(Authentication auth, @PathVariable UUID id) {
        return ProjectResponse.from(projects.get(AuthUtils.userId(auth), id));
    }

    @PostMapping
    public ProjectResponse create(Authentication auth, @RequestBody @Valid CreateProjectRequest req) {
        UUID userId = AuthUtils.userId(auth);
        Project p = projects.create(userId,
                req.kind() == null ? Project.Kind.PROJECT : req.kind(),
                req.name(), req.description(), req.githubUrl(),
                req.title(), req.company(), req.location(), req.dates());
        return ProjectResponse.from(p);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(Authentication auth, @PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        return ProjectResponse.from(projects.update(AuthUtils.userId(auth), id,
                req.name(), req.description(), req.githubUrl(),
                req.techStack(), req.yourRole(), req.ownership(), req.scaleImpact(), req.hardestProblem(),
                req.technicalDecisions(), req.userImpact(), req.securityPosture(),
                req.title(), req.company(), req.location(), req.dates()));
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable UUID id) {
        projects.delete(AuthUtils.userId(auth), id);
    }

    @PostMapping("/{id}/bullets/generate")
    public List<BulletResponse> generateBullets(Authentication auth, @PathVariable UUID id) {
        return bullets.generateForProject(AuthUtils.userId(auth), id).stream().map(BulletResponse::from).toList();
    }

    public record GenerateBankRequest(List<String> categories) {}

    @PostMapping("/{id}/bullets/generate-bank")
    public List<BulletResponse> generateBank(Authentication auth, @PathVariable UUID id,
                                             @RequestBody GenerateBankRequest req) {
        return bullets.generateBank(AuthUtils.userId(auth), id, req.categories(), ProgressLog.noOp())
                .stream().map(BulletResponse::from).toList();
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

    @PostMapping("/{id}/bullets/generate-bank/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse generateBankSubmit(Authentication auth, @PathVariable UUID id,
                                             @RequestBody GenerateBankRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                bullets.generateBank(userId, id, req.categories(), progress);
                jobStore.complete(jobId, id);
            } catch (Exception e) {
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        return new SubmitResponse(jobId);
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
