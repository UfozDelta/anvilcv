package com.resumepipeline.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for /api/register.
 * Allows 5 registration attempts per hour per IP.
 */
@Component
public class RegisterRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegisterRateLimiter.class);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String ip) {
        boolean allowed = buckets.computeIfAbsent(ip, k -> newBucket()).tryConsume(1);
        // ip is httpReq.getRemoteAddr() (AuthController) — behind a reverse proxy or the Docker
        // bridge that's the proxy's address, identical for every caller, until
        // server.forward-headers-strategy is set. Same caveat applies to what this limiter keys on.
        if (!allowed) {
            log.warn("AUTH_RATELIMIT ip={}", ip);
        }
        return allowed;
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofHours(1))
                        .build())
                .build();
    }
}
