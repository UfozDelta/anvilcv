package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.progress.ProgressLog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final JobProgressStore jobStore;

    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ApplicationController(ApplicationService service, JobProgressStore jobStore) {
        this.service = service;
        this.jobStore = jobStore;
    }

    @GetMapping
    public List<ApplicationSummary> list(Authentication auth,
                                         @RequestParam(required = false) String outcome) {
        return service.list(AuthUtils.userId(auth), outcome).stream().map(ApplicationSummary::from).toList();
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(Authentication auth, @RequestBody @Valid CreateApplicationRequest req) {
        UUID userId = AuthUtils.userId(auth); // capture before async dispatch
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        SSE_EXECUTOR.submit(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.create(userId, req.jdText(), req.jdUrl(), req.roleEmphasis(),
                        req.includeCoverLetter(), progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        return new SubmitResponse(jobId);
    }

    @GetMapping("/jobs/{jobId}/progress")
    public JobProgressResponse jobProgress(Authentication auth, @PathVariable UUID jobId) {
        if (!jobStore.isOwner(jobId, AuthUtils.userId(auth))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId);
        }
        JobProgressStore.Snapshot snap = jobStore.getSnapshot(jobId);
        return new JobProgressResponse(snap.lines(), snap.status().name(), snap.appId(), snap.error());
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(Authentication auth, @PathVariable UUID id,
                                   @RequestParam(defaultValue = "false") boolean includePdf) {
        return ApplicationResponse.from(service.get(AuthUtils.userId(auth), id), includePdf);
    }

    @PostMapping
    public ApplicationResponse create(Authentication auth, @RequestBody @Valid CreateApplicationRequest req,
                                      @RequestParam(defaultValue = "false") boolean includePdf) {
        Application a = service.create(AuthUtils.userId(auth), req.jdText(), req.jdUrl(),
                req.roleEmphasis(), req.includeCoverLetter(), ProgressLog.noOp());
        return ApplicationResponse.from(a, includePdf);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createStream(Authentication auth, @RequestBody @Valid CreateApplicationRequest req,
                                   HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setBufferSize(1);
        SseEmitter emitter = new SseEmitter(600_000L);
        UUID userId = AuthUtils.userId(auth); // capture before async dispatch

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
                Application a = service.create(userId, req.jdText(), req.jdUrl(),
                        req.roleEmphasis(), req.includeCoverLetter(), progress);
                emitter.send(SseEmitter.event().name("done").data(a.getId().toString()));
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.delete(AuthUtils.userId(auth), id);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse updateOutcome(Authentication auth, @PathVariable UUID id,
                                             @RequestBody @Valid UpdateOutcomeRequest req) {
        return ApplicationResponse.from(service.updateOutcome(AuthUtils.userId(auth), id, req.outcome()));
    }

    @PostMapping("/{id}/rerender")
    public ApplicationResponse rerender(Authentication auth, @PathVariable UUID id,
                                        @RequestBody RerenderRequest req) {
        return ApplicationResponse.from(service.rerender(AuthUtils.userId(auth), id,
                req.selectedBulletIds(), ProgressLog.noOp()));
    }

    @PostMapping(value = "/{id}/rerender/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rerenderStream(Authentication auth, @PathVariable UUID id,
                                     @RequestBody RerenderRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setBufferSize(1);
        SseEmitter emitter = new SseEmitter(60_000L);
        UUID userId = AuthUtils.userId(auth); // capture before async dispatch

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
                Application a = service.rerender(userId, id, req.selectedBulletIds(), progress);
                emitter.send(SseEmitter.event().name("done").data(a.getId().toString()));
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

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(Authentication auth, @PathVariable UUID id) {
        Application a = service.get(AuthUtils.userId(auth), id);
        if (a.getPdfBlob() == null || a.getPdfBlob().length == 0) {
            return ResponseEntity.status(404).body("No PDF stored (compile failed?)".getBytes());
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        String fname = "resume-" + (a.getCompany() == null ? "app" : a.getCompany().replaceAll("\\W+", "_")) + ".pdf";
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fname + "\"");
        return new ResponseEntity<>(a.getPdfBlob(), h, 200);
    }

    @GetMapping(value = "/{id}/cover-letter", produces = MediaType.TEXT_PLAIN_VALUE)
    public String coverLetter(Authentication auth, @PathVariable UUID id) {
        return service.get(AuthUtils.userId(auth), id).getCoverLetter();
    }
}
