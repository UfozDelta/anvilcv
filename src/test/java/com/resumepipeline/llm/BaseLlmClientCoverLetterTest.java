package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BaseLlmClientCoverLetterTest {

    private static final String URL = "http://localhost:8080/chat/completions";

    @Test
    void nullCompanyUsesGenericGreetingAndPromptCarriesRole() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenCodeLlmClient client = new OpenCodeLlmClient(
                builder, "generate-model", "match-model", "clean-model", null);

        server.expect(requestTo(URL))
                .andExpect(content().string(allOf(
                        containsString("Hi hiring team,"),
                        containsString("Backend Engineer"),
                        not(containsString("Hi the team")),
                        not(containsString("Company: null")))))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"coverLetter\\":\\"Hi hiring team,\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.CoverLetterRequest req = new LlmClient.CoverLetterRequest(
                "clean jd", null, "Backend Engineer", "backend", List.of("bullet"), List.of(), List.of(), List.of());

        assertEquals("Hi hiring team,", client.coverLetter(req, ProgressLog.noOp(), new TokenAccumulator()));
        server.verify();
    }

    @Test
    void malformedCoverLetterReplyIsRetriedOnce() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenCodeLlmClient client = new OpenCodeLlmClient(
                builder, "generate-model", "match-model", "clean-model", null);

        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"sorry, no json here"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"coverLetter\\":\\"Hi Acme team,\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.CoverLetterRequest req = new LlmClient.CoverLetterRequest(
                "clean jd", "Acme", "Engineer", "backend", List.of("bullet"), List.of(), List.of(), List.of());

        assertEquals("Hi Acme team,", client.coverLetter(req, ProgressLog.noOp(), new TokenAccumulator()));
        server.verify();
    }
}
