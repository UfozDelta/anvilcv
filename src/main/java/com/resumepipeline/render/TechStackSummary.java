package com.resumepipeline.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortens a project's free-text {@code techStack} into the few technology names that
 * belong in a resume heading.
 *
 * <p>The field is deliberately verbose — content_extract.md asks the user for pinned
 * versions and named algorithms because that detail feeds bullet generation. The heading
 * is the other consumer and needs the opposite: four names, no versions, no prose. So a
 * stack like
 *
 * <pre>Java 21, Spring Boot 3.4.0 (web, data-jpa, security), PostgreSQL (Neon, prod) with Flyway migrations</pre>
 *
 * renders as {@code Java, Spring Boot, PostgreSQL, Flyway}. Matching is against the
 * curated {@code tech-terms.txt} allowlist, which also supplies canonical casing — so a
 * bogus "pandas 2" comes out as "pandas" and version noise never reaches the page.
 */
public final class TechStackSummary {

    private TechStackSummary() {}

    private static final String TERMS_RESOURCE = "tech-terms.txt";

    /** Names on the heading. Four fits the line with room for the dates column. */
    private static final int MAX_TERMS = 4;

    /** Only used when nothing matched — keeps a niche stack from vanishing entirely. */
    private static final int FALLBACK_TERMS = 3;
    private static final int FALLBACK_MAX_CHARS = 60;
    private static final int FALLBACK_MAX_WORDS = 3;

    /** Parenthesised asides, bracketed notes, and quoted code spans — all heading noise. */
    private static final Pattern DETAIL = Pattern.compile(
            "\\([^()]*\\)|\\[[^\\[\\]]*\\]|'[^']*'|‘[^’]*’|\"[^\"]*\"");

    private static final Pattern FRAGMENT = Pattern.compile("[,;]|\\s+with\\s+|\\s+and\\s+");

    /**
     * A term shorter than this is matched case-sensitively. Without it "R" fires on "R&D"
     * and "Go" on "go-to" — at two characters there is no room for a distinguishing shape.
     */
    private static final int CASE_SENSITIVE_BELOW = 3;

    private record Term(String canonical, Pattern pattern) {}

    /** Longest canonical name first, so "Spring Boot" claims its text before "Spring" can. */
    private static final List<Term> TERMS = loadTerms();

    private static List<Term> loadTerms() {
        List<String> names = new ArrayList<>();
        InputStream in = TechStackSummary.class.getClassLoader().getResourceAsStream(TERMS_RESOURCE);
        if (in == null) throw new IllegalStateException("Missing classpath resource: " + TERMS_RESOURCE);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) names.add(t);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        names.sort(Comparator.comparingInt(String::length).reversed());

        List<Term> terms = new ArrayList<>(names.size());
        for (String name : names) {
            // Boundaries are alphanumeric-only so "C++", "C#", "Next.js" and "HTTP/2" match
            // as written, while "Go" still refuses to fire inside "Google".
            int flags = name.length() < CASE_SENSITIVE_BELOW ? 0 : Pattern.CASE_INSENSITIVE;
            terms.add(new Term(name, Pattern.compile(
                    "(?<![A-Za-z0-9])" + Pattern.quote(name) + "(?![A-Za-z0-9])", flags)));
        }
        return List.copyOf(terms);
    }

    /** Null/blank in, empty out — the caller falls back to bullet tags. */
    public static String shorten(String raw) {
        if (raw == null || raw.isBlank()) return "";

        String cleaned = DETAIL.matcher(raw).replaceAll(" ");

        // Claimed character ranges, so a term nested inside a longer one is not emitted twice
        // ("React" must not follow "React Native" out of the same six words).
        boolean[] claimed = new boolean[cleaned.length()];
        List<int[]> hits = new ArrayList<>();          // {startIndex, termIndex}
        for (int i = 0; i < TERMS.size(); i++) {
            Matcher m = TERMS.get(i).pattern().matcher(cleaned);
            while (m.find()) {
                if (overlapsClaimed(claimed, m.start(), m.end())) continue;
                for (int c = m.start(); c < m.end(); c++) claimed[c] = true;
                hits.add(new int[]{m.start(), i});
                break;                                  // one mention per technology
            }
        }

        // Author's order is meaningful — the primary language is usually written first.
        hits.sort(Comparator.comparingInt(h -> h[0]));
        LinkedHashSet<String> picked = new LinkedHashSet<>();
        for (int[] h : hits) {
            picked.add(TERMS.get(h[1]).canonical());
            if (picked.size() == MAX_TERMS) break;
        }

        return picked.isEmpty() ? fallback(cleaned) : String.join(", ", picked);
    }

    private static boolean overlapsClaimed(boolean[] claimed, int start, int end) {
        for (int i = start; i < end; i++) if (claimed[i]) return true;
        return false;
    }

    /**
     * Nothing in the allowlist matched — an unusual toolchain, or a term we have not
     * curated yet. Emit the first few short fragments verbatim rather than an empty
     * heading, capped so an unmatched prose blob still cannot overrun the page.
     */
    private static String fallback(String cleaned) {
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (String fragment : FRAGMENT.split(cleaned)) {
            String f = fragment.trim().replaceAll("\\s+", " ");
            if (f.isEmpty() || f.split(" ").length > FALLBACK_MAX_WORDS) continue;
            int cost = f.length() + (kept.isEmpty() ? 0 : 2);
            if (used + cost > FALLBACK_MAX_CHARS) break;
            kept.add(f);
            used += cost;
            if (kept.size() == FALLBACK_TERMS) break;
        }
        return String.join(", ", kept);
    }
}
