package com.resumepipeline.github;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links a bullet back to the verified evidence spans it most plausibly came from, so the UI can
 * open the file or commit behind it. Deterministic, no LLM: shared quantities weigh most (a
 * bullet's "240000ms" can only come from a span that says 240000), then shared identifier
 * words, with camelCase and snake_case split so {@code JobProgressStore} matches "job progress".
 */
public final class SourceTracer {

    private SourceTracer() {}

    public record Source(String path, int startLine, int endLine, String commit, String field, String claim) {}

    static final int MIN_SCORE = 3;
    static final int MAX_SOURCES = 3;
    private static final int NUMBER_WEIGHT = 3;

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "over", "via", "using", "used",
            "use", "per", "across", "each", "all", "its", "their", "than", "then", "when", "while",
            "built", "build", "implemented", "designed", "developed", "created", "added", "enabling",
            "string", "return", "public", "private", "final", "static", "void", "new", "class", "import");

    public static List<Source> trace(String bullet, List<RepoExplorer.Evidence> evidence) {
        if (bullet == null || evidence == null || evidence.isEmpty()) return List.of();
        Set<String> bWords = words(bullet);
        Set<String> bNums = numbers(bullet);

        record Scored(RepoExplorer.Evidence e, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (RepoExplorer.Evidence e : evidence) {
            String hay = nz(e.claim()) + "\n" + nz(e.text());
            Set<String> w = words(hay);
            Set<String> n = numbers(hay);
            int score = 0;
            for (String x : bWords) if (w.contains(x)) score++;
            for (String x : bNums) if (n.contains(x)) score += NUMBER_WEIGHT;
            if (score >= MIN_SCORE) scored.add(new Scored(e, score));
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAX_SOURCES)
                .map(s -> new Source(s.e().path(), s.e().startLine(), s.e().endLine(), s.e().commit(), s.e().field(), s.e().claim()))
                .toList();
    }

    static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        Matcher m = WORD.matcher(s.replace("**", ""));
        while (m.find()) {
            // Split camelCase / PascalCase identifiers into their parts, keep the whole too.
            String tok = m.group();
            for (String part : tok.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) add(out, part);
            add(out, tok);
        }
        return out;
    }

    private static void add(Set<String> out, String w) {
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() >= 3 && !STOP.contains(l)) out.add(l);
    }

    private static Set<String> numbers(String s) {
        Set<String> out = new HashSet<>();
        Matcher m = DIGITS.matcher(s.replaceAll("(?<=\\d),(?=\\d{3})", ""));
        while (m.find()) {
            String d = m.group().replaceFirst("^0+(?=\\d)", "");
            if (d.length() >= 2) out.add(d); // single digits are everywhere; they prove nothing
        }
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
