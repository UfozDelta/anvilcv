package com.resumepipeline.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.profile.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The shared job feed: verifies and stores postings pushed by the external Jobs API
 * webhook, and serves them to the public /jobs page.
 */
@Service
public class JobFeedService {

    static final int MAX_PAGE_SIZE = 100;
    static final int TOP_TAGS = 30;

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

    /**
     * Search filters shared by {@link #list} and {@link #counts}. {@code remote} widens the location
     * match to also take remote postings (alone it means remote only); {@code days} keeps postings
     * received in the last that many days. {@code stack} keeps postings tagged with any of those
     * techs; {@code skills} (null for off) does the same with the user's skill spellings, so an
     * empty list matches nothing.
     */
    public record JobFilter(String q, String location, boolean remote, Integer days,
                            List<String> stack, List<String> skills) {}

    /** {@code savedBy} non-null limits the list to postings that user saved. */
    public Page<JobPosting> list(String source, JobFilter f, UUID savedBy, int page, int size) {
        int clamped = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repo.findAll(spec(source, f, savedBy),
                PageRequest.of(Math.max(0, page), clamped, Sort.by(Sort.Direction.DESC, "receivedAt")));
    }

    /** Per-source and saved totals under the same filters as {@link #list}; saved is 0 for guests. */
    public JobCounts counts(JobFilter f, UUID userId) {
        return new JobCounts(
                repo.count(spec("linkedin", f, null)),
                repo.count(spec("indeed", f, null)),
                userId == null ? 0 : repo.count(spec(null, f, userId)));
    }

    public record JobCounts(long linkedin, long indeed, long saved) {}

    Specification<JobPosting> spec(String source, JobFilter f, UUID savedBy) {
        Specification<JobPosting> spec = (root, query, cb) -> cb.conjunction();
        if (savedBy != null) {
            List<UUID> ids = repo.savedIds(savedBy);
            spec = spec.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
        }
        if (source != null && !source.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        String q = f.q();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("company")), like),
                    cb.like(cb.lower(root.get("role")), like)));
        }
        String location = f.location();
        boolean hasLocation = location != null && !location.isBlank();
        if (hasLocation || f.remote()) {
            String like = hasLocation ? "%" + location.toLowerCase(Locale.ROOT) + "%" : null;
            spec = spec.and((root, query, cb) -> {
                var loc = cb.lower(root.<String>get("location"));
                if (!f.remote()) return cb.like(loc, like);
                var remote = cb.like(loc, "%remote%");
                return hasLocation ? cb.or(cb.like(loc, like), remote) : remote;
            });
        }
        if (f.stack() != null && f.stack().stream().anyMatch(t -> t != null && !t.isBlank())) {
            spec = spec.and(anyTag(f.stack()));
        }
        if (f.skills() != null) spec = spec.and(anyTag(f.skills()));
        if (f.days() != null && f.days() > 0) {
            Instant since = Instant.now().minus(f.days(), ChronoUnit.DAYS);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("receivedAt"), since));
        }
        return spec;
    }

    /** Postings whose stack holds any of {@code tags}, ignoring case. No usable tags matches nothing. */
    private static Specification<JobPosting> anyTag(Collection<String> tags) {
        List<String> patterns = tags.stream()
                .filter(t -> t != null)
                .map(t -> t.trim().toLowerCase(Locale.ROOT).replace(",", ""))
                .filter(t -> !t.isEmpty())
                .distinct()
                .map(t -> "%," + t.replace("!", "!!").replace("%", "!%").replace("_", "!_") + ",%")
                .toList();
        return (root, query, cb) -> {
            if (patterns.isEmpty()) return cb.disjunction();
            // ",java,react," - the fencing commas keep "java" from matching "javascript".
            Expression<String> joined = cb.lower(cb.concat(cb.concat(",",
                    cb.function("array_to_string", String.class, root.get("stack"), cb.literal(","))), ","));
            return cb.or(patterns.stream().map(p -> cb.like(joined, p, '!')).toArray(Predicate[]::new));
        };
    }

    /** The most common stack tags, most used first; spelled as stored, merged across case. */
    public List<String> topTags() {
        return repo.topTags(TOP_TAGS);
    }

    /** The user's language, framework, database, devops and AI &amp; integrations skills from their profile. */
    public static List<String> skillsOf(Profile p) {
        if (p == null) return List.of();
        return Stream.of(p.getSkillsLanguages(), p.getSkillsFrameworks(),
                        p.getSkillsDatabases(), p.getSkillsDevops(), p.getSkillsInterests())
                .filter(csv -> csv != null && !csv.isBlank())
                .flatMap(csv -> Arrays.stream(csv.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Every lower-cased spelling of the skills a stack tag might use, for the skills filter. */
    public static List<String> skillSpellings(List<String> skills) {
        return skills.stream().flatMap(s -> KeywordScorer.variants(s).stream()).distinct().toList();
    }

    /** The tags in {@code stack} that one of {@code skills} names, alias-aware ("K8s" for "Kubernetes"). */
    public static List<String> matchedTags(String[] stack, List<String> skills) {
        if (stack == null || skills.isEmpty()) return List.of();
        return Arrays.stream(stack)
                .filter(tag -> skills.stream().anyMatch(s -> KeywordScorer.names(s, tag)))
                .toList();
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
