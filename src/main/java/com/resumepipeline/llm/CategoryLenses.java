package com.resumepipeline.llm;

import com.resumepipeline.field.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Category "lenses" that focus bullet generation on a specific audience-targeted angle
 * of the same role/project. Each lens prepends a short paragraph to the prompt telling
 * the LLM what to emphasize and what to call out by name.
 */
public final class CategoryLenses {

    // Each lens = what the role covers + what a reviewer hiring for it looks for. No technique
    // lists and no "quantify with X counts": both used to read as a quota, so the model borrowed
    // techniques the project never used and padded bullets with commit / endpoint / build counts.
    public static final Map<String, String> LENSES = new LinkedHashMap<>();
    static {
        LENSES.put("ai-ml", """
                LENS: AI / Machine Learning.
                Covers: work where a model, retrieval or learned component does the core job.
                A reviewer for this role looks for: which technique was chosen and why, how output
                quality was judged or protected, and what it cost in latency or money.""");

        LENSES.put("backend", """
                LENS: Backend & Data Architecture.
                Covers: services, APIs, data models and storage behind a product.
                A reviewer for this role looks for: design decisions and their tradeoffs, correctness
                under failure (consistency, idempotency, migrations), and performance at the scale
                the source states.""");

        LENSES.put("data", """
                LENS: Data Engineering.
                Covers: getting data in, cleaning it, transforming it and serving it for analysis.
                A reviewer for this role looks for: sources and volume, how data quality was enforced,
                and what the pipeline made possible downstream.""");

        LENSES.put("general", """
                LENS: General Software Engineering.
                Covers: the project's work as a whole when it is not mainly AI / ML, backend or data.
                A reviewer for this role looks for: what was built and for whom, the engineering
                decisions behind it, and the measurable result the source states.""");
    }

    /**
     * The mechanical-engineering lenses (MULTI_FIELD_PLAN step 3). Kept apart from {@link #LENSES}, which is
     * the software set: a software user must never be offered, or generate for, a lens that is not theirs.
     * {@code general} exists in both and reads differently in each.
     */
    public static final Map<String, String> MECHANICAL_LENSES = new LinkedHashMap<>();
    static {
        MECHANICAL_LENSES.put("design", """
                LENS: Design & Analysis.
                Covers: work where a part, assembly or system was designed, modeled or analyzed: CAD,
                FEA, hand calculations, tolerancing, material and process selection.
                A reviewer for this role looks for: the requirement the design had to meet, the design
                decision and the alternative rejected, how the design was verified (analysis, prototype,
                test), and the margin, mass or cost result the source states.""");

        MECHANICAL_LENSES.put("testing", """
                LENS: Testing & Validation.
                Covers: planning, building and running tests, rigs and instrumentation, and finding why
                something failed (DFMEA, root cause, correlating a model to measurements).
                A reviewer for this role looks for: what was tested against which requirement, how it was
                measured and how accurately, what the test found, and what changed because of it.""");

        MECHANICAL_LENSES.put("manufacturing", """
                LENS: Manufacturing & Process.
                Covers: making the part: fabrication, tooling and fixtures, process development, quality
                control, design for manufacture and assembly, cost and yield.
                A reviewer for this role looks for: which process or tooling was developed or improved,
                the tolerance or quality level held, and the cost, cycle-time or yield result the source
                states.""");

        MECHANICAL_LENSES.put("program", """
                LENS: Project & Program.
                Covers: delivering engineering work across people and time: scope, schedule, vendors,
                cross-functional teams, design reviews and budget.
                A reviewer for this role looks for: the size of the team and budget the source states,
                what was owned versus contributed to, and how schedule, cost or risk was kept in check.""");

        MECHANICAL_LENSES.put("general", """
                LENS: General Mechanical Engineering.
                Covers: the work as a whole when it is not mainly design, testing, manufacturing or
                program work.
                A reviewer for this role looks for: what was built and for whom, the engineering
                decisions behind it, and the measurable result the source states.""");
    }

    /** The lenses a user of {@code field} works with. {@code other} runs the software set. */
    public static Map<String, String> lensesFor(Field field) {
        return Field.orDefault(field) == Field.MECHANICAL ? MECHANICAL_LENSES : LENSES;
    }

    /** The lens block for {@code category} under {@code field}, or null when none applies. */
    public static String lensFor(String category, Field field) {
        if (category == null || category.isBlank()) return null;
        return lensesFor(field).get(category);
    }

    /** Prompt-schema hint listing the slugs a JD parse may choose from for {@code field}. */
    public static String slugList(Field field) {
        return String.join(", ", lensesFor(field).keySet());
    }

    /** As {@link #validate(List)}, against the lenses {@code field} offers. */
    public static List<String> validate(List<String> raw, Field field) {
        if (raw == null) return List.of();
        Map<String, String> known = lensesFor(field);
        return raw.stream()
                .filter(s -> s != null && known.containsKey(s.trim().toLowerCase()))
                .map(s -> s.trim().toLowerCase())
                .distinct()
                .limit(2)
                .toList();
    }

    /** True for a slug any field offers. Storage keeps whatever a field wrote; use the field-aware calls to offer. */
    public static boolean isKnown(String slug) {
        if (slug == null) return false;
        String s = slug.trim().toLowerCase(java.util.Locale.ROOT);
        return LENSES.containsKey(s) || MECHANICAL_LENSES.containsKey(s);
    }

    /** The lens slug for {@code slug}; anything unknown or blank is {@code general}. Any field's slug is kept. */
    public static String normalize(String slug) {
        if (slug == null) return GENERAL;
        String s = slug.trim().toLowerCase(java.util.Locale.ROOT);
        return isKnown(s) ? s : GENERAL;
    }

    /**
     * Returns the lens block for the given category, or null if no lens applies
     * (e.g. category = "general" or unknown).
     */
    public static String lensFor(String category) {
        if (category == null || category.isBlank()) return null;
        return LENSES.get(category);
    }

    public static final String GENERAL = "general";
    public static final String GENERALIST = "generalist";

    /** Keeps known slugs only, de-duplicated, at most 2, best first. Null-safe. */
    public static List<String> validate(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(s -> s != null && LENSES.containsKey(s.trim().toLowerCase()))
                .map(s -> s.trim().toLowerCase())
                .distinct()
                .limit(2)
                .toList();
    }

    /** Weight of {@code category} in an ordered lens list: sole lens 1.0, else 0.7 / 0.3, absent 0. */
    public static double weight(List<String> lenses, String category) {
        if (category == null) return 0;
        int i = lenses.indexOf(category);
        if (i < 0) return 0;
        return lenses.size() == 1 ? 1.0 : (i == 0 ? 0.7 : 0.3);
    }

    /** Human/prompt label: "backend+data", or "generalist" when no lens applies. */
    public static String label(List<String> lenses) {
        return lenses.isEmpty() ? GENERALIST : String.join("+", lenses);
    }

    /** Prompt-schema hint listing the slugs the JD parse may choose from. */
    public static String slugList() {
        return String.join(", ", LENSES.keySet());
    }

    private CategoryLenses() {}
}
