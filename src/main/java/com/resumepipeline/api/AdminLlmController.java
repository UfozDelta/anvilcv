package com.resumepipeline.api;

import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.RoutingLlmClient;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.llm.settings.LlmSettings;
import com.resumepipeline.llm.settings.LlmSettingsService;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only LLM provider settings. The whole {@code /api/admin/**} tree is gated on
 * {@code ROLE_ADMIN} by SecurityConfig; nothing here re-checks the role.
 *
 * API keys leave this controller masked, never in the clear, in either direction:
 * GET returns only the last four characters, and a blank key on PUT means "keep the
 * stored one" so the UI never has to round-trip a secret it was not given.
 */
@RestController
@RequestMapping("/api/admin/llm")
public class AdminLlmController {

    private static final Logger log = LoggerFactory.getLogger(AdminLlmController.class);

    private final LlmSettingsService settings;
    private final GenerationConfigService configService;

    public AdminLlmController(LlmSettingsService settings, GenerationConfigService configService) {
        this.settings = settings;
        this.configService = configService;
    }

    @GetMapping
    public LlmSettingsService.View get() {
        return settings.view();
    }

    @PutMapping
    public LlmSettingsService.View update(@RequestBody LlmSettingsService.UpdateRequest req, Authentication auth) {
        return settings.update(req, auth.getName());
    }

    /**
     * One cheap live call against the settings as currently stored, so a bad key or a
     * retired model id surfaces here instead of halfway through a user's pipeline run.
     * Doubles as the {@code response_format: json_object} smoke test for OpenAI-compatible
     * providers, whose JSON support is a convention rather than a schema guarantee.
     */
    @PostMapping("/test")
    public Map<String, Object> test(@RequestParam(required = false) String provider, Authentication auth) {
        LlmSettings row = settings.row();
        String target = (provider == null || provider.isBlank()) ? settings.activeProvider(row) : provider;
        LlmSettingsService.Resolved resolved = settings.resolve(target, row);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", resolved.provider());
        result.put("model", resolved.cleanJdModel());

        long started = System.currentTimeMillis();
        try {
            LlmClient client = RoutingLlmClient.build(resolved, configService);
            TokenAccumulator tokens = new TokenAccumulator();
            // cleanJd is the cheapest of the four calls and exercises the full path:
            // request, JSON-mode response, parse, token accounting.
            LlmClient.JdCleanResult clean =
                    client.cleanJd("Software Engineer at Acme. Build APIs in Java.", ProgressLog.noOp(), tokens);

            result.put("ok", true);
            result.put("company", clean.company());
            result.put("role", clean.role());
            result.put("promptTokens", tokens.getPromptTokens());
            result.put("candidatesTokens", tokens.getCandidatesTokens());
            result.put("costUsd", tokens.getCostUsd());
        } catch (RuntimeException e) {
            log.warn("LLM provider test failed for {} (requested by {}): {}", target, auth.getName(), e.getMessage());
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        result.put("elapsedMs", System.currentTimeMillis() - started);
        return result;
    }
}
