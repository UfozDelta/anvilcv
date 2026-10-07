package com.resumepipeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.auth.AppUserPrincipal;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.jobs.JobFeedService;
import com.resumepipeline.jobs.JobPosting;
import com.resumepipeline.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public intern-job feed. The external Jobs API pushes {@code job.new} events to the
 * webhook (HMAC-signed); anyone can browse the list, and tailoring or saving one needs a login.
 */
@RestController
@RequestMapping("/api")
public class JobFeedController {

    private static final Logger log = LoggerFactory.getLogger(JobFeedController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobFeedService service;
    private final ProfileRepository profiles;

    public JobFeedController(JobFeedService service, ProfileRepository profiles) {
        this.service = service;
        this.profiles = profiles;
    }

    public record JobDto(UUID id, String source, String title, String company, String location,
                         String posted, String spotted, String url, String companyUrl, String role,
                         List<String> stack, Instant receivedAt, boolean saved, List<String> matched) {
        static JobDto of(JobPosting j, boolean saved, List<String> skills) {
            return new JobDto(j.getId(), j.getSource(), j.getTitle(), j.getCompany(), j.getLocation(),
                    j.getPosted(), j.getSpotted(), j.getUrl(), j.getCompanyUrl(), j.getRole(),
                    List.of(j.getStack()), j.getReceivedAt(), saved, JobFeedService.matchedTags(j.getStack(), skills));
        }
    }

    public record JobListResponse(List<JobDto> jobs, long total, JobFeedService.JobCounts counts) {}

    /** Public; a signed-in caller also gets their saved flags and may filter on them. */
    @GetMapping("/public/jobs")
    public JobListResponse list(@RequestParam(required = false) String source,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String location,
                                @RequestParam(defaultValue = "false") boolean remote,
                                @RequestParam(required = false) Integer days,
                                @RequestParam(required = false) List<String> stack,
                                @RequestParam(defaultValue = "false") boolean mine,
                                @RequestParam(defaultValue = "false") boolean saved,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                Authentication auth) {
        // permitAll still runs the session, so a logged-in user arrives with their principal.
        UUID userId = auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal ? principal.getUserId() : null;
        // Signed-in callers get their skills matched on every card; "mine" also filters on them.
        List<String> skills = userId == null ? List.of()
                : JobFeedService.skillsOf(profiles.findByUserId(userId).orElse(null));
        JobFeedService.JobFilter filter = new JobFeedService.JobFilter(q, location, remote, days, stack,
                mine && userId != null ? JobFeedService.skillSpellings(skills) : null);
        JobFeedService.JobCounts counts = service.counts(filter, userId);
        if (saved && userId == null) return new JobListResponse(List.of(), 0, counts);

        Page<JobPosting> p = service.list(source, filter, saved ? userId : null, page, size);
        Set<UUID> savedIds = userId == null ? Set.of()
                : service.savedAmong(userId, p.map(JobPosting::getId).getContent());
        return new JobListResponse(p.map(j -> JobDto.of(j, savedIds.contains(j.getId()), skills)).getContent(),
                p.getTotalElements(), counts);
    }

    /** The most common stack tags, for the filter picker. */
    @GetMapping("/public/jobs/tags")
    public List<String> tags() {
        return service.topTags();
    }

    @PutMapping("/jobs/{id}/save")
    public ResponseEntity<Void> save(@PathVariable UUID id, Authentication auth) {
        service.save(AuthUtils.userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/jobs/{id}/save")
    public ResponseEntity<Void> unsave(@PathVariable UUID id, Authentication auth) {
        service.unsave(AuthUtils.userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/public/jobs/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] body,
                                        @RequestHeader(value = "X-Jobs-Signature", required = false) String signature)
            throws IOException {
        if (!service.webhookEnabled()) return ResponseEntity.status(503).build();
        if (!service.signatureValid(body, signature)) return ResponseEntity.status(401).build();

        JsonNode event = MAPPER.readTree(body);
        if (event == null || !"job.new".equals(event.path("event").asText())) {
            return ResponseEntity.noContent().build();
        }
        boolean stored = service.ingest(event.get("job"));
        log.info("JOB_WEBHOOK stored={}", stored);
        return ResponseEntity.noContent().build();
    }
}
