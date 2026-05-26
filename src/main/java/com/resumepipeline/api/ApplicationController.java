package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.progress.ProgressLog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

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

    // Virtual-thread executor — one thread per SSE request, cheap on JDK 21+.
    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ApplicationController(ApplicationService service, JobProgressStore jobStore) {
        this.service = service;
        this.jobStore = jobStore;
    }

    @GetMapping
    public List<ApplicationSummary> list(@RequestParam(required = false) String outcome) {
        return service.list(outcome).stream().map(ApplicationSummary::from).toList();
    }

    /**
     * Async submit — starts the pipeline in the background and returns a job ID
     * immediately. Frontend polls /jobs/{jobId}/progress for incremental updates.
     * Avoids SSE streaming entirely, which was batched by React 18's scheduler.
     */
    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(@RequestBody @Valid CreateApplicationRequest req) {
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId);
        SSE_EXECUTOR.submit(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.create(req.jdText(), req.jdUrl(), req.roleEmphasis(), req.includeCoverLetter(), progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        return new SubmitResponse(jobId);
    }

    /** Poll endpoint — returns accumulated log lines + current status for a job. */
    @GetMapping("/jobs/{jobId}/progress")
    public JobProgressResponse jobProgress(@PathVariable UUID jobId) {
        JobProgressStore.Snapshot snap = jobStore.getSnapshot(jobId);
        if (snap == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId);
        return new JobProgressResponse(snap.lines(), snap.status().name(), snap.appId(), snap.error());
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "false") boolean includePdf) {
        return ApplicationResponse.from(service.get(id), includePdf);
    }

    /** Blocking endpoint kept for non-streaming callers. */
    @PostMapping
    public ApplicationResponse create(@RequestBody @Valid CreateApplicationRequest req,
                                      @RequestParam(defaultValue = "false") boolean includePdf) {
        Application a = service.create(req.jdText(), req.jdUrl(), req.roleEmphasis(), req.includeCoverLetter(), ProgressLog.noOp());
        return ApplicationResponse.from(a, includePdf);
    }

    /**
     * SSE endpoint for resume creation. Streams real progress events as they happen,
     * then sends a final "done" event carrying the application ID so the frontend
     * can navigate to the result page without polling.
     *
     * Event types:
     *   log  — one progress message (data = plain text)
     *   done — pipeline complete (data = application UUID)
     *   error — pipeline failed (data = error message)
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createStream(@RequestBody @Valid CreateApplicationRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setBufferSize(1);
        // Timeout matches worst-case pipeline: JD fetch + 2 LLM calls + tectonic compile.
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min: 3 sequential LLM calls can take 3-6 min total

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
                Application a = service.create(req.jdText(), req.jdUrl(), req.roleEmphasis(), req.includeCoverLetter(), progress);
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
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse updateOutcome(@PathVariable UUID id,
                                             @RequestBody @Valid UpdateOutcomeRequest req) {
        return ApplicationResponse.from(service.updateOutcome(id, req.outcome()));
    }

    /** Blocking rerender kept for non-streaming callers. */
    @PostMapping("/{id}/rerender")
    public ApplicationResponse rerender(@PathVariable UUID id, @RequestBody RerenderRequest req) {
        return ApplicationResponse.from(service.rerender(id, req.selectedBulletIds(), ProgressLog.noOp()));
    }

    /**
     * SSE rerender — same event protocol as /stream above.
     * Streams LaTeX render + tectonic compile progress, then sends done with app ID.
     */
    @PostMapping(value = "/{id}/rerender/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rerenderStream(@PathVariable UUID id, @RequestBody RerenderRequest req, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setBufferSize(1);
        SseEmitter emitter = new SseEmitter(60_000L);

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
                Application a = service.rerender(id, req.selectedBulletIds(), progress);
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
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Application a = service.get(id);
        if (a.getPdfBlob() == null || a.getPdfBlob().length == 0) {
            return ResponseEntity.status(404).body("No PDF stored (compile failed?)".getBytes());
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        String fname = "resume-" + (a.getCompany() == null ? "app" : a.getCompany().replaceAll("\\W+", "_")) + ".pdf";
        // inline disposition tells the browser to render in iframe, not force-download.
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fname + "\"");
        return new ResponseEntity<>(a.getPdfBlob(), h, 200);
    }

    @GetMapping(value = "/{id}/cover-letter", produces = MediaType.TEXT_PLAIN_VALUE)
    public String coverLetter(@PathVariable UUID id) {
        return service.get(id).getCoverLetter();
    }
}
