package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.settings.LlmSettingsService;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The {@link LlmClient} everything else injects. Picks the real provider client from the
 * admin-managed {@code llm_settings} row at call time, so switching provider or rotating a
 * key takes effect on the next pipeline run instead of a redeploy.
 *
 * The delegate is cached against {@code llm_settings.updated_at}: a save bumps that column
 * and the next call rebuilds. Building a client is cheap (an SDK handle or a RestClient),
 * so the cache exists to avoid churn, not because construction is expensive.
 */
@Component
@Primary
public class RoutingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingLlmClient.class);

    private final LlmSettingsService settings;
    private final GenerationConfigService configService;

    private volatile Cached cached;

    private record Cached(Instant version, String provider, LlmClient client) {}

    public RoutingLlmClient(LlmSettingsService settings, GenerationConfigService configService) {
        this.settings = settings;
        this.configService = configService;
    }

    @Override
    public BulletGenerationResult generateBullets(GenerateBulletsRequest req, ProgressLog progress, TokenAccumulator tokens) {
        return current().generateBullets(req, progress, tokens);
    }

    @Override
    public JdCleanResult cleanJd(String rawJd, ProgressLog progress, TokenAccumulator tokens) {
        return current().cleanJd(rawJd, progress, tokens);
    }

    @Override
    public RankResult rankBullets(RankRequest req, ProgressLog progress, TokenAccumulator tokens) {
        return current().rankBullets(req, progress, tokens);
    }

    @Override
    public String coverLetter(CoverLetterRequest req, ProgressLog progress, TokenAccumulator tokens) {
        return current().coverLetter(req, progress, tokens);
    }

    /** The delegate for the settings as they stand right now. */
    // ponytail: unsynchronised read-then-write. BulletService generates categories in
    // parallel, so concurrent callers can each build a client and the last one wins.
    // Building is cheap and every copy is equivalent, so the only cost is a little
    // garbage. Add a lock if construction ever gets expensive.
    LlmClient current() {
        LlmSettingsService.Snapshot snap = settings.current();
        Cached c = cached;
        if (c != null && c.version().equals(snap.version())) return c.client();

        LlmClient built = build(snap.active(), configService);
        cached = new Cached(snap.version(), snap.active().provider(), built);
        log.info("LLM provider active: {} (generate={}, match={}, cleanJd={})",
                snap.active().provider(), snap.active().generateModel(),
                snap.active().matchModel(), snap.active().cleanJdModel());
        return built;
    }

    /** Constructs the transport for a resolved provider config. Shared with the admin test endpoint. */
    public static LlmClient build(LlmSettingsService.Resolved r, GenerationConfigService configService) {
        return switch (r.provider()) {
            case "opencode" -> new OpenCodeLlmClient(
                    r.baseUrl(), r.apiKey(), r.generateModel(), r.matchModel(), r.cleanJdModel(), configService);
            case "openai" -> new OpenAiLlmClient(
                    r.baseUrl(), r.apiKey(), r.generateModel(), r.matchModel(), r.cleanJdModel(), configService);
            default -> new GoogleLlmClient(
                    r.apiKey(), r.generateModel(), r.matchModel(), r.cleanJdModel(), configService);
        };
    }
}
