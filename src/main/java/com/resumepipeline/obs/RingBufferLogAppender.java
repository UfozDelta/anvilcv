package com.resumepipeline.obs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * In-process ring buffer, attached to root alongside the console appender, backing the admin
 * log viewer ({@code /api/admin/logs}). Not the retention story — memory-only, resets on
 * restart — just a live/recent-window read path that works regardless of deploy shape: this
 * app has no Docker socket and no guaranteed compose/host log location to tail from inside
 * the JVM, so a file or DB sink would be the wrong cost for what this is (see logging-plan.md
 * Layer 6).
 */
public class RingBufferLogAppender extends AppenderBase<ILoggingEvent> {

    private record LogLine(String text, int levelInt) {}

    private final int capacity;
    private final ArrayDeque<LogLine> lines;
    private final Object lock = new Object();
    private PatternLayout layout;

    public RingBufferLogAppender(int capacity) {
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
    }

    @Override
    public void start() {
        layout = new PatternLayout();
        layout.setContext(getContext());
        // No %clr — this feeds a JSON API response, not a terminal, and raw ANSI escapes in
        // the response body would be exactly the kind of thing an admin has to squint past.
        // Carries the [req|user] correlation bracket from logging.pattern.correlation.
        layout.setPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p [%15.15t] [%X{req:-none}|%X{user:-anon}] %-40.40logger{39} : %m%n%wEx");
        layout.start();
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String rendered = layout.doLayout(event);
        LogLine line = new LogLine(rendered, event.getLevel().toInt());
        synchronized (lock) {
            if (lines.size() >= capacity) lines.removeFirst();
            lines.addLast(line);
        }
    }

    /**
     * Newest last. {@code contains} is filtered against already-rendered, already-escaped log
     * text — the buffer holds strings, not raw fields, so there's no second injection surface
     * here beyond not reflecting the filter itself back unescaped.
     */
    public List<String> snapshot(String contains, String minLevel) {
        int minInt = (minLevel == null || minLevel.isBlank())
                ? Level.ALL_INT
                : Level.toLevel(minLevel, Level.ALL).toInt();
        synchronized (lock) {
            List<String> out = new ArrayList<>(lines.size());
            for (LogLine line : lines) {
                if (line.levelInt() < minInt) continue;
                if (contains != null && !contains.isBlank() && !line.text().contains(contains)) continue;
                out.add(line.text());
            }
            return out;
        }
    }
}
