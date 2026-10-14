package com.resumepipeline.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP brake on password guessing: 10 failed logins per 15 minutes. Only failures consume
 * a token, so normal use never hits it. Same proxy-address caveat as {@link RegisterRateLimiter}.
 */
@Component
public class LoginRateLimiter {

    /** Bound on tracked IPs; past it the map is reset rather than grown without limit. */
    private static final int MAX_TRACKED = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        Bucket b = buckets.get(ip);
        return b != null && b.getAvailableTokens() <= 0;
    }

    public void recordFailure(String ip) {
        if (buckets.size() >= MAX_TRACKED) buckets.clear();
        buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(15)).build())
                .build()).tryConsume(1);
    }

    public void recordSuccess(String ip) {
        buckets.remove(ip);
    }
}
