package com.resumepipeline.obs;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries the calling thread's MDC context across a fork so log lines emitted on the far
 * side still show the request/job correlation bracket. Covers the eleven explicit-executor
 * fork sites (virtual-thread {@code submit}, {@code CompletableFuture.supplyAsync}) that
 * {@link MdcTaskDecorator} cannot see, since that decorator only runs for {@code @Async}
 * dispatch through {@code applicationTaskExecutor}.
 */
public final class Mdc {

    private Mdc() {}

    public static Runnable wrap(Runnable body) {
        Supplier<Void> wrapped = wrap(() -> {
            body.run();
            return null;
        });
        return wrapped::get;
    }

    public static <T> Supplier<T> wrap(Supplier<T> body) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> prev = MDC.getCopyOfContextMap();
            if (ctx != null) MDC.setContextMap(ctx); else MDC.clear();
            try {
                return body.get();
            } finally {
                // prev is null at every one of these fork sites today (a fresh virtual thread
                // has no prior context), so this restore is dead code here. It stays because
                // the same logic underpins MdcTaskDecorator, where the thread is pooled and a
                // stale context really would otherwise leak into the next task.
                if (prev != null) MDC.setContextMap(prev); else MDC.clear();
            }
        };
    }
}
