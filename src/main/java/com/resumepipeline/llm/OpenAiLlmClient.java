package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import org.springframework.web.client.RestClient;

/**
 * Generic OpenAI-compatible provider. Defaults to the real OpenAI API, but the
 * base URL is freely overridable to point at OpenRouter, a local Ollama/LM Studio
 * server, or any other OpenAI-compatible {@code /chat/completions} endpoint.
 *
 * Not a Spring bean: {@link RoutingLlmClient} constructs it from the admin-managed
 * settings row so the provider can be switched without a redeploy.
 */
public class OpenAiLlmClient extends OpenAiCompatibleLlmClient {

    public OpenAiLlmClient(
            String baseUrl,
            String apiKey,
            String generateModel,
            String matchModel,
            String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    OpenAiLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                    String cleanJdModel, GenerationConfigService configService) {
        super(builder, generateModel, matchModel, cleanJdModel, configService);
    }
}
