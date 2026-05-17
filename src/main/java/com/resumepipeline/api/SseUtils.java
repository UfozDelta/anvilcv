package com.resumepipeline.api;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared SSE helpers.
 *
 * Keepalive: Render and most reverse proxies drop idle HTTP connections after ~30s.
 * During long LLM calls no events emit, so we schedule a comment ping every 20s.
 * SSE comment lines (": ping") are invisible to the browser but keep the TCP connection alive.
 */
public final class SseUtils {

    // Pool of 2 — one slow client write can't block all other keepalive pings.
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-keepalive");
                t.setDaemon(true);
                return t;
            });

    private SseUtils() {}

    /**
     * Starts a ping every 20s on the given emitter. Call cancel() on the returned future
     * when the SSE stream completes (success or error) to stop the scheduler.
     */
    public static ScheduledFuture<?> startKeepalive(SseEmitter emitter) {
        return SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                // SSE comment — browser ignores it, proxy sees bytes and resets idle timer.
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ignored) {
                // Emitter already closed — scheduler will be cancelled by caller.
            }
        }, 20, 20, TimeUnit.SECONDS);
    }
}
