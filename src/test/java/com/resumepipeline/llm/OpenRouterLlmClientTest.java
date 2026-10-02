package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouterLlmClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenRouterLlmClient client =
            new OpenRouterLlmClient(builder, "deepseek/deepseek-v4.1-flash", "match-model", "clean-model", null);

    private static final BaseLlmClient.SchemaSpec SCHEMA = BaseLlmClient.SchemaSpec.object(
            Map.of("ok", BaseLlmClient.SchemaSpec.string()), List.of("ok"));

    private static final String REPLY = """
            {"choices":[{"message":{"content":"{\\"ok\\":\\"yes\\"}"}}],
             "usage":{"prompt_tokens":1000,"completion_tokens":200,"cost":0.00054}}
            """;

    @Test
    void latencyCriticalCallTurnsReasoningOffAndDeniesDataCollection() {
        server.expect(requestTo("http://localhost:8080/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reasoning.effort").value("none"))
                .andExpect(jsonPath("$.provider.data_collection").value("deny"))
                .andExpect(jsonPath("$.usage.include").value(true))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        client.callJson("match-model", "rank these", SCHEMA, 1.0, ProgressLog.noOp(),
                new TokenAccumulator(), true, "Ranking");
        server.verify();
    }

    @Test
    void otherCallsKeepTheModelsDefaultReasoning() {
        server.expect(requestTo("http://localhost:8080/chat/completions"))
                .andExpect(jsonPath("$.reasoning").doesNotExist())
                .andExpect(jsonPath("$.provider.data_collection").value("deny"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        client.callJson("deepseek/deepseek-v4.1-flash", "write bullets", SCHEMA, 1.0, ProgressLog.noOp(),
                new TokenAccumulator(), false, "Bullets");
        server.verify();
    }

    @Test
    void recordsTheBilledCostOpenRouterReports() {
        server.expect(requestTo("http://localhost:8080/chat/completions"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        TokenAccumulator tokens = new TokenAccumulator();
        client.callJson("match-model", "x", SCHEMA, 1.0, ProgressLog.noOp(), tokens, false, "Fit score");

        assertEquals(1000, tokens.getPromptTokens());
        assertEquals(200, tokens.getCandidatesTokens());
        // The reported $0.00054, not 1000/200 tokens priced at the Gemini rate table.
        assertEquals(0, new BigDecimal("0.00054").compareTo(tokens.getCostUsd()));
        server.verify();
    }
}
