package com.resumepipeline.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobFeedServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JobPostingRepository repo = mock(JobPostingRepository.class);
    private final JobFeedService service = new JobFeedService(repo, "s3cret");

    private static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    void acceptsSignatureOverRawBody() throws Exception {
        byte[] body = "{\"event\":\"job.new\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(service.signatureValid(body, sign("s3cret", body))).isTrue();
    }

    @Test
    void rejectsWrongSecretMissingHeaderAndTamperedBody() throws Exception {
        byte[] body = "{\"event\":\"job.new\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(service.signatureValid(body, sign("other", body))).isFalse();
        assertThat(service.signatureValid(body, null)).isFalse();
        assertThat(service.signatureValid("{}".getBytes(StandardCharsets.UTF_8), sign("s3cret", body))).isFalse();
    }

    @Test
    void blankSecretDisablesWebhook() throws Exception {
        JobFeedService open = new JobFeedService(repo, " ");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(open.webhookEnabled()).isFalse();
        assertThat(open.signatureValid(body, sign(" ", body))).isFalse();
    }

    @Test
    void storesNewPostingWithStack() throws Exception {
        boolean stored = service.ingest(json("""
                {"source":"linkedin","id":"42","title":"SWE Intern","company":"Acme",
                 "url":"https://www.linkedin.com/jobs/view/42","company_url":"javascript:alert(1)",
                 "stack":["Java","",  "React"]}"""));

        assertThat(stored).isTrue();
        ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getExternalId()).isEqualTo("42");
        assertThat(saved.getValue().getStack()).containsExactly("Java", "React");
        assertThat(saved.getValue().getCompanyUrl()).isNull();
    }

    @Test
    void duplicateIsSkipped() throws Exception {
        when(repo.existsBySourceAndExternalId("indeed", "7")).thenReturn(true);
        assertThat(service.ingest(json("{\"source\":\"indeed\",\"id\":\"7\",\"url\":\"https://indeed.com/x\"}"))).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    void concurrentDuplicateInsertIsSkipped() throws Exception {
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertThat(service.ingest(json("{\"source\":\"indeed\",\"id\":\"7\",\"url\":\"https://indeed.com/x\"}"))).isFalse();
    }

    @Test
    void rejectsPostingWithoutHttpUrl() {
        assertThatThrownBy(() -> service.ingest(json("{\"source\":\"indeed\",\"id\":\"7\",\"url\":\"javascript:alert(1)\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ingest(json("{\"source\":\"indeed\",\"url\":\"https://indeed.com/x\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savingUnknownPostingIs404() {
        UUID user = UUID.randomUUID(), job = UUID.randomUUID();
        when(repo.existsById(job)).thenReturn(false);
        assertThatThrownBy(() -> service.save(user, job)).isInstanceOf(ResponseStatusException.class);
        verify(repo, never()).save(user, job);
    }

    @Test
    void savesExistingPosting() {
        UUID user = UUID.randomUUID(), job = UUID.randomUUID();
        when(repo.existsById(job)).thenReturn(true);
        service.save(user, job);
        verify(repo).save(user, job);
    }

    @Test
    void savedAmongSkipsQueryForEmptyPage() {
        assertThat(service.savedAmong(UUID.randomUUID(), List.of())).isEmpty();
        verify(repo, never()).savedIdsAmong(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsAreZeroSavedForGuests() {
        when(repo.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(3L, 4L);

        JobFeedService.JobCounts c = service.counts(new JobFeedService.JobFilter("intern", null, false, null, null, null), null);

        assertThat(c).isEqualTo(new JobFeedService.JobCounts(3, 4, 0));
        verify(repo, times(2)).count(any(org.springframework.data.jpa.domain.Specification.class));
        verify(repo, never()).savedIds(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsIncludeSavedForSignedInUser() {
        UUID user = UUID.randomUUID();
        when(repo.savedIds(user)).thenReturn(List.of(UUID.randomUUID()));
        when(repo.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(3L, 4L, 2L);

        assertThat(service.counts(new JobFeedService.JobFilter(null, null, false, null, null, null), user)).isEqualTo(new JobFeedService.JobCounts(3, 4, 2));
    }

    // The repo is mocked, so these check which predicates a filter builds rather than the SQL result.

    @SuppressWarnings("unchecked")
    private static Root<JobPosting> root(CriteriaBuilder cb) {
        Root<JobPosting> root = mock(Root.class);
        when(root.get(anyString())).thenReturn(mock(Path.class));
        when(cb.lower(any())).thenReturn(mock(Expression.class));
        return root;
    }

    @Test
    void remoteAloneMatchesOnlyRemoteLocations() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, null, true, null, null, null), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb).like(any(), eq("%remote%"));
        verify(cb, never()).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void remoteWidensLocationInsteadOfNarrowingIt() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, "NYC", true, null, null, null), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb).like(any(), eq("%nyc%"));
        verify(cb).like(any(), eq("%remote%"));
        verify(cb).or(any(), any());
    }

    @Test
    void daysKeepsRecentlyReceivedPostings() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Root<JobPosting> root = root(cb);
        service.spec(null, new JobFeedService.JobFilter(null, null, false, 7, null, null), null)
                .toPredicate(root, mock(CriteriaQuery.class), cb);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(root).get("receivedAt");
        verify(cb).greaterThanOrEqualTo(any(Expression.class), since.capture());
        assertThat(since.getValue()).isBetween(Instant.now().minus(Duration.ofDays(7)).minusSeconds(5),
                Instant.now().minus(Duration.ofDays(7)));
    }

    @Test
    void zeroDaysAndNoRemoteAddNoPredicates() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, null, false, 0, null, null), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb, never()).like(any(), anyString());
        verify(cb, never()).greaterThanOrEqualTo(any(Expression.class), any(Instant.class));
    }

    @Test
    void stackFilterMatchesAnyTagCaseInsensitivelyAndWholeTag() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, null, false, null, List.of("Java", " react ", "java", "c_%"), null), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb).function(eq("array_to_string"), eq(String.class), any(), any());
        verify(cb).like(any(), eq("%,java,%"), eq('!'));
        verify(cb).like(any(), eq("%,react,%"), eq('!'));
        verify(cb).like(any(), eq("%,c!_!%,%"), eq('!'));
        verify(cb, times(3)).like(any(), anyString(), anyChar());
    }

    @Test
    void blankStackFilterIsIgnored() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, null, false, null, List.of(" "), null), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb, never()).function(anyString(), any(), any());
        verify(cb, never()).disjunction();
    }

    @Test
    void skillsFilterWithNoSkillsMatchesNothing() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        service.spec(null, new JobFeedService.JobFilter(null, null, false, null, null, List.of()), null)
                .toPredicate(root(cb), mock(CriteriaQuery.class), cb);

        verify(cb).disjunction();
    }

    @Test
    void skillSpellingsIncludeAliases() {
        assertThat(JobFeedService.skillSpellings(List.of("Postgres", "Node.js")))
                .contains("postgres", "postgresql", "psql", "node.js", "nodejs");
    }

    @Test
    void matchedTagsAreAliasAware() {
        String[] stack = {"K8s", "PostgreSQL", "Rust", "React"};
        assertThat(JobFeedService.matchedTags(stack, List.of("Kubernetes", "Postgres", "React/Redux")))
                .containsExactly("K8s", "PostgreSQL", "React");
        assertThat(JobFeedService.matchedTags(stack, List.of())).isEmpty();
    }

    @Test
    void skillsOfIncludesTheAiAndIntegrationsRow() {
        com.resumepipeline.profile.Profile p = new com.resumepipeline.profile.Profile();
        p.setSkillsLanguages("Java");
        p.setSkillsInterests("LangChain, OpenAI API");
        assertThat(JobFeedService.skillsOf(p)).containsExactly("Java", "LangChain", "OpenAI API");
    }
}
