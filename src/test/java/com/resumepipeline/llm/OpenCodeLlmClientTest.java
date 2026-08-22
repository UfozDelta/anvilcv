package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenCodeLlmClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private OpenCodeLlmClient client(GenerationConfigService configService) {
        return new OpenCodeLlmClient(builder, "generate-model", "match-model", "clean-model", configService);
    }

    @Test
    void cleanJdPostsExpectedShapeAndMapsUsage() {
        OpenCodeLlmClient client = client(null);
        server.expect(requestTo("http://localhost:8080/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("clean-model"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("\"cleanJd\"")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"cleanJd\\":\\"clean jd\\",\\"company\\":\\"Acme\\",\\"role\\":\\"Engineer\\",\\"keywords\\":[\\"java\\",\\"spring\\"]}"}}],
                         "usage":{"prompt_tokens":100,"completion_tokens":50}}
                        """, MediaType.APPLICATION_JSON));

        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.JdCleanResult result = client.cleanJd("raw jd", ProgressLog.noOp(), tokens);

        assertEquals("clean jd", result.cleanJd());
        assertEquals("Acme", result.company());
        assertEquals("Engineer", result.role());
        assertEquals(List.of("java", "spring"), result.keywords());
        assertEquals(100, tokens.getPromptTokens());
        assertEquals(50, tokens.getCandidatesTokens());
        server.verify();
    }

    @Test
    void cleanJdStripsMarkdownFences() {
        OpenCodeLlmClient client = client(null);
        server.expect(anyRequest())
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"```json\\n{\\"cleanJd\\":\\"jd\\",\\"company\\":\\"Acme\\",\\"role\\":\\"Eng\\",\\"keywords\\":[]}\\n```"}}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1}}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.JdCleanResult result = client.cleanJd("x", ProgressLog.noOp(), new TokenAccumulator());

        assertEquals("jd", result.cleanJd());
        server.verify();
    }

    @Test
    void generateBulletsKeepsAllWhenFilterDisabled() {
        GenerationConfig cfg = new GenerationConfig();
        cfg.setWordFilterEnabled(false);
        GenerationConfigService configService = new GenerationConfigService(null) {
            @Override
            public GenerationConfig get(UUID userId) {
                return cfg;
            }
        };

        OpenCodeLlmClient client = client(configService);
        server.expect(anyRequest())
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"bullets\\":[{\\"text\\":\\"Built a thing.\\",\\"tags\\":[\\"backend\\"]},{\\"text\\":\\"Another one.\\",\\"tags\\":[\\"data\\"]}]}"}}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.BulletGenerationResult result = client.generateBullets(
                new LlmClient.GenerateBulletsRequest(UUID.randomUUID(), LlmClient.SourceKind.PROJECT,
                        "general", "proj", "desc", null, "Java", null, null, null, null,
                        null, null, null, null),
                ProgressLog.noOp(), new TokenAccumulator());

        assertEquals(2, result.bullets().size());
        assertEquals("Built a thing.", result.bullets().get(0).text());
        assertEquals(List.of("backend"), result.bullets().get(0).tags());
        server.verify();
    }

    @Test
    void serverErrorBecomesRuntimeException() {
        OpenCodeLlmClient client = client(null);
        server.expect(anyRequest())
                .andRespond(withServerError().body("{\"error\":\"upstream down\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThrows(RuntimeException.class,
                () -> client.cleanJd("x", ProgressLog.noOp(), new TokenAccumulator()));
        server.verify();
    }

    private static org.springframework.test.web.client.RequestMatcher anyRequest() {
        return request -> {
        };
    }
}
