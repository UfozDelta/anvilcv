package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import org.springframework.web.client.RestClient;

/**
 * OpenCode Zen provider — an OpenAI-compatible endpoint at {@code opencode.ai/zen}
 * proxying free/low-cost models (defaults to {@code deepseek-v4-flash-free}).
 *
 * Not a Spring bean: {@link RoutingLlmClient} constructs it from the admin-managed
 * settings row so the provider can be switched without a redeploy.
 */
public class OpenCodeLlmClient extends OpenAiCompatibleLlmClient {

    public OpenCodeLlmClient(
            String baseUrl,
            String apiKey,
            String generateModel,
            String matchModel,
            String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    OpenCodeLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                      String cleanJdModel, GenerationConfigService configService) {
        super(builder, generateModel, matchModel, cleanJdModel, configService);
    }
}
