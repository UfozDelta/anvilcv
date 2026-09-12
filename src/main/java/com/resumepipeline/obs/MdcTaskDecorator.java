package com.resumepipeline.obs;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates MDC context onto {@code applicationTaskExecutor}, the pooled executor behind
 * {@code @Async}. This is the only mechanism that can reach {@code ProjectService.enrich}
 * (dispatched through a Spring AOP proxy, so it can't be wrapped at the call site) and the
 * only fork site where the thread is reused, so the previous context must be restored in a
 * {@code finally} rather than merely cleared, or it leaks into the next task on that thread.
 * Registered as a bean in {@code config.ObservabilityConfig}, which Spring Boot's
 * {@code TaskExecutionAutoConfiguration} picks up automatically for {@code applicationTaskExecutor}.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> prev = MDC.getCopyOfContextMap();
            if (ctx != null) MDC.setContextMap(ctx); else MDC.clear();
            try {
                runnable.run();
            } finally {
                if (prev != null) MDC.setContextMap(prev); else MDC.clear();
            }
        };
    }
}
