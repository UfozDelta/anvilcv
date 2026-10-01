package com.resumepipeline.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
