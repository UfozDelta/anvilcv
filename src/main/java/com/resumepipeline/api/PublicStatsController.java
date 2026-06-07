package com.resumepipeline.api;

import com.resumepipeline.application.ApplicationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unauthenticated, privacy-safe aggregate stats for the public landing page.
 * Exposes only global averages — never per-user data.
 */
@RestController
@RequestMapping("/api/public")
public class PublicStatsController {

    /** Hide sample size until at least this many timed runs exist. */
    private static final long SAMPLE_SIZE_FLOOR = 25;

    private final ApplicationRepository repo;

    public PublicStatsController(ApplicationRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Double avgMs = repo.avgPipelineDurationMs();
        long sampleSize = repo.countByPipelineDurationMsNotNull();

        Map<String, Object> result = new LinkedHashMap<>();
        if (avgMs == null) {
            result.put("avgPipelineDurationMs", null);
            result.put("avgPipelineDurationSec", null);
        } else {
            BigDecimal sec = BigDecimal.valueOf(avgMs / 1000.0)
                    .setScale(1, RoundingMode.HALF_UP);
            result.put("avgPipelineDurationMs", Math.round(avgMs));
            result.put("avgPipelineDurationSec", sec);
        }
        // Only surface sample size once it's large enough to be credible.
        result.put("sampleSize", sampleSize >= SAMPLE_SIZE_FLOOR ? sampleSize : null);
        return result;
    }
}
