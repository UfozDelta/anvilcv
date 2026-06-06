package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;

/**
 * Pure text rules for generated bullets: word counting, terminal-period
 * normalisation, and the word-count keep/drop filter. Extracted from
 * {@link GoogleLlmClient} so the logic can be unit-tested without a live LLM.
 *
 * No state, no Spring — every method is static and deterministic.
 */
public final class BulletTextRules {

    private BulletTextRules() {}

    /** Why a bullet was kept or dropped by the word-count filter. */
    public enum Decision { KEPT, DEAD_ZONE, TOO_SHORT }

    /**
     * Word count after stripping markdown bolds, so {@code **64K**} counts as one
     * word rather than three tokens. Null/blank counts as 0.
     */
    public static int wordCount(String s) {
        if (s == null || s.isBlank()) return 0;
        String stripped = s.replace("**", "");
        return stripped.trim().split("\\s+").length;
    }

    /**
     * Trim and ensure a terminal period. Bullets without sentence-ending
     * punctuation look unfinished on a resume. Null becomes "".
     */
    public static String ensureTerminalPeriod(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return t;
        char last = t.charAt(t.length() - 1);
        if (last == '.' || last == '!' || last == '?') return t;
        return t + ".";
    }

    /**
     * Decide whether a bullet of the given word count survives the filter.
     * When the filter is disabled in config, everything is {@link Decision#KEPT}.
     * Otherwise a bullet in the dead zone is dropped, then one below the floor.
     */
    public static Decision decide(int wordCount, GenerationConfig cfg) {
        if (!cfg.isWordFilterEnabled()) return Decision.KEPT;
        if (wordCount >= cfg.getDeadZoneLow() && wordCount <= cfg.getDeadZoneHigh()) {
            return Decision.DEAD_ZONE;
        }
        if (wordCount < cfg.getMinWordFloor()) {
            return Decision.TOO_SHORT;
        }
        return Decision.KEPT;
    }
}
