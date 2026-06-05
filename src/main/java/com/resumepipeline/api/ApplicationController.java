package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.progress.ProgressLog;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final JobProgressStore jobStore;

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ApplicationController(ApplicationService service, JobProgressStore jobStore) {
        this.service = service;
        this.jobStore = jobStore;
    }

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static String csvField(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @GetMapping
    public List<ApplicationSummary> list(Authentication auth,
                                         @RequestParam(required = false) String outcome) {
        return service.list(AuthUtils.userId(auth), outcome).stream().map(ApplicationSummary::from).toList();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication auth,
                                         @RequestParam(required = false) String outcome) {
        List<ApplicationSummary> apps = service.list(AuthUtils.userId(auth), outcome)
                .stream().map(ApplicationSummary::from).toList();
        StringBuilder csv = new StringBuilder("Company,Role,Outcome,Date\n");
        for (ApplicationSummary a : apps) {
            csv.append(csvField(a.company())).append(',')
               .append(csvField(a.role())).append(',')
               .append(csvField(a.outcome())).append(',')
               .append(CSV_DATE.format(a.createdAt())).append('\n');
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"applications.csv\"")
                .body(bytes);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(Authentication auth, @RequestBody @Valid CreateApplicationRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(() -> {
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

    @PostMapping("/{id}/rerender/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse rerenderSubmit(Authentication auth, @PathVariable UUID id,
                                         @RequestBody RerenderRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.rerender(userId, id, req.selectedBulletIds(), progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        return new SubmitResponse(jobId);
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
