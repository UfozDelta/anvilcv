package com.resumepipeline.progress;

/**
 * Sink for real-time progress events. Services call emit() at each meaningful
 * decision point; the SSE controller wires this to an SseEmitter so the browser
 * sees events as they happen instead of waiting for the full response.
 *
 * The no-op() factory lets existing blocking callers pass a sink that discards
 * events, so nothing breaks and no method signatures change at call sites that
 * don't want streaming.
 */
@FunctionalInterface
public interface ProgressLog {

    void emit(String message);

    /** Returns a ProgressLog that silently drops every event. */
    static ProgressLog noOp() {
        return message -> {};
    }
}
