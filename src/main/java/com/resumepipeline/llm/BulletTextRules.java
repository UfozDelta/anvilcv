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
    // A number plus an optional magnitude suffix, so "64K" in a bullet can be
    // matched against a source that spells the same quantity "64,000".
    private static final Pattern SCALED = Pattern.compile("(\\d+)\\s*([kKmMbB])?");

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
     *
     * <p>Matching is on whole digit runs, not substrings: a source containing "5" must
     * not vouch for a bullet claiming "500ms", and a source containing "2024" must not
     * vouch for "24". Thousands separators are stripped from both sides first, and a
     * bullet's "64K"/"3M" suffix is expanded, so "64K" still matches a source "64,000".
     */
    public static List<String> fabricatedNumbers(String text, String sourceContext) {
        if (text == null || text.isBlank()) return List.of();
        // Strip thousands separators so "64,000" reads as one run rather than "64" + "000".
        String src = stripThousands(sourceContext == null ? "" : sourceContext);
        Set<String> srcNumbers = new HashSet<>();
        Matcher srcMatcher = DIGITS.matcher(src);
        while (srcMatcher.find()) srcNumbers.add(stripLeadingZeros(srcMatcher.group()));

        List<String> fabricated = new ArrayList<>();
        Matcher boldMatcher = BOLD.matcher(text);
        while (boldMatcher.find()) {
            String token = boldMatcher.group(1);
            Matcher numMatcher = SCALED.matcher(stripThousands(token));
            while (numMatcher.find()) {
                String digits = stripLeadingZeros(numMatcher.group(1));
                String suffix = numMatcher.group(2);
                // The bare digits count as quoted, and so does their scaled expansion —
                // the source may spell the same quantity either way.
                // ponytail: the suffix match is naive, so "300ms" reads as "300M". That can
                // only let a bullet through, never drop a good one (bare digits are checked
                // first), so a real unit parser is not worth it here.
                boolean found = srcNumbers.contains(digits)
                        || (suffix != null && srcNumbers.contains(scale(digits, suffix)));
                if (!found) {
                    fabricated.add(token);
                    break;
                }
            }
        }
        return fabricated;
    }

    /** Drop thousands separators so "64,000" is one digit run rather than two. */
    private static String stripThousands(String s) {
        return s.replaceAll("(?<=\\d),(?=\\d{3})", "");
    }

    /** Multiply a digit string by its magnitude suffix, e.g. ("64","K") -> "64000". */
    private static String scale(String digits, String suffix) {
        int zeros = switch (Character.toLowerCase(suffix.charAt(0))) {
            case 'k' -> 3;
            case 'm' -> 6;
            case 'b' -> 9;
            default  -> 0;
        };
        return digits + "0".repeat(zeros);
    }

    /** "007" and "7" are the same quantity; normalise so they compare equal. */
    private static String stripLeadingZeros(String digits) {
        String t = digits.replaceFirst("^0+(?=\\d)", "");
        return t.isEmpty() ? "0" : t;
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
