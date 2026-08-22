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
     * True when {@code text} actually mentions {@code term}, using the same normalisation
     * as scoring — so a bullet saying "K8s" mentions "kubernetes". Used to check that the
     * tags an LLM attaches to a bullet are things it really wrote about.
     */
    public static boolean mentions(String text, String term) {
        String canonical = canonical(term);
        return !canonical.isBlank() && forms(text).contains(canonical);
    }

    /**
     * The single normalised form of a keyword or tag: every token glued together, so
     * "Amazon Web Services" and "AWS" both reduce to "amazonwebservices".
     */
    private static String canonical(String s) {
        List<String> tokens = tokenize(s);
        if (tokens.isEmpty()) return "";
        return alias(String.join("", tokens));
    }

    /**
     * Every normalised run of 1..{@link #MAX_NGRAM} adjacent tokens in the text. A keyword
     * matches the bullet when its canonical form appears in this set.
     */
    private static Set<String> forms(String text) {
        List<String> tokens = tokenize(text);
        Set<String> forms = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            StringBuilder run = new StringBuilder();
            for (int n = 0; n < MAX_NGRAM && i + n < tokens.size(); n++) {
                run.append(tokens.get(i + n));
                // Glue first, alias second: "node" + "js" must become "nodejs" rather than
                // "node" + the aliased "javascript".
                forms.add(alias(run.toString()));
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

    private static String alias(String s) {
        return ALIASES.getOrDefault(s, s);
    }
}
