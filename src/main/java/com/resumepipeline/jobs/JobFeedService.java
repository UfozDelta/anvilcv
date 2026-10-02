package com.resumepipeline.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The shared job feed: verifies and stores postings pushed by the external Jobs API
 * webhook, and serves them to the public /jobs page.
 */
@Service
public class JobFeedService {

    static final int MAX_PAGE_SIZE = 100;

    private final JobPostingRepository repo;
    private final String secret;

    public JobFeedService(JobPostingRepository repo, @Value("${jobs.webhook-secret:}") String secret) {
        this.repo = repo;
        this.secret = secret;
    }

    /** The webhook is public, so with no secret configured it accepts nothing. */
    public boolean webhookEnabled() {
        return secret != null && !secret.isBlank();
    }

    /** Checks {@code X-Jobs-Signature: sha256=<hex HMAC-SHA256 of the raw body>}. */
    public boolean signatureValid(byte[] body, String header) {
        if (!webhookEnabled() || header == null || !header.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
            byte[] given = header.substring("sha256=".length()).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, given);
        } catch (Exception e) {
            return false;
        }
    }

    /** Stores one posting. Returns false when (source, id) is already stored. */
    public boolean ingest(JsonNode job) {
        if (job == null || !job.isObject()) throw new IllegalArgumentException("job object required");
        String source = text(job, "source");
        String externalId = text(job, "id");
        String url = httpUrl(text(job, "url"));
        if (source == null || externalId == null || url == null) {
            throw new IllegalArgumentException("job.source, job.id and an http(s) job.url are required");
        }
        if (repo.existsBySourceAndExternalId(source, externalId)) return false;

        List<String> stack = new ArrayList<>();
        JsonNode s = job.get("stack");
        if (s != null && s.isArray()) s.forEach(n -> { if (n.isTextual() && !n.asText().isBlank()) stack.add(n.asText()); });

        try {
            repo.save(new JobPosting(source, externalId, text(job, "title"), text(job, "company"),
                    text(job, "location"), text(job, "posted"), text(job, "spotted"), url,
                    httpUrl(text(job, "company_url")), text(job, "role"), stack.toArray(String[]::new)));
            return true;
        } catch (DataIntegrityViolationException e) {
            // A retried delivery raced the first one past the exists check.
            return false;
        }
    }

    /** {@code savedBy} non-null limits the list to postings that user saved. */
    public Page<JobPosting> list(String source, String q, String location, UUID savedBy, int page, int size) {
        Specification<JobPosting> spec = (root, query, cb) -> cb.conjunction();
        if (savedBy != null) {
            List<UUID> ids = repo.savedIds(savedBy);
            spec = spec.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
        }
        if (source != null && !source.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("company")), like),
                    cb.like(cb.lower(root.get("role")), like)));
        }
        if (location != null && !location.isBlank()) {
            String like = "%" + location.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), like));
        }
        int clamped = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repo.findAll(spec, PageRequest.of(Math.max(0, page), clamped, Sort.by(Sort.Direction.DESC, "receivedAt")));
    }

    /** Which of {@code jobIds} the user has saved. */
    public Set<UUID> savedAmong(UUID userId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) return Set.of();
        return new HashSet<>(repo.savedIdsAmong(userId, jobIds));
    }

    public void save(UUID userId, UUID jobId) {
        if (!repo.existsById(jobId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found: " + jobId);
        repo.save(userId, jobId);
    }

    public void unsave(UUID userId, UUID jobId) {
        repo.unsave(userId, jobId);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** Links are rendered as hrefs on a public page, so anything but http(s) is dropped. */
    static String httpUrl(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? url : null;
    }
}
