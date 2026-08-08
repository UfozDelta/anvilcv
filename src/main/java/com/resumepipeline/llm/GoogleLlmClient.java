package com.resumepipeline.llm;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.PipelineTimer;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gemini transport for {@link LlmClient}. Everything about the prompts, the
 * word-count filter, and response parsing lives in {@link BaseLlmClient}; this
 * class only translates the provider-agnostic {@link SchemaSpec} into a Gemini
 * response schema and performs the SDK calls.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
public class GoogleLlmClient extends BaseLlmClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleLlmClient.class);

    private final Client client;
    private final String generateModel;
    private final String matchModel;
    private final String cleanJdModel;

    public GoogleLlmClient(
            @Value("${llm.gemini.api-key}") String apiKey,
            @Value("${llm.gemini.model.generate}") String generateModel,
            @Value("${llm.gemini.model.match}") String matchModel,
            @Value("${llm.gemini.model.clean-jd}") String cleanJdModel,
            GenerationConfigService configService) {
        super(configService);
        this.client = Client.builder().apiKey(apiKey).build();
        this.generateModel = generateModel;
        this.matchModel = matchModel;
        this.cleanJdModel = cleanJdModel;
    }

    @Override
    protected String generateModel() { return generateModel; }
    @Override
    protected String matchModel()    { return matchModel; }
    @Override
    protected String cleanJdModel()  { return cleanJdModel; }

    @Override
    protected String callJson(String model, String prompt, SchemaSpec spec, double temperature,
                              ProgressLog progress, TokenAccumulator tokens, boolean stream, String label) {
        Schema schema = toGoogleSchema(spec);
        if (stream) {
            return callStreaming(model, prompt, schema, temperature, label, progress, tokens);
        }
        return call(model, prompt, schema, temperature, tokens);
    }

    private Schema toGoogleSchema(SchemaSpec spec) {
        return switch (spec.type()) {
            case STRING  -> Schema.builder().type(Type.Known.STRING).build();
            case INTEGER -> Schema.builder().type(Type.Known.INTEGER).build();
            case ARRAY   -> Schema.builder().type(Type.Known.ARRAY).items(toGoogleSchema(spec.items())).build();
            case OBJECT  -> {
                Map<String, Schema> props = new LinkedHashMap<>();
                spec.properties().forEach((k, v) -> props.put(k, toGoogleSchema(v)));
                yield Schema.builder().type(Type.Known.OBJECT).properties(props).required(spec.required()).build();
            }
        };
    }

    private String call(String model, String prompt, Schema schema, double temperature, TokenAccumulator tokens) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature((float) temperature)
                .build();
        // Run on a separate thread so we can enforce a hard 2-minute timeout.
        // Without this, a stalled LLM response blocks the virtual thread forever.
        try {
            PipelineTimer tLlm = PipelineTimer.start("LLM " + model + " (promptLen=" + prompt.length() + ")");
            GenerateContentResponse[] respHolder = new GenerateContentResponse[1];
            String json = CompletableFuture
                    .supplyAsync(() -> {
                        GenerateContentResponse resp = client.models.generateContent(model, prompt, config);
                        respHolder[0] = resp;
                        return resp.text();
                    })
                    .get(120, TimeUnit.SECONDS);
            tLlm.stop("responseLen=" + (json == null ? 0 : json.length()));
            if (tokens != null && respHolder[0] != null) {
                respHolder[0].usageMetadata().ifPresent(u -> tokens.add(
                        model,
                        u.promptTokenCount().orElse(0),
                        u.candidatesTokenCount().orElse(0)));
            }
            log.debug("LLM {} raw: {}", model, json);
            return json;
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM call timed out after 2 minutes — Gemini may be overloaded, try again.", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        }
    }

    private String callStreaming(String model, String prompt, Schema schema,
                                 double temperature, String label, ProgressLog progress, TokenAccumulator tokens) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature((float) temperature)
                .build();
        try {
            PipelineTimer tLlm = PipelineTimer.start("LLM stream " + model + " (promptLen=" + prompt.length() + ")");
            int[] promptOut = {0, 0};
            String json = CompletableFuture
                    .supplyAsync(() -> {
                        ResponseStream<GenerateContentResponse> stream =
                                client.models.generateContentStream(model, prompt, config);
                        StringBuilder sb = new StringBuilder();
                        int chunks = 0;
                        GenerateContentResponse lastChunk = null;
                        for (GenerateContentResponse chunk : stream) {
                            String t = chunk.text();
                            if (t != null) sb.append(t);
                            chunks++;
                            lastChunk = chunk;
                            if (chunks % 10 == 0) {
                                // Rough word count — strip ** so bold tokens don't inflate count.
                                int words = sb.toString().replace("**", "").trim().split("\\s+").length;
                                progress.emit(label + "... (" + words + "w received)");
                            }
                        }
                        if (tokens != null && lastChunk != null) {
                            lastChunk.usageMetadata().ifPresent(u -> {
                                promptOut[0] = u.promptTokenCount().orElse(0);
                                promptOut[1] = u.candidatesTokenCount().orElse(0);
                            });
                        }
                        return sb.toString();
                    })
                    .get(120, TimeUnit.SECONDS);
            tLlm.stop("responseLen=" + (json == null ? 0 : json.length()));
            if (tokens != null && (promptOut[0] > 0 || promptOut[1] > 0)) {
                tokens.add(model, promptOut[0], promptOut[1]);
            }
            log.debug("LLM stream {} raw: {}", model, json);
            return json;
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM call timed out after 2 minutes — Gemini may be overloaded, try again.", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        }
    }
}
