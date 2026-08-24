package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure text rules for generated bullets: length measurement, terminal-period
 * normalisation, the character-count keep/drop filter, forbidden-opener and
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

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    // What turns a digit run into a claimed quantity rather than a version or product
    // number: a currency/percent/plus marker, a time unit, a multiplier, or a magnitude
    // suffix. "s"/"ms"/"x" and the magnitudes need a word boundary, so "64K" and "3 x"
    // read as quantities while "S3 buckets", "2dsphere" and "Java 17 stack" do not.
    private static final String UNIT = "(?:%|\\+|(?:ms|s|[xXkKmMbB])\\b)";
    // $-prefixed or unit-bearing digit run. The unit may be glued on (group 3) or one
    // space away (group 4, matched inside a lookahead so it stays out of the reported
    // token). Everything else is skipped — see fabricatedNumbers.
    //
    // The leading lookbehind is load-bearing: a digit welded to a preceding letter belongs
    // to a product name, never to a claim. Without it "K8s" parses as "8" + the "s" time
    // unit, and a clean bullet mentioning Kubernetes is thrown out as a fabricated metric.
    // Same for "P95", "EC2", "ES2022". "$200K" is unaffected — the lookbehind sits before
    // the "$", and the character preceding that is whitespace.
    private static final Pattern QUANTITY =
            Pattern.compile("(?<![A-Za-z])(\\$)?(\\d+)(?:(" + UNIT + ")|(?=\\s(" + UNIT + ")))?");

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
     * Quantities claimed anywhere in the bullet that don't appear in the source context
     * (project/role description + repo context) the bullet was generated from. The LLM is
     * instructed to quote metrics verbatim — this catches it when it doesn't. Returns the
     * offending quantity substrings, e.g. "180", "200ms", "95%" (empty list if none).
     *
     * <p>The whole bullet is scanned, not just its {@code **bold**} spans: bolding is
     * style-configurable, so a bold-only gate reported nothing at all under
     * {@code boldDensity=NONE} and missed any un-bolded invented metric otherwise.
     *
     * <p>Only unit-bearing quantities are checked — a digit run preceded by {@code $} or
     * followed (at most one space away) by {@code %}, {@code +}, {@code x}, {@code ms},
     * {@code s} or a {@code k}/{@code m}/{@code b} magnitude. A bare or letter-glued
     * number is a version or product identifier, not a claim: {@code S3}, {@code EC2},
     * {@code P95}, {@code AES-256-GCM}, {@code Java 17}, {@code HTTP/2}, {@code 24/7},
     * {@code 3-tier}. Scanning every digit would reject those wholesale.
     *
     * <p>Matching is on whole digit runs, not substrings: a source containing "5" must
     * not vouch for a bullet claiming "500ms", and a source containing "2024" must not
     * vouch for "24K". Thousands separators are stripped from both sides first, and a
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
        // Bold markers are dropped so "**64K**" and "**3 to 180** ms" read as plain text.
        Matcher m = QUANTITY.matcher(stripThousands(text.replace("**", "")));
        while (m.find()) {
            String unit = m.group(3) != null ? m.group(3) : m.group(4);
            if (m.group(1) == null && unit == null) continue;   // bare number, not a claim
            String digits = stripLeadingZeros(m.group(2));
            // The bare digits count as quoted, and so does their scaled expansion —
            // the source may spell the same quantity either way.
            boolean magnitude = unit != null && unit.length() == 1
                    && "kKmMbB".indexOf(unit.charAt(0)) >= 0;
            boolean found = srcNumbers.contains(digits)
                    || (magnitude && srcNumbers.contains(scale(digits, unit)));
            if (!found) fabricated.add(m.group());
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
     * Jaccard word-overlap at or above which two bullets are treated as the same bullet.
     * Shared so the generation recovery pass and the persist-time dedup agree on what a
     * duplicate is.
     */
    public static final double NEAR_DUPLICATE_THRESHOLD = 0.6;

    /** True when {@code text} is a near-duplicate of anything already in {@code existing}. */
    public static boolean isNearDuplicate(String text, Collection<String> existing) {
        return existing.stream().anyMatch(t -> similarity(t, text) >= NEAR_DUPLICATE_THRESHOLD);
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

    /** Why a bullet was kept or dropped by the length filter. */
    public enum Decision { KEPT, DEAD_ZONE, TOO_SHORT, TOO_LONG }

    /**
     * Ratio used to read the word-based {@link GenerationConfig} bands as character
     * bands. The configured defaults are already self-consistent at this ratio — 22-26
     * words is the "≈130 chars" single line the prompt asks for, 42-50 the "≈250 chars"
     * double line — so the conversion changes no existing tuning.
     *
     * <p>ponytail: one global constant rather than six new config columns. If per-user
     * tuning drifts (very terse or very verbose writing), promote the character bands to
     * their own columns instead of adjusting this.
     */
    static final double CHARS_PER_WORD = 5.4;

    /** The configured word band expressed in characters. */
    private static int chars(int words) {
        return (int) Math.round(words * CHARS_PER_WORD);
    }

    /**
     * Word count after stripping markdown bolds, so {@code **64K**} counts as one
     * word rather than three tokens. Null/blank counts as 0.
     *
     * <p>Retained for progress/diagnostic output. The length filter itself measures
     * characters — see {@link #charCount}.
     */
    public static int wordCount(String s) {
        if (s == null || s.isBlank()) return 0;
        String stripped = s.replace("**", "");
        return stripped.trim().split("\\s+").length;
    }

    /**
     * Rendered length in characters, with the {@code **} bold markers removed — they
     * compile to {@code \textbf{}} and take no width on the page.
     *
     * <p>This, not {@link #wordCount}, is what decides line fill: a word count treats
     * {@code **Kubernetes**} and {@code **a**} as one apiece, while the LaTeX line they
     * have to fill does not.
     */
    public static int charCount(String s) {
        if (s == null || s.isBlank()) return 0;
        return s.replace("**", "").trim().length();
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
     * Decide whether a bullet of the given character count survives the filter.
     * When the filter is disabled in config, everything is {@link Decision#KEPT}.
     * Otherwise a bullet in the dead zone is dropped, then one past the two-line
     * ceiling, then one below the floor.
     *
     * <p>The ceiling is what stops a bullet from silently spilling onto a third
     * line: the dead zone only guards the gap between a full first line and a full
     * second one, so without an upper bound anything past the two-line band — 300
     * chars, 400, no limit — counted as {@link Decision#KEPT} and shipped to the PDF.
     *
     * <p>The config bands are stored in words and converted here — see
     * {@link #CHARS_PER_WORD}.
     */
    public static Decision decide(int charCount, GenerationConfig cfg) {
        if (!cfg.isWordFilterEnabled()) return Decision.KEPT;
        if (charCount >= chars(cfg.getDeadZoneLow()) && charCount <= chars(cfg.getDeadZoneHigh())) {
            return Decision.DEAD_ZONE;
        }
        if (charCount > chars(cfg.getDoubleLineHigh())) {
            return Decision.TOO_LONG;
        }
        if (charCount < chars(cfg.getMinWordFloor())) {
            return Decision.TOO_SHORT;
        }
        return Decision.KEPT;
    }

    /** The configured band boundaries in characters, for prompts and progress messages. */
    public static int deadZoneLowChars(GenerationConfig cfg)  { return chars(cfg.getDeadZoneLow()); }
    public static int deadZoneHighChars(GenerationConfig cfg) { return chars(cfg.getDeadZoneHigh()); }
    public static int singleLowChars(GenerationConfig cfg)    { return chars(cfg.getSingleLineLow()); }
    public static int singleHighChars(GenerationConfig cfg)   { return chars(cfg.getSingleLineHigh()); }
    public static int doubleLowChars(GenerationConfig cfg)    { return chars(cfg.getDoubleLineLow()); }
    public static int doubleHighChars(GenerationConfig cfg)   { return chars(cfg.getDoubleLineHigh()); }
    public static int minFloorChars(GenerationConfig cfg)     { return chars(cfg.getMinWordFloor()); }
}
