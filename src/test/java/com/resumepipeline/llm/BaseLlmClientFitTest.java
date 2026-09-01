package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BaseLlmClientFitTest {

    @Test
    void overallIsTheMeanOfTheTwoDimensions() {
        assertEquals(50, BaseLlmClient.overallScore(40, 60));
        assertEquals(71, BaseLlmClient.overallScore(70, 71)); // .5 rounds up
        assertEquals(0, BaseLlmClient.overallScore(0, 0));
        assertEquals(100, BaseLlmClient.overallScore(100, 100));
    }

    @Test
    void verdictBandBoundaries() {
        assertEquals("Strong Fit", BaseLlmClient.verdictFor(100));
        assertEquals("Strong Fit", BaseLlmClient.verdictFor(75));
        assertEquals("Good Fit", BaseLlmClient.verdictFor(74));
        assertEquals("Good Fit", BaseLlmClient.verdictFor(60));
        assertEquals("Moderate Fit", BaseLlmClient.verdictFor(59));
        assertEquals("Moderate Fit", BaseLlmClient.verdictFor(45));
        assertEquals("Weak Fit", BaseLlmClient.verdictFor(44));
        assertEquals("Weak Fit", BaseLlmClient.verdictFor(30));
        assertEquals("Poor Fit", BaseLlmClient.verdictFor(29));
        assertEquals("Poor Fit", BaseLlmClient.verdictFor(0));
    }

    @Test
    void malformedFitReplyThrowsRatherThanScoringZero() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenCodeLlmClient client = new OpenCodeLlmClient(
                builder, "generate-model", "match-model", "clean-model", null);

        server.expect(requestTo("http://localhost:8080/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"sorry, I cannot score this"}}]}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.FitRequest req = new LlmClient.FitRequest(
                "clean jd", "Acme", "Engineer", List.of("java"), "backend", List.of(), List.of());

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> client.scoreFit(req, ProgressLog.noOp(), new TokenAccumulator()));
        assertTrue(e.getMessage().contains("sorry, I cannot score this"), e.getMessage());
        server.verify();
    }
}
