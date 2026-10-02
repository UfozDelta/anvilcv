package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OpenRouter provider: one key, many hosted models ({@code vendor/model} slugs), on the
 * OpenAI-compatible {@code /chat/completions} endpoint. A subclass rather than a base URL on
 * the generic openai provider because of three request fields only OpenRouter understands:
 *
 * <ul>
 *   <li>{@code reasoning.effort=none} on {@link #NO_THINKING_LABELS} — the latency-critical
 *       calls; OpenRouter maps it onto each model's own thinking switch.
 *   <li>{@code provider.data_collection=deny} — never route to a host that trains on or
 *       stores prompts, since prompts carry users' resumes and private repo context.
 *   <li>{@code usage.include=true} — OpenRouter reports the billed USD per call, which the
 *       base client records instead of guessing from a rate table.
 * </ul>
 *
 * US-only hosting is an OpenRouter account setting, not sent per request.
 *
 * Not a Spring bean: {@link RoutingLlmClient} constructs it from the admin-managed
 * settings row so the provider can be switched without a redeploy.
 */
public class OpenRouterLlmClient extends OpenAiCompatibleLlmClient {

    public OpenRouterLlmClient(
            String baseUrl,
            String apiKey,
            String generateModel,
            String matchModel,
            String cleanJdModel,
            GenerationConfigService configService) {
        this(builder(baseUrl, apiKey), generateModel, matchModel, cleanJdModel, configService);
    }

    OpenRouterLlmClient(RestClient.Builder builder, String generateModel, String matchModel,
                        String cleanJdModel, GenerationConfigService configService) {
        super(builder, generateModel, matchModel, cleanJdModel, configService);
    }

    @Override
    protected void addProviderFields(Map<String, Object> body, String label) {
        body.put("provider", Map.of("data_collection", ""));
        body.put("usage", Map.of("include", true));
        if (NO_THINKING_LABELS.contains(label)) {
            body.put("reasoning", Map.of("effort", "none"));
        }
    }

    @Override
    protected String providerName() { return "openrouter"; }
}
