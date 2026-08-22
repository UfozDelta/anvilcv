package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenCode Zen provider — an OpenAI-compatible endpoint at {@code opencode.ai/zen}
 * proxying free/low-cost models (defaults to {@code deepseek-v4-flash-free}).
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "opencode")
public class OpenCodeLlmClient extends OpenAiCompatibleLlmClient {

    // Two constructors, so Spring needs to be told which one to wire — without this it
    // falls back to a no-arg constructor that does not exist.
    @Autowired
    public OpenCodeLlmClient(
            @Value("${llm.opencode.base-url:https://opencode.ai/zen/v1}") String baseUrl,
            @Value("${llm.opencode.api-key:}") String apiKey,
            @Value("${llm.opencode.model.generate}") String generateModel,
            @Value("${llm.opencode.model.match}") String matchModel,
            @Value("${llm.opencode.model.clean-jd}") String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    OpenCodeLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                      String cleanJdModel, GenerationConfigService configService) {
        super(builder, generateModel, matchModel, cleanJdModel, configService);
    }
}
