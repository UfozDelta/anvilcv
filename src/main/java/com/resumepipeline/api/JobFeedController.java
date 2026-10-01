package com.resumepipeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.jobs.JobFeedService;
import com.resumepipeline.jobs.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public intern-job feed. The external Jobs API pushes {@code job.new} events to the
 * webhook (HMAC-signed); anyone can browse the list, and tailoring one needs a login.
 */
@RestController
@RequestMapping("/api/public/jobs")
public class JobFeedController {

    private static final Logger log = LoggerFactory.getLogger(JobFeedController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobFeedService service;

    public JobFeedController(JobFeedService service) {
        this.service = service;
    }

    public record JobDto(UUID id, String source, String title, String company, String location,
                         String posted, String spotted, String url, String companyUrl, String role,
                         List<String> stack, Instant receivedAt) {
        static JobDto of(JobPosting j) {
            return new JobDto(j.getId(), j.getSource(), j.getTitle(), j.getCompany(), j.getLocation(),
                    j.getPosted(), j.getSpotted(), j.getUrl(), j.getCompanyUrl(), j.getRole(),
                    List.of(j.getStack()), j.getReceivedAt());
        }
    }

    public record JobListResponse(List<JobDto> jobs, long total) {}

    @GetMapping
    public JobListResponse list(@RequestParam(required = false) String source,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String location,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        Page<JobPosting> p = service.list(source, q, location, page, size);
        return new JobListResponse(p.map(JobDto::of).getContent(), p.getTotalElements());
    }

    @PostMapping("/webhook")
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
