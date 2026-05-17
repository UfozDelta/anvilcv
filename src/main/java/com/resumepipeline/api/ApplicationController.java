package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.progress.ProgressLog;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    // Virtual-thread executor — one thread per SSE request, cheap on JDK 21+.
    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationSummary> list(@RequestParam(required = false) String outcome) {
        return service.list(outcome).stream().map(ApplicationSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return ApplicationResponse.from(service.get(id));
    }

    /** Blocking endpoint kept for non-streaming callers. */
    @PostMapping
    public ApplicationResponse create(@RequestBody @Valid CreateApplicationRequest req) {
        Application a = service.create(req.jdText(), req.jdUrl(), req.roleEmphasis(), ProgressLog.noOp());
        return ApplicationResponse.from(a);
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
    public SseEmitter createStream(@RequestBody @Valid CreateApplicationRequest req) {
        // Timeout matches worst-case pipeline: JD fetch + 2 LLM calls + tectonic compile.
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
                Application a = service.create(req.jdText(), req.jdUrl(), req.roleEmphasis(), progress);
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
    public SseEmitter rerenderStream(@PathVariable UUID id, @RequestBody RerenderRequest req) {
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
