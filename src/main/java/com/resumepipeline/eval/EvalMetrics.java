package com.resumepipeline.eval;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.llm.BulletTextRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic quality metrics for a bullet set. Pure: no Spring, no DB, no LLM. Every rule
 * that already exists for generation is reused from {@link BulletTextRules}; only the vanity
 * and sentence checks are new, because nothing in the pipeline measured them before.
 *
 * <p>All headline numbers are rates, never raw counts: a fresh one-shot run and a bank
 * accumulated over weeks differ in size for reasons that say nothing about prompt quality.
 */
public final class EvalMetrics {

    private EvalMetrics() {}

    /**
     * Counts of work volume that impress nobody: commits, lines of code, files, classes...
     * One optional word between number and noun, so "14 Java classes" and "6,062 lines" match.
     */
    static final Pattern VANITY = Pattern.compile(
            "\\b\\d[\\d,.]*\\+?\\s*(?:[A-Za-z-]+\\s+)?"
                    + "(?:commits?|lines(?:\\s+of\\s+code)?|LOC|files|classes|modules|methods|functions|PRs|pull\\s+requests)\\b",
            Pattern.CASE_INSENSITIVE);

    // A sentence break: terminal punctuation, whitespace, then a capital or a bold marker.
    private static final Pattern SENTENCE_BREAK = Pattern.compile("[.!?]\\s+(?=\\*{0,2}[A-Z])");

    /** Per-bullet findings, shown next to the bullet in the side-by-side view. */
    public record Flags(boolean identical, boolean overlap, boolean vanity, boolean multiSentence,
                        String length, boolean weakOpener, List<String> unbacked) {}

    public record Summary(int count, int projects, double perProject,
                          double identicalRate, double overlapRate, double vanityRate,
                          double multiSentenceRate, double lengthFitRate, double meanChars,
                          double weakOpenerRate, double openerDiversity, List<String> topOpeners,
                          double unbackedRate, Map<String, Integer> categories) {}

    /**
     * @param sourceByProject what each project's bullets may quote numbers from; a project
     *                        missing here is left out of the unbacked rate rather than counted clean
     * @param cfg             length bands to judge fit against (the bank owner's)
     */
    public static Summary summarize(List<EvalItem> items, Map<UUID, String> sourceByProject, GenerationConfig cfg) {
        List<Flags> flags = flag(items, sourceByProject, cfg);
        int n = items.size();
        int identical = 0, overlap = 0, vanity = 0, multi = 0, fit = 0, weak = 0, unbacked = 0, sourced = 0;
        long chars = 0;
        for (int i = 0; i < n; i++) {
            Flags f = flags.get(i);
            if (f.identical()) identical++;
            if (f.overlap()) overlap++;
            if (f.vanity()) vanity++;
            if (f.multiSentence()) multi++;
            if (f.length() == null) fit++;
            if (f.weakOpener()) weak++;
            if (sourceByProject.containsKey(items.get(i).projectId())) {
                sourced++;
                if (!f.unbacked().isEmpty()) unbacked++;
            }
            chars += BulletTextRules.charCount(items.get(i).text());
        }
        Map<String, Long> openers = items.stream()
                .map(it -> opener(it.text()))
                .filter(o -> !o.isEmpty())
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()));
        List<String> top = openers.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(e -> e.getKey() + " x" + e.getValue())
                .toList();
        Map<String, Integer> categories = new LinkedHashMap<>();
        items.stream().map(EvalItem::category).sorted()
                .forEach(c -> categories.merge(c, 1, Integer::sum));
        long projects = items.stream().map(EvalItem::projectId).distinct().count();
        return new Summary(n, (int) projects, projects == 0 ? 0 : (double) n / projects,
                rate(identical, n), rate(overlap, n), rate(vanity, n), rate(multi, n), rate(fit, n),
                n == 0 ? 0 : (double) chars / n, rate(weak, n), rate(openers.size(), n), top,
                rate(unbacked, sourced), categories);
    }

    /** Flags for each item, in input order. Duplicates are judged within the item's own project. */
    public static List<Flags> flag(List<EvalItem> items, Map<UUID, String> sourceByProject, GenerationConfig cfg) {
        Map<UUID, List<String>> textsByProject = items.stream().collect(Collectors.groupingBy(
                EvalItem::projectId, Collectors.mapping(EvalItem::text, Collectors.toList())));
        List<Flags> out = new ArrayList<>(items.size());
        for (EvalItem it : items) {
            List<String> others = new ArrayList<>(textsByProject.get(it.projectId()));
            others.remove(it.text());   // one copy: an exact twin still counts against it
            // Near-identical prose is a real repeat. Overlap at the normal dedup floor is reported
            // separately: a second framing of the same work is intended (cross-lens variants).
            boolean identical = BulletTextRules.isNearDuplicate(it.text(), others, BulletTextRules.CROSS_LENS_THRESHOLD);
            boolean overlap = BulletTextRules.isNearDuplicate(it.text(), others);
            BulletTextRules.Decision d = BulletTextRules.decide(BulletTextRules.charCount(it.text()), cfg);
            String source = sourceByProject.get(it.projectId());
            out.add(new Flags(identical, overlap,
                    VANITY.matcher(it.text().replace("**", "")).find(),
                    sentences(it.text()) >= 2,
                    d == BulletTextRules.Decision.KEPT ? null : d.name(),
                    BulletTextRules.hasForbiddenOpener(it.text()),
                    source == null ? List.of() : BulletTextRules.fabricatedNumbers(it.text(), source)));
        }
        return out;
    }

    static int sentences(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return 0;
        return SENTENCE_BREAK.split(t).length;
    }

    static String opener(String text) {
        if (text == null) return "";
        String t = text.replace("**", "").strip();
        int sp = t.indexOf(' ');
        String w = sp < 0 ? t : t.substring(0, sp);
        return w.replaceAll("[^A-Za-z-]", "").toLowerCase();
    }

    private static double rate(int num, int den) {
        return den == 0 ? 0 : (double) num / den;
    }
}
