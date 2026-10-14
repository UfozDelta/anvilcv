package com.resumepipeline.quota;

import java.util.Locale;

/**
 * What a plan includes. Matches the pricing page: keep the two in step.
 *
 * @param tailors     tailored pages included per period
 * @param generations bullet-writing runs included per period; null on the free plan, which is
 *                    limited by how many entries it may write bullets for instead
 * @param monthly     true if the allowance resets each calendar month; false for a one-off pack
 */
public enum Plan {
    FREE("free", 3, null, true),
    STARTER("starter", 60, 5, true),
    PRO("pro", 150, 15, true),
    PACK("pack", 200, 20, false);

    private final String slug;
    private final int tailors;
    private final Integer generations;
    private final boolean monthly;

    Plan(String slug, int tailors, Integer generations, boolean monthly) {
        this.slug = slug;
        this.tailors = tailors;
        this.generations = generations;
        this.monthly = monthly;
    }

    public String slug() { return slug; }
    public int tailors() { return tailors; }
    public Integer generations() { return generations; }
    public boolean monthly() { return monthly; }

    /** An unknown stored value counts as free: the strictest plan is the safe one. */
    public static Plan of(String slug) {
        if (slug != null) {
            String s = slug.toLowerCase(Locale.ROOT);
            for (Plan p : values()) if (p.slug.equals(s)) return p;
        }
        return FREE;
    }
}
