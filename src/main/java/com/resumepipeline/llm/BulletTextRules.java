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

    /**
     * Minimum distinct shared quantity tokens (see {@link #quantityTokens}) for the
     * quantity-overlap duplicate signal below. Two bullets restating the same underlying work
     * tend to carry several identical numbers ("6,062 lines... 9 modules... 14 classes...
     * 46.44%"); one shared number is common coincidence between unrelated bullets ("3
     * services" turns up everywhere), so the floor is set at two.
     */
    public static final int QUANTITY_OVERLAP_FLOOR = 2;

    /**
     * Jaccard floor paired with {@link #QUANTITY_OVERLAP_FLOOR}. Deliberately lower than
     * {@link #NEAR_DUPLICATE_THRESHOLD}: this signal exists precisely because two bullets can
     * reword a shared claim heavily enough that word-overlap alone drops well under 0.6 — the
     * real shipped-PDF pair this was added for scores only 0.386 despite asserting the same
     * four numbers. Requiring the full 0.6 here would defeat the point. 0.3 is not doing the
     * discriminating on its own, though — it only keeps the pair in the same rough subject
     * area; {@link #QUANTITY_OVERLAP_FLOOR} is what actually proves they're the same claim.
     */
    public static final double QUANTITY_DUPLICATE_THRESHOLD = 0.3;

    /**
     * True when {@code text} is a near-duplicate of anything already in {@code existing}:
     * either the classic Jaccard word-overlap test, or (independently) sharing at least
     * {@link #QUANTITY_OVERLAP_FLOOR} distinct quantities with at least
     * {@link #QUANTITY_DUPLICATE_THRESHOLD} Jaccard overlap. The second test catches bullets
     * that restate the same numeric claims in different prose — see {@link #quantityTokens}.
     */
    public static boolean isNearDuplicate(String text, Collection<String> existing) {
        return existing.stream().anyMatch(t -> isSameClaim(t, text));
    }

    private static boolean isSameClaim(String a, String b) {
        double sim = similarity(a, b);
        if (sim >= NEAR_DUPLICATE_THRESHOLD) return true;
        if (sim < QUANTITY_DUPLICATE_THRESHOLD) return false;
        Set<String> shared = new HashSet<>(quantityTokens(a));
        shared.retainAll(quantityTokens(b));
        return shared.size() >= QUANTITY_OVERLAP_FLOOR;
    }

    /**
     * Distinct normalized quantity tokens in a bullet, extracted with the same {@link
     * #QUANTITY} pattern and thousands/leading-zero normalization {@link #fabricatedNumbers}
     * uses to compare a bullet's claims against its source — reused here to compare a
     * bullet's claims against another bullet's.
     *
     * <p>Unlike {@link #fabricatedNumbers}, every digit run counts, not just the
     * currency/unit-marked ones: "9 modules" and "14 classes" are exactly the kind of bare
     * counts two bullets about the same work would both restate verbatim, and the "is this a
     * claim worth sourcing" filter that drops bare numbers there would blind this check to
     * them. {@code QUANTITY}'s lookbehind still keeps product/version numbers glued to letters
     * ({@code K8s}, {@code EC2}, {@code ES2022}) out of the token set.
     */
    private static Set<String> quantityTokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        Matcher m = QUANTITY.matcher(stripThousands(text.replace("**", "")));
        while (m.find()) tokens.add(stripLeadingZeros(m.group(2)));
        return tokens;
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
     * bands. Measured against the real bullet corpus (n=84): mean 7.41, median 7.38,
     * p90 7.98. Technical resume vocabulary — {@code PostgreSQL}, {@code AES-256-GCM},
     * {@code anti-hallucination} — runs much longer than ordinary prose, so the earlier
     * 5.4 understated rendered width by roughly a third.
     *
     * <p>This ratio and the {@link GenerationConfig} word bands are one calibration and
     * move together: the bands exist to hit character targets that fit {@link
     * #CHARS_PER_LINE}, so changing this without re-deriving them (see
     * {@code V24__recalibrate_bands_for_measured_ratio.sql}) silently retargets every
     * bullet. At 7.4 the shipped bands give ~81-96 chars for one line and ~170-200 for
     * two — the two-line ceiling stays under 2 x CHARS_PER_LINE, so a kept bullet never
     * renders a third line.
     *
     * <p>ponytail: one global constant rather than six new config columns. If per-user
     * tuning drifts (very terse or very verbose writing), promote the character bands to
     * their own columns instead of adjusting this. Re-measure with
     * {@code CharsPerWordMeasureTest} before changing it.
     */
    static final double CHARS_PER_WORD = 7.4;

    /**
     * Rendered characters per LaTeX bullet line, measured against a real compiled PDF
     * from {@code resume.tex}: 11pt body text in the template's widened text block
     * ({@code \addtolength{\textwidth}{1.2in}} — see that file). A bullet longer than
     * this wraps to a second line.
     *
     * <p>This is a physical measurement, not a tuning knob: if the template's text
     * width or font size ever changes, this must be re-measured against a freshly
     * compiled PDF, and the {@link GenerationConfig} word bands re-derived from it.
     */
    public static final int CHARS_PER_LINE = 105;

    /**
     * Estimated rendered line count for a bullet, using the same bold-stripped
     * character count the length filter uses. Ceiling division: any partial line
     * still costs a full line of vertical space on the page.
     */
    public static int estimatedLines(String text) {
        return Math.max(1, (int) Math.ceil(charCount(text) / (double) CHARS_PER_LINE));
    }

    /**
     * Upper bound on a kept bullet, in characters. The configured two-line band is the
     * intent, but a bullet may never render a third line, so this hard-clamps to what two
     * rendered lines physically hold. Without the clamp an admin raising the word band --
     * or a future ratio change -- would silently start admitting three-line bullets, which
     * blow the one-page budget a resume is built around.
     */
    static int twoLineCeilingChars(GenerationConfig cfg) {
        return Math.min(chars(cfg.getDoubleLineHigh()), 2 * CHARS_PER_LINE);
    }

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
     *
     * <p>Only PAIRED markers come off, mirroring {@code ApplicationRenderer.escapeRich},
     * which rewrites the same span pattern to bold. A lone {@code **} is
     * not bold syntax: it survives into the PDF as two visible asterisks, so counting it
     * out would measure the bullet short of what it renders.
     */
    public static int charCount(String s) {
        if (s == null || s.isBlank()) return 0;
        return BOLD_SPAN.matcher(s).replaceAll("$1").trim().length();
    }

    /**
     * Maximum {@code **bold**} spans a bullet is allowed to keep — see {@link #capBoldSpans}.
     * A bullet bolding 8-12 spans (one shipped example did both) reads as no emphasis at all;
     * capping at 2 restores the point of bolding in the first place.
     */
    public static final int MAX_BOLD_SPANS = 2;

    /**
     * Bold ceiling for {@code boldDensity=HEAVY}. HEAVY is the user asking for more emphasis,
     * so it gets more — but still a ceiling, because the failure mode that motivated the cap
     * was a bullet where eight bolded spans left nothing actually emphasised. Four is the most
     * a single-line bullet can carry and still read as having highlights rather than being one.
     */
    public static final int MAX_BOLD_SPANS_HEAVY = 4;

    /** The bold ceiling for a config's density: NONE and LIGHT keep the default, HEAVY gets more. */
    public static int maxBoldSpans(GenerationConfig cfg) {
        return cfg != null && cfg.getBoldDensity() == GenerationConfig.BoldDensity.HEAVY
                ? MAX_BOLD_SPANS_HEAVY
                : MAX_BOLD_SPANS;
    }

    private static final Pattern BOLD_SPAN = Pattern.compile("\\*\\*(.+?)\\*\\*");

    /**
     * Keeps at most {@link #MAX_BOLD_SPANS} {@code **bold**} spans in a bullet, unwrapping
     * the rest (words kept, markers dropped) rather than deleting them.
     *
     * <p>Survivors are chosen by priority, not just position: a span containing a digit
     * (a quantified claim — "4 venues", "0ms", "46.44%") wins over one that doesn't, since
     * that is what a recruiter's eye should land on. Ties within the same priority keep the
     * earliest span. This is a single pass over the matched spans, reusing {@link #DIGITS}
     * to test each span's own content rather than a second bold-stripping regex.
     *
     * <p>Unpaired {@code **} never matches {@link #BOLD_SPAN} (it requires a closing pair),
     * so malformed markup just passes through untouched rather than corrupting the text.
     *
     * <p>Does not change {@link #charCount}: that method strips every {@code **} occurrence
     * unconditionally, so unwrapping a losing span (which removes its two markers) leaves the
     * bold-stripped character count identical. Callers relying on charCount for the length
     * filter can call this before or after without changing a keep/drop decision.
     */
    public static String capBoldSpans(String text) {
        return capBoldSpans(text, MAX_BOLD_SPANS);
    }

    /**
     * As {@link #capBoldSpans(String)}, but with an explicit ceiling so a HEAVY-density config
     * keeps more emphasis than the default. See {@link #maxBoldSpans(GenerationConfig)}.
     */
    public static String capBoldSpans(String text, int maxSpans) {
        if (text == null || text.isBlank()) return text;
        Matcher m = BOLD_SPAN.matcher(text);
        List<int[]> spans = new ArrayList<>();      // [start, end) of each "**...**" match
        List<String> inner = new ArrayList<>();     // content between the markers
        List<Boolean> hasDigit = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            inner.add(m.group(1));
            hasDigit.add(DIGITS.matcher(m.group(1)).find());
        }
        if (spans.size() <= maxSpans) return text;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) order.add(i);
        order.sort((a, b) -> {
            if (!hasDigit.get(a).equals(hasDigit.get(b))) return hasDigit.get(a) ? -1 : 1;
            return Integer.compare(spans.get(a)[0], spans.get(b)[0]);
        });
        Set<Integer> keep = new HashSet<>(order.subList(0, maxSpans));

        StringBuilder sb = new StringBuilder();
        int last = 0;
        for (int i = 0; i < spans.size(); i++) {
            int[] span = spans.get(i);
            sb.append(text, last, span[0]);
            if (keep.contains(i)) {
                sb.append("**").append(inner.get(i)).append("**");
            } else {
                sb.append(inner.get(i));
            }
            last = span[1];
        }
        sb.append(text, last, text.length());
        return sb.toString();
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
        if (charCount > twoLineCeilingChars(cfg)) {
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
    public static int doubleHighChars(GenerationConfig cfg)   { return twoLineCeilingChars(cfg); }
    public static int minFloorChars(GenerationConfig cfg)     { return chars(cfg.getMinWordFloor()); }
}
