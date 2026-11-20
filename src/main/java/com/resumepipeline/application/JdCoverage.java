package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.project.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The JD coverage matrix: what a posting actually asks for, and which of it a resume
 * carries. Keyword overlap alone cannot tell a hard gate from a nice-to-have or say
 * whether a bullet shows a mechanism or only names a technology, so this extracts four
 * kinds of JD item and scores each one against the text that will actually render.
 *
 * <p>Pure and deterministic: no LLM, no DB. Alias handling delegates to
 * {@link KeywordScorer}, so "K8s" still covers "Kubernetes" and "Postgres" covers
 * "PostgreSQL" — the alias table stays in one place.
 */
public final class JdCoverage {

    private JdCoverage() {}

    public enum Kind { HARD_GATE, MINIMUM, PREFERRED, RESPONSIBILITY, KEYWORD }
    public enum Status { COVERED, THIN, MISSING }
    /** The three angles every entry's bullets should carry (SOP §4). */
    public enum Angle { WHAT, MECHANISM, OUTCOME }

    public record Item(String text, Kind kind, Status status) {}

    /** The extracted matrix; {@code items} is in JD order, hard gates first. */
    public record Matrix(List<Item> items) {
        /** Minimum/preferred quals and responsibilities left uncovered or only thinly evidenced. */
        public List<String> coverageGaps() {
            return items.stream()
                    .filter(i -> i.kind() != Kind.HARD_GATE && i.kind() != Kind.KEYWORD)
                    .filter(i -> i.status() != Status.COVERED)
                    .map(i -> i.kind().name().toLowerCase().replace('_', ' ') + ": " + i.text())
                    .toList();
        }

        /** Hard gates the rendered page does not satisfy — these are screening failures. */
        public List<String> hardGateMisses() {
            return items.stream()
                    .filter(i -> i.kind() == Kind.HARD_GATE && i.status() != Status.COVERED)
                    .map(Item::text)
                    .toList();
        }

        /** How many matrix items this bullet fully covers: the unit of entry-level JD density. */
        public long coveredCount(String bulletText) {
            return items.stream().filter(i -> statusOf(bulletText, i) == Status.COVERED).count();
        }

        /** Which JD items this bullet covers, thinly covers, or misses. */
        public Map<Item, Status> score(String bulletText) {
            Map<Item, Status> out = new LinkedHashMap<>();
            for (Item i : items) out.put(i, statusOf(bulletText, i));
            return out;
        }

        /** Items grouped by kind, for the ranking prompt. */
        public List<String> byKind(Kind kind) {
            return items.stream().filter(i -> i.kind() == kind).map(Item::text).toList();
        }
    }

    // Cue phrases, checked in this order: a "must have" line is a minimum qual even when
    // it mentions a responsibility verb, and a "preferred" line is never a hard gate just
    // because it contains "degree".
    private static final Pattern HARD_GATE = Pattern.compile(
            "(?i)\\b(must be|must have|must complete|required to|requirement|eligib|"
          + "graduat|available|authoriz|citizen|visa|export control|clearance|on-?site|"
          + "at least \\d+ years|enrolled|degree)\\b");
    private static final Pattern MINIMUM = Pattern.compile(
            "(?i)\\b(required|must have|minimum|at least|proficien|strong (?:knowledge|experience)|"
          + "experience with|skilled in|hands-?on)\\b");
    private static final Pattern PREFERRED = Pattern.compile(
            "(?i)\\b(preferred|nice to have|bonus|plus|familiar|exposure|desired|advantage)\\b");

    /** Responsibility verbs a posting uses; mirroring them is honest only when the work supports it. */
    private static final List<String> RESPONSIBILITY_VERBS = List.of(
            "build", "develop", "design", "implement", "automate", "orchestrate", "integrate",
            "deploy", "maintain", "test", "debug", "analyz", "collaborat", "track", "document",
            "lead", "optimiz", "support", "own", "drive", "create", "manage", "deliver",
            "investigat", "monitor", "improv", "scal", "migrat");

    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|[\\r\\n]+");
    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final int MAX_ITEMS = 16;

    private static final Set<String> STOP = Set.of(
            "about", "above", "across", "after", "again", "against", "applicants", "application",
            "because", "been", "being", "between", "candidate", "could", "degree", "desired",
            "eligible", "experience", "familiar", "from", "having", "include", "including", "into",
            "job", "knowledge", "level", "minimum", "must", "need", "other", "our", "preferred",
            "required", "requirement", "role", "should", "skill", "skills", "strong", "that",
            "their", "this", "through", "will", "with", "within", "work", "working", "would",
            "years", "your", "team", "teams", "plus", "bonus");

    /**
     * Extracts the matrix from the JD alone (every item starts {@link Status#MISSING}), used
     * by the ranking prompt. {@link #of} then scores the same items against rendered text.
     */
    public static Matrix items(String jdText, Collection<String> keywords) {
        return of(jdText, "", keywords);
    }

    /** Extracts the matrix and scores every item against the text that will actually render. */
    public static Matrix of(String jdText, String renderedText, Collection<String> keywords) {
        List<Item> items = new ArrayList<>();
        if (jdText != null && !jdText.isBlank()) {
            for (String sentence : SENTENCE.split(jdText)) {
                String s = sentence.strip();
                if (s.length() < 8) continue;
                Kind kind = classify(s);
                if (kind != null) items.add(new Item(abbreviate(s), kind, statusOf(s, renderedText, kind)));
                if (items.size() >= MAX_ITEMS) break;
            }
            // Responsibility verbs are often buried in a long responsibility sentence; pulling
            // them out separately makes the "which bullet covers whichJD item" check explicit.
            for (String verb : RESPONSIBILITY_VERBS) {
                if (items.size() >= MAX_ITEMS) break;
                if (containsWord(jdText, verb)) {
                    items.add(new Item(verb, Kind.RESPONSIBILITY, statusOf(verb, renderedText, Kind.RESPONSIBILITY)));
                }
            }
        }
        // The JD's own keyword list stays in the matrix: an alias-aware literal check is the
        // fallback for anything the sentence-level extraction could not parse.
        if (keywords != null) {
            for (String k : keywords) {
                if (k == null || k.isBlank()) continue;
                boolean present = !renderedText.isBlank() && KeywordScorer.mentions(renderedText, k);
                items.add(new Item(k, Kind.KEYWORD, present ? Status.COVERED : Status.MISSING));
            }
        }
        return new Matrix(List.copyOf(items));
    }

    private static Kind classify(String s) {
        if (HARD_GATE.matcher(s).find()) return Kind.HARD_GATE;
        if (PREFERRED.matcher(s).find()) return Kind.PREFERRED;
        if (MINIMUM.matcher(s).find()) return Kind.MINIMUM;
        return null; // responsibility verbs are extracted separately below
    }

    private static Status statusOf(String text, Item item) {
        return statusOf(item.text(), text, item.kind());
    }

    private static Status statusOf(String itemText, String rendered, Kind kind) {
        if (rendered == null || rendered.isBlank()) return Status.MISSING;
        String lower = rendered.toLowerCase();
        if (kind == Kind.HARD_GATE) {
            Matcher year = YEAR.matcher(itemText);
            if (year.find() && lower.contains(year.group(1))) return Status.COVERED;
        }
        if (kind == Kind.RESPONSIBILITY) {
            for (String form : verbForms(itemText)) {
                if (KeywordScorer.mentions(rendered, form)) return Status.COVERED;
            }
            return Status.MISSING;
        }
        List<String> terms = salientTerms(itemText);
        if (terms.isEmpty()) return Status.MISSING;
        long hits = terms.stream().filter(t -> KeywordScorer.mentions(rendered, t)).count();
        double ratio = hits / (double) terms.size();
        if (ratio >= 0.6) return Status.COVERED;
        if (ratio >= 0.3) return Status.THIN;
        return Status.MISSING;
    }

    /** Alias-aware keyword check plus plain containment; length keeps "go" from matching "logo". */
    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\w*\\b").matcher(text).find();
    }

    private static List<String> salientTerms(String itemText) {
        return java.util.Arrays.stream(itemText.toLowerCase().split("[^a-z0-9+#.]+"))
                .filter(t -> t.length() >= 4 && !STOP.contains(t))
                .distinct()
                .limit(12)
                .toList();
    }

    private static List<String> verbForms(String verb) {
        Map<String, List<String>> irregular = Map.of(
                "build", List.of("build", "builds", "built", "building"),
                "lead", List.of("lead", "leads", "led", "leading"),
                "own", List.of("own", "owns", "owned", "owning"),
                "test", List.of("test", "tests", "tested", "testing"));
        return irregular.getOrDefault(verb, List.of(verb, verb + "s", verb + "ed", verb + "ing"));
    }

    /** Deterministic angle of one bullet, used to check an entry covers all three SOP angles. */
    public static Angle angleOf(String text) {
        if (text == null || text.isBlank()) return Angle.WHAT;
        String t = text.toLowerCase();
        boolean outcome = t.matches("(?s).*\\b(cut|reduced|recover|prevent|avoid|stopped|no longer|"
                + "escalat|faster|saved|held|improved)\\b.*")
                || t.contains("fail-closed") || t.contains("%") || t.contains("ms")
                || t.contains("failure prevented");
        if (outcome) return Angle.OUTCOME;
        if (t.contains(" by ") || t.contains(" using ") || t.contains(" via ") || t.contains(" through ")
                || t.contains("self-cancelling") || t.contains("hash-locked") || t.contains("idempotent")
                || t.contains("hash") || t.contains("queue") || t.contains("pipeline") || t.contains("index")) {
            return Angle.MECHANISM;
        }
        return Angle.WHAT;
    }

    private static String abbreviate(String s) {
        return s.length() <= 140 ? s : s.substring(0, 137).stripTrailing() + "...";
    }

    /**
     * Orders selected entries by JD density (how many matrix items their bullets cover),
     * breaking ties by the caller's existing order. Within an entry the bullets are ordered
     * WHAT → MECHANISM → OUTCOME so the three angles read in that sequence.
     */
    public static List<Bullet> orderByDensityAndAngle(List<Bullet> selected, Matrix matrix,
                                                      Map<java.util.UUID, Project> projectById) {
        if (matrix == null || matrix.items().isEmpty()) return selected;
        Set<java.util.UUID> order = selected.stream().map(Bullet::getProjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<java.util.UUID, Long> density = new LinkedHashMap<>();
        for (java.util.UUID pid : order) {
            density.put(pid, selected.stream().filter(b -> b.getProjectId().equals(pid))
                    .mapToLong(b -> matrix.coveredCount(b.getText())).sum());
        }
        List<java.util.UUID> sorted = new ArrayList<>(order);
        sorted.sort((a, b) -> {
            int cmp = Long.compare(density.get(b), density.get(a));
            if (cmp != 0) return cmp;
            return Integer.compare(new ArrayList<>(order).indexOf(a), new ArrayList<>(order).indexOf(b));
        });
        List<Bullet> out = new ArrayList<>();
        for (java.util.UUID pid : sorted) {
            List<Bullet> entry = selected.stream().filter(b -> b.getProjectId().equals(pid))
                    .sorted(java.util.Comparator.comparingInt(b -> angleOf(b.getText()).ordinal()))
                    .toList();
            out.addAll(entry);
        }
        return out;
    }

    /** Whether an entry's bullets carry the three distinct SOP angles. */
    public static boolean hasThreeAngles(List<Bullet> entryBullets) {
        return entryBullets.stream().map(b -> angleOf(b.getText())).collect(Collectors.toSet()).size() >= 3;
    }
}
