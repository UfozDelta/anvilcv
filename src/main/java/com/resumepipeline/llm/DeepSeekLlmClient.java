package com.resumepipeline.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible transport for {@link LlmClient}, pointed at an OpenAI-style
 * {@code POST /chat/completions} endpoint (DeepSeek, OpenRouter, a local
 * Ollama/LM Studio server, etc.). Defaults to the OpenCode Zen free tier
 * ({@code deepseek-v4-flash-free}).
 *
 * The provider only guarantees {@code response_format: json_object}, not schema
 * enforcement, so the expected JSON shape is spelled out in the system message
 * (from {@link SchemaSpec}) and the reply is parsed defensively.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek")
public class DeepSeekLlmClient extends BaseLlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient rest;
    private final String generateModel;
    private final String matchModel;
    private final String cleanJdModel;

    // Two constructors, so Spring needs to be told which one to wire — without this it
    // falls back to a no-arg constructor that does not exist.
    @Autowired
    public DeepSeekLlmClient(
            @Value("${llm.deepseek.base-url:https://opencode.ai/zen/v1}") String baseUrl,
            @Value("${llm.deepseek.api-key:}") String apiKey,
            @Value("${llm.deepseek.model.generate}") String generateModel,
            @Value("${llm.deepseek.model.match}") String matchModel,
            @Value("${llm.deepseek.model.clean-jd}") String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    DeepSeekLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                      String cleanJdModel, GenerationConfigService configService) {
        super(configService);
        this.rest = builder.build();
        this.generateModel = generateModel;
        this.matchModel = matchModel;
        this.cleanJdModel = cleanJdModel;
    }

    private static RestClient.Builder builder(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(20_000);
        factory.setReadTimeout(120_000);

        RestClient.Builder b = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return b;
    }

    @Override
    protected String generateModel() { return generateModel; }
    @Override
    protected String matchModel()    { return matchModel; }
    @Override
    protected String cleanJdModel()  { return cleanJdModel; }

    @Override
    protected String callJson(String model, String prompt, SchemaSpec schema, double temperature,
                              ProgressLog progress, TokenAccumulator tokens, boolean stream, String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are a JSON-only assistant. Respond with a single valid JSON object "
                                + "matching exactly this shape — no prose, no markdown fences:\n" + toJsonShape(schema)),
                Map.of("role", "user", "content", prompt)
        ));

        progress.emit(label + ": waiting on " + model + "...");
        try {
            String resp = rest.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(resp);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new RuntimeException("LLM response missing content: " + resp);
            }
            JsonNode usage = root.path("usage");
            if (tokens != null) {
                tokens.add(model,
                        usage.path("prompt_tokens").asInt(0),
                        usage.path("completion_tokens").asInt(0));
            }
            String json = extractJson(content.asText());
            log.debug("LLM {} raw: {}", model, resp);
            return json;
        } catch (RestClientException e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /** Strip markdown fences and surrounding prose so the reply parses as JSON. */
    private static String extractJson(String content) {
        if (content == null) return null;
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) s = s.substring(0, end);
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }

    /** Render the schema as a JSON skeleton the model can imitate, e.g. {"cleanJd":"string",...}. */
    private static String toJsonShape(SchemaSpec spec) {
        return switch (spec.type()) {
            case STRING  -> "\"string\"";
            case INTEGER -> "1";
            case ARRAY   -> "[" + toJsonShape(spec.items()) + "]";
            case OBJECT  -> {
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, SchemaSpec> e : spec.properties().entrySet()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append('"').append(e.getKey()).append("\": ").append(toJsonShape(e.getValue()));
                }
                sb.append('}');
                yield sb.toString();
            }
        };
    }
}
