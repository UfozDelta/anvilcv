package com.resumepipeline.llm;

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

        LENSES.put("frontend", """
                LENS: Frontend & Product.
                Covers: what users see and touch: UI, client-side state, rendering, interaction.
                A reviewer for this role looks for: what users could do because of it, how hard UI
                state or rendering problems were solved, and responsiveness or accessibility.""");

        LENSES.put("data", """
                LENS: Data Engineering.
                Covers: getting data in, cleaning it, transforming it and serving it for analysis.
                A reviewer for this role looks for: sources and volume, how data quality was enforced,
                and what the pipeline made possible downstream.""");

        LENSES.put("security", """
                LENS: Security & Authentication.
                Covers: identity, access control, secrets, tenant isolation and abuse prevention.
                A reviewer for this role looks for: the concrete threat each control stops, where the
                boundary is enforced, and any compliance constraint the source states.""");

        LENSES.put("devops", """
                LENS: Infrastructure & DevOps.
                Covers: build, deploy, hosting, observability and reliability.
                A reviewer for this role looks for: what got faster, safer or cheaper to ship or run,
                and how failures are detected and recovered from.""");

        LENSES.put("systems", """
                LENS: Distributed Systems & Real-time.
                Covers: concurrency, streaming, messaging and coordination between processes or services.
                A reviewer for this role looks for: how ordering, backpressure, retries or partial
                failure were handled, and the latency or throughput the source states.""");

        LENSES.put("comms", """
                LENS: Real-time Communications.
                Covers: telephony, messaging, email, chat and live media channels.
                A reviewer for this role looks for: which channels and providers were integrated, how
                delivery and failures were handled, and messaging rules (consent, opt-out) enforced.""");
    }

    /**
     * Returns the lens block for the given category, or null if no lens applies
     * (e.g. category = "general" or unknown).
     */
    public static String lensFor(String category) {
        if (category == null || category.isBlank()) return null;
        return LENSES.get(category);
    }

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
