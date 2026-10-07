package com.resumepipeline.api;

import com.resumepipeline.api.dto.ApplicationDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.obs.Mdc;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileRepository;
import com.resumepipeline.progress.ProgressLog;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

    private final ApplicationService service;
    private final JobProgressStore jobStore;
    private final ProfileRepository profiles;

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public ApplicationController(ApplicationService service, JobProgressStore jobStore, ProfileRepository profiles) {
        this.service = service;
        this.jobStore = jobStore;
        this.profiles = profiles;
    }

    @GetMapping
    public List<ApplicationSummary> list(Authentication auth,
                                         @RequestParam(required = false) String outcome) {
        return service.list(AuthUtils.userId(auth), outcome).stream().map(ApplicationSummary::from).toList();
    }

    @GetMapping("/outcome-history")
    public List<OutcomeHistoryEntry> outcomeHistory(Authentication auth) {
        return service.outcomeHistory(AuthUtils.userId(auth)).stream().map(OutcomeHistoryEntry::from).toList();
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(Authentication auth, @RequestBody @Valid CreateApplicationRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.create(userId, req.jdText(), req.jdUrl(), req.roleEmphasis(),
                        req.includeCoverLetter(), progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                log.error("APP_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }));
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

    /** Repeat warnings for a proposed page, in page order. Read-only: nothing is saved. */
    @PostMapping("/selection-check")
    public List<ApplicationService.SelectionWarning> selectionCheck(Authentication auth,
                                                                    @RequestBody RerenderRequest req) {
        return service.selectionWarnings(AuthUtils.userId(auth), req.selectedBulletIds());
    }

    @PostMapping("/{id}/rerender/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse rerenderSubmit(Authentication auth, @PathVariable UUID id,
                                         @RequestBody RerenderRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.rerender(userId, id, req.selectedBulletIds(), progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                log.error("APP_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }));
        return new SubmitResponse(jobId);
    }

    /**
     * Re-run the recruiter pass on the current selection. Async only: the pass has been observed
     * at 42.6s and this app sits behind a cloudflared tunnel, so a synchronous variant would
     * hold a request thread past the proxy's patience for no benefit.
     */
    @PostMapping("/{id}/rescore/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse rescoreSubmit(Authentication auth, @PathVariable UUID id) {
        UUID userId = AuthUtils.userId(auth);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.rescore(userId, id, progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                log.error("APP_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }));
        return new SubmitResponse(jobId);
    }

    @PatchMapping("/{id}/locks")
    public ApplicationResponse setLocked(Authentication auth, @PathVariable UUID id,
                                         @RequestBody LockRequest req) {
        return ApplicationResponse.from(service.setLocked(AuthUtils.userId(auth), id, req.lockedBulletIds()));
    }

    @PostMapping("/{id}/refit-selection")
    public ApplicationResponse refitSelection(Authentication auth, @PathVariable UUID id,
                                              @RequestBody(required = false) RefitRequest req) {
        return ApplicationResponse.from(
                service.refitSelection(AuthUtils.userId(auth), id, projectScope(req), ProgressLog.noOp()));
    }

    @PostMapping("/{id}/refit-selection/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse refitSelectionSubmit(Authentication auth, @PathVariable UUID id,
                                               @RequestBody(required = false) RefitRequest req) {
        UUID userId = AuthUtils.userId(auth);
        UUID scope = projectScope(req);
        UUID jobId = UUID.randomUUID();
        jobStore.start(jobId, userId);
        ASYNC_EXECUTOR.submit(Mdc.wrap(() -> {
            ProgressLog progress = msg -> jobStore.append(jobId, msg);
            try {
                Application a = service.refitSelection(userId, id, scope, progress);
                jobStore.complete(jobId, a.getId());
            } catch (Exception e) {
                log.error("APP_FAILED job={} cause={}", jobId, e.getMessage(), e);
                jobStore.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }));
        return new SubmitResponse(jobId);
    }

    /** Absent body and absent projectId both mean "re-pick the whole page". */
    private static UUID projectScope(RefitRequest req) {
        return req == null ? null : req.projectId();
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(Authentication auth, @PathVariable UUID id) {
        Application a = service.get(AuthUtils.userId(auth), id);
        if (a.getPdfBlob() == null || a.getPdfBlob().length == 0) {
            return ResponseEntity.status(404).body("No PDF stored (compile failed?)".getBytes());
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + baseFilename(auth, a) + ".pdf\"");
        return new ResponseEntity<>(a.getPdfBlob(), h, 200);
    }

    @GetMapping(value = "/{id}/tex", produces = "application/x-tex")
    public ResponseEntity<byte[]> tex(Authentication auth, @PathVariable UUID id) {
        Application a = service.get(AuthUtils.userId(auth), id);
        if (a.getTexBlob() == null || a.getTexBlob().length == 0) {
            return ResponseEntity.status(404).body("No LaTeX source stored".getBytes(StandardCharsets.UTF_8));
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-tex"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseFilename(auth, a) + ".tex\"");
        return new ResponseEntity<>(a.getTexBlob(), h, 200);
    }

    @GetMapping(value = "/{id}/cover-letter", produces = MediaType.TEXT_PLAIN_VALUE)
    public String coverLetter(Authentication auth, @PathVariable UUID id) {
        return service.get(AuthUtils.userId(auth), id).getCoverLetter();
    }

    /** Read-only lookup: a download must not create a profile as a side effect. */
    private String baseFilename(Authentication auth, Application a) {
        String name = profiles.findByUserId(AuthUtils.userId(auth)).map(Profile::getName).orElse(null);
        return resumeBase(name, a.getRole(), a.getCompany());
    }

    private static final Set<String> NAME_SUFFIXES = Set.of("jr", "sr", "ii", "iii", "iv");

    /** {Last}_{Role}_resume, ASCII-safe; role falls back to company, missing parts are dropped. */
    static String resumeBase(String name, String role, String company) {
        String last = "";
        if (name != null) {
            String[] tokens = name.trim().split("\\s+");
            for (int i = tokens.length - 1; i >= 0; i--) {
                if (!NAME_SUFFIXES.contains(tokens[i].replace(".", "").toLowerCase())) { last = tokens[i]; break; }
            }
        }
        String what = role != null && !role.isBlank() ? role : company;
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{safe(last, 30), safe(what, 40), "resume"}) {
            if (!part.isEmpty()) parts.add(part);
        }
        return String.join("_", parts);
    }

    private static String safe(String s, int max) {
        if (s == null) return "";
        String ascii = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String out = ascii.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (out.length() > max) out = out.substring(0, max).replaceAll("_+$", "");
        return out;
    }
}
