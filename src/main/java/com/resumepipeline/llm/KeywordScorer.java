package com.resumepipeline.llm;

import com.resumepipeline.bullet.Bullet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scores a bullet against JD keywords by counting keyword occurrences in the bullet's
 * own text, plus any tag hits as a secondary signal.
 *
 * <p>Bullet {@code tags} are coarse category labels (backend/frontend/ai-ml/devops/...),
 * while JD keywords are specific tech terms (React, Kubernetes, Postgres...) — those
 * vocabularies barely overlap, so tag-only matching was near-random. Matching keywords
 * against the bullet text itself is what actually reflects relevance.
 *
 * <p>Both sides run through the same normalisation before comparison, because a literal
 * word-boundary match misses most of the ways the same technology gets written down:
 * a JD saying "Kubernetes" must match a bullet saying "K8s", "PostgreSQL" must match
 * "Postgres", "Node.js" must match "NodeJS", and "CI/CD" must match anything at all
 * (punctuation meant it previously matched nothing). Normalisation is:
 * expand punctuation-bearing aliases, split on non-alphanumerics, glue adjacent tokens
 * back together in runs of up to {@link #MAX_NGRAM}, then map each result through the
 * alias table. A keyword matches when its normalised form is one of those runs.
 */
public final class KeywordScorer {

    private KeywordScorer() {}

    /** Longest multi-word keyword we can match, e.g. "amazon web services". */
    private static final int MAX_NGRAM = 3;

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /**
     * Spelling variants mapped onto a shared canonical form. Entries whose key contains
     * punctuation are substituted into the raw text before tokenising (tokenising would
     * otherwise destroy them — "C++" and "C#" both collapse to a bare "c"); the rest are
     * looked up after tokenising. Extend freely: an entry only ever merges two spellings
     * that already mean the same thing.
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    static {
        // Punctuation-bearing — substituted before the text is split into tokens.
        ALIASES.put("c++", "cplusplus");
        ALIASES.put("c#", "csharp");
        ALIASES.put(".net", "dotnet");
        ALIASES.put("ci/cd", "cicd");
        ALIASES.put("node.js", "nodejs");
        ALIASES.put("next.js", "nextjs");
        ALIASES.put("vue.js", "vuejs");
        ALIASES.put("react.js", "react");
        // Alphanumeric — looked up after tokenising.
        ALIASES.put("k8s", "kubernetes");
        ALIASES.put("postgres", "postgresql");
        ALIASES.put("psql", "postgresql");
        ALIASES.put("reactjs", "react");
        ALIASES.put("golang", "go");
        ALIASES.put("gcp", "googlecloud");
        ALIASES.put("aws", "amazonwebservices");
        ALIASES.put("js", "javascript");
        ALIASES.put("ml", "machinelearning");
        ALIASES.put("ai", "artificialintelligence");
    }

    /**
     * One-directional: text naming a key also covers each value, never the reverse. A bullet
     * on PostgreSQL shows SQL; one saying only "SQL" does not show PostgreSQL. Text side only.
     */
    private static final Map<String, String> IMPLIES = Map.of(
            "postgresql", "sql",
            "mysql", "sql",
            "sqlite", "sql",
            "mssql", "sql",
            "sqlserver", "sql",
            "mariadb", "sql");

    /**
     * Plurals that name a different thing than their singular — "Teams" is the product, "team"
     * is prose. A keyword spelled like this is never folded, so it needs the plural in the text.
     */
    private static final Set<String> NO_FOLD = Set.of(
            "teams", "windows", "rails", "sales", "requests", "pages", "functions",
            "sheets", "docs", "actions", "ads", "pandas");

    /** The punctuation-bearing subset, pre-filtered so the hot path does not re-scan the table. */
    private static final List<Map.Entry<String, String>> PUNCTUATED = ALIASES.entrySet().stream()
            .filter(e -> !e.getKey().chars().allMatch(Character::isLetterOrDigit))
            .toList();

    public static ToLongFunction<Bullet> score(Set<String> keywordsLower) {
        // Canonicalised once here rather than per bullet — score() is handed to comparators
        // that call it repeatedly during sorting.
        Set<String> wanted = keywordsLower.stream()
                .map(KeywordScorer::canonical)
                .filter(k -> !k.isBlank())
                .collect(Collectors.toSet());

        return b -> {
            Set<String> forms = forms(b.getText());
            long textHits = wanted.stream().filter(forms::contains).count();
            long tagHits = Arrays.stream(b.getTags() == null ? new String[0] : b.getTags())
                    .map(KeywordScorer::canonical)
                    .filter(wanted::contains)
                    .count();
            return textHits * 2 + tagHits; // text match is the stronger signal
        };
    }

    /**
     * Which of {@code keywordsLower} this bullet covers, in canonical form — the same
     * text-and-tag matching {@link #score} counts, exposed as a set instead of a total.
     *
     * <p>A count says how good a bullet is alone; a set says what it adds to bullets already
     * chosen. Selection needs the second: a third bullet repeating keywords the first two
     * already carry adds nothing an ATS or a recruiter can see, while one carrying a keyword
     * the page is still missing does.
     */
    public static Set<String> matched(Bullet b, Set<String> keywordsLower) {
        Set<String> present = new HashSet<>(forms(b.getText()));
        for (String tag : b.getTags() == null ? new String[0] : b.getTags()) {
            String c = canonical(tag);
            if (!c.isBlank()) present.add(c);
        }
        return keywordsLower.stream()
                .map(KeywordScorer::canonical)
                .filter(k -> !k.isBlank() && present.contains(k))
                .collect(Collectors.toSet());
    }

    /**
     * True when {@code text} actually mentions {@code term}, using the same normalisation
     * as scoring — so a bullet saying "K8s" mentions "kubernetes". Used to check that the
     * tags an LLM attaches to a bullet are things it really wrote about.
     */
    public static boolean mentions(String text, String term) {
        String canonical = canonical(term);
        return !canonical.isBlank() && forms(text).contains(canonical);
    }

    /**
     * True when {@code item} (one entry of a user's skills list) names {@code term}: equal
     * after normalisation, or {@code term} is a run inside it ("React" in "React/Redux").
     * Unlike {@link #mentions}, implications do not count — knowing PostgreSQL does not put
     * "SQL" on the user's skills list.
     */
    public static boolean names(String item, String term) {
        String canonical = canonical(term);
        return !canonical.isBlank() && forms(item, false).contains(canonical);
    }

    /** True when both spell the same thing after normalisation ("Postgres" / "PostgreSQL"). */
    public static boolean sameTerm(String a, String b) {
        String ca = canonical(a);
        return !ca.isBlank() && ca.equals(canonical(b));
    }

    /**
     * Lower-cased spellings of {@code term} a plain string compare can match: the term itself,
     * its canonical form and every alias that shares it ("Postgres" -> postgres, postgresql, psql).
     */
    public static Set<String> variants(String term) {
        String c = canonical(term);
        if (c.isBlank()) return Set.of();
        Set<String> out = new HashSet<>(List.of(term.trim().toLowerCase(), c));
        ALIASES.forEach((k, v) -> {
            if (canonical(k).equals(c)) out.add(k);
            if (canonical(v).equals(c)) out.add(v);
        });
        return out;
    }

    /**
     * The single normalised form of a keyword or tag: every token glued together, so
     * "Amazon Web Services" and "AWS" both reduce to "amazonwebservices".
     */
    private static String canonical(String s) {
        List<String> tokens = tokenize(s);
        if (tokens.isEmpty()) return "";
        String a = alias(String.join("", tokens));
        return NO_FOLD.contains(a) ? a : fold(a);
    }

    /**
     * Every normalised run of 1..{@link #MAX_NGRAM} adjacent tokens in the text. A keyword
     * matches the bullet when its canonical form appears in this set.
     */
    private static Set<String> forms(String text) {
        return forms(text, true);
    }

    private static Set<String> forms(String text, boolean withImplies) {
        List<String> tokens = tokenize(text);
        Set<String> forms = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            StringBuilder run = new StringBuilder();
            for (int n = 0; n < MAX_NGRAM && i + n < tokens.size(); n++) {
                run.append(tokens.get(i + n));
                // Glue first, alias second: "node" + "js" must become "nodejs" rather than
                // "node" + the aliased "javascript".
                // Both spellings: a plural keyword folds, so the text must offer the folded form;
                // a NO_FOLD keyword stays plural, so it must offer the raw one too.
                String a = alias(run.toString());
                forms.add(a);
                forms.add(fold(a));
            }
        }
        if (withImplies) {
            for (Map.Entry<String, String> e : IMPLIES.entrySet()) {
                if (forms.contains(e.getKey())) forms.add(e.getValue());
            }
        }
        return forms;
    }

    /** Lower-case, expand punctuation-bearing aliases, then split on non-alphanumerics. */
    private static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        String t = s.toLowerCase();
        for (Map.Entry<String, String> e : PUNCTUATED) {
            if (t.contains(e.getKey())) {
                // Padded so "asp.net" becomes "asp dotnet" (two tokens, glued back to
                // "aspdotnet" by the n-gram pass) rather than the single token "aspdotnet".
                t = t.replace(e.getKey(), " " + e.getValue() + " ");
            }
        }
        List<String> tokens = new ArrayList<>();
        for (String tok : NON_ALNUM.split(t)) {
            if (!tok.isEmpty()) tokens.add(tok);
        }
        return tokens;
    }

    /**
     * Drops a plural "s" ("apis" -> "api", "microservices" -> "microservice"). Words of three
     * letters or fewer and "-ss"/"-us" endings are left alone, so "aws" and "css" (which would
     * become "CS") stay intact. Both sides fold the same way, so "redis" -> "redi" is harmless.
     */
    private static String fold(String s) {
        if (s.length() < 4 || !s.endsWith("s") || s.endsWith("ss") || s.endsWith("us")) return s;
        return s.substring(0, s.length() - 1);
    }

    private static String alias(String s) {
        return ALIASES.getOrDefault(s, s);
    }
}
