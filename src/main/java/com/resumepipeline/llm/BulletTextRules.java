package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure text rules for generated bullets: word counting, terminal-period
 * normalisation, the word-count keep/drop filter, forbidden-opener and
 * fabricated-metric checks, and bullet-similarity scoring for dedup.
 * Extracted from {@link GoogleLlmClient} so the logic can be unit-tested
 * without a live LLM.
 *
 * No state, no Spring — every method is static and deterministic.
 */
public final class BulletTextRules {

    private BulletTextRules() {}

    // Same list called out as forbidden openers in the generation prompt — enforced
    // here in code too, since the LLM doesn't always follow prompt instructions.
    private static final String[] FORBIDDEN_OPENERS = {
            "worked on", "helped with", "was responsible for", "assisted",
            "contributed to", "collaborated on"
    };

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** True if the bullet opens with one of the weak/passive phrases the prompt forbids. */
    public static boolean hasForbiddenOpener(String text) {
        if (text == null) return false;
        String t = text.strip().toLowerCase();
        for (String opener : FORBIDDEN_OPENERS) {
            if (t.startsWith(opener)) return true;
        }
        return false;
    }

    /**
     * Bolded tokens containing a number that doesn't appear anywhere in the source
     * context (project/role description + repo context) the bullet was generated
     * from. The LLM is instructed to quote metrics verbatim — this catches it when
     * it doesn't. Returns the fabricated bold tokens (empty list if none / no numbers).
     */
    public static List<String> fabricatedNumbers(String text, String sourceContext) {
        if (text == null || text.isBlank()) return List.of();
        String src = sourceContext == null ? "" : sourceContext;
        Set<String> srcNumbers = new HashSet<>();
        Matcher srcMatcher = DIGITS.matcher(src);
        while (srcMatcher.find()) srcNumbers.add(srcMatcher.group());

        List<String> fabricated = new ArrayList<>();
        Matcher boldMatcher = BOLD.matcher(text);
        while (boldMatcher.find()) {
            String token = boldMatcher.group(1);
            Matcher numMatcher = DIGITS.matcher(token);
            while (numMatcher.find()) {
                String digits = numMatcher.group();
                boolean found = srcNumbers.stream().anyMatch(n -> n.contains(digits) || digits.contains(n));
                if (!found) {
                    fabricated.add(token);
                    break;
                }
            }
        }
        return fabricated;
    }

    /**
     * Jaccard similarity of two bullets' word sets (bold markup stripped, lowercased,
     * punctuation dropped). 0 = disjoint, 1 = identical bag of words. Used to catch
     * near-duplicate bullets across generations.
     */
    public static double similarity(String a, String b) {
        Set<String> wa = wordSet(a);
        Set<String> wb = wordSet(b);
        if (wa.isEmpty() || wb.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(wa);
        intersection.retainAll(wb);
        Set<String> union = new HashSet<>(wa);
        union.addAll(wb);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> wordSet(String s) {
        if (s == null) return Set.of();
        String cleaned = s.toLowerCase().replace("**", "").replaceAll("[^a-z0-9\\s]", " ");
        return Arrays.stream(cleaned.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toSet());
    }

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
