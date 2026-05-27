package com.resumepipeline.api;

import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.ProjectDtos.CreateProjectRequest;
import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.api.dto.ProjectDtos.UpdateProjectRequest;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;
    private final BulletService bullets;

    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ProjectController(ProjectService projects, BulletService bullets) {
        this.projects = projects;
        this.bullets = bullets;
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
                req.name(), req.description(), req.sourcePath(),
                req.title(), req.company(), req.location(), req.dates());
        return ProjectResponse.from(p);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(Authentication auth, @PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        return ProjectResponse.from(projects.update(AuthUtils.userId(auth), id,
                req.name(), req.description(), req.sourcePath(),
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

    @PostMapping(value = "/{id}/bullets/generate-bank/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateBankStream(Authentication auth, @PathVariable UUID id,
                                         @RequestBody GenerateBankRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setBufferSize(1);
        SseEmitter emitter = new SseEmitter(120_000L);
        // Capture userId before dispatch — SecurityContext is not propagated to virtual threads.
        UUID userId = AuthUtils.userId(auth);

        SSE_EXECUTOR.submit(() -> {
            ScheduledFuture<?> keepalive = SseUtils.startKeepalive(emitter);
            ProgressLog progress = message -> {
                try {
                    emitter.send(SseEmitter.event().name("log").data(message));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            };

            try {
                List<?> saved = bullets.generateBank(userId, id, req.categories(), progress);
                emitter.send(SseEmitter.event().name("done").data(String.valueOf(saved.size())));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                keepalive.cancel(false);
            }
        });

        return emitter;
    }
}
