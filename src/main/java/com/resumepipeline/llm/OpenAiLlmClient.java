package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Generic OpenAI-compatible provider. Defaults to the real OpenAI API, but the
 * base URL is freely overridable to point at OpenRouter, a local Ollama/LM Studio
 * server, or any other OpenAI-compatible {@code /chat/completions} endpoint.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmClient extends OpenAiCompatibleLlmClient {

    // Two constructors, so Spring needs to be told which one to wire — without this it
    // falls back to a no-arg constructor that does not exist.
    @Autowired
    public OpenAiLlmClient(
            @Value("${llm.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${llm.openai.api-key:}") String apiKey,
            @Value("${llm.openai.model.generate}") String generateModel,
            @Value("${llm.openai.model.match}") String matchModel,
            @Value("${llm.openai.model.clean-jd}") String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    OpenAiLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                    String cleanJdModel, GenerationConfigService configService) {
        super(builder, generateModel, matchModel, cleanJdModel, configService);
    }
}
