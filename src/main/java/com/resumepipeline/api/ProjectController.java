package com.resumepipeline.api;

import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.ProjectDtos.CreateProjectRequest;
import com.resumepipeline.api.dto.ProjectDtos.ProjectResponse;
import com.resumepipeline.api.dto.ProjectDtos.UpdateProjectRequest;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
    public List<ProjectResponse> list(@RequestParam(required = false) Project.Kind kind) {
        var rows = kind == null ? projects.list() : projects.listByKind(kind);
        return rows.stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return ProjectResponse.from(projects.get(id));
    }

    @PostMapping
    public ProjectResponse create(@RequestBody @Valid CreateProjectRequest req) {
        Project p = projects.create(
                req.kind() == null ? Project.Kind.PROJECT : req.kind(),
                req.name(), req.description(), req.sourcePath(),
                req.title(), req.company(), req.location(), req.dates());
        return ProjectResponse.from(p);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        return ProjectResponse.from(projects.update(id,
                req.name(), req.description(), req.sourcePath(),
                req.title(), req.company(), req.location(), req.dates()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    @PostMapping("/{id}/bullets/generate")
    public List<BulletResponse> generateBullets(@PathVariable UUID id) {
        return bullets.generateForProject(id).stream().map(BulletResponse::from).toList();
    }

    public record GenerateBankRequest(List<String> categories) {}

    /** Blocking generate-bank kept for non-streaming callers. */
    @PostMapping("/{id}/bullets/generate-bank")
    public List<BulletResponse> generateBank(@PathVariable UUID id, @RequestBody GenerateBankRequest req) {
        return bullets.generateBank(id, req.categories(), ProgressLog.noOp())
                .stream().map(BulletResponse::from).toList();
    }

    /**
     * SSE generate-bank — streams real events for each bullet kept/cut/category.
     *
     * Event types:
     *   log  — one progress message (data = plain text)
     *   done — all categories complete (data = total bullet count)
     *   error — pipeline failed (data = error message)
     *
     * Why POST+SSE: we need the categories list in the body (can be large), and GET
     * query params would require URL encoding a list. Spring SseEmitter works fine
     * with POST.
     */
    @PostMapping(value = "/{id}/bullets/generate-bank/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateBankStream(@PathVariable UUID id, @RequestBody GenerateBankRequest req) {
        // Timeout: up to 8 categories × ~15s per LLM call = 120s worst case.
        SseEmitter emitter = new SseEmitter(120_000L);

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
                List<?> saved = bullets.generateBank(id, req.categories(), progress);
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
