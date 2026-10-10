package com.resumepipeline.field;

import java.util.Locale;

/**
 * The discipline a user writes for. It picks the lenses, skill groups, prompt wording and
 * fabricated-metric units the pipeline uses. {@code software} is the default and keeps every
 * existing user's behaviour exactly as it was. See MULTI_FIELD_PLAN.md.
 *
 * <p>{@link #OTHER} ("not sure") stores the choice but runs the software preset: nothing is claimed
 * for a field that has no preset yet.
 */
public enum Field {
    SOFTWARE("software"),
    MECHANICAL("mechanical"),
    OTHER("other");

    private final String slug;

    Field(String slug) { this.slug = slug; }

    public String slug() { return slug; }

    /** The preset the pipeline actually runs for this field. */
    public FieldPreset preset() { return FieldPresets.of(this); }

    /** {@code f}, or {@link #SOFTWARE} when null: callers and test doubles may not know a field. */
    public static Field orDefault(Field f) { return f == null ? SOFTWARE : f; }

    /** The field for a stored or submitted slug; anything unknown or blank is {@link #SOFTWARE}. */
    public static Field of(String slug) {
        if (slug == null) return SOFTWARE;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        for (Field f : values()) if (f.slug.equals(s)) return f;
        return SOFTWARE;
    }

    /** True for a slug that names a field, so an API can reject typos instead of silently defaulting. */
    public static boolean isKnown(String slug) {
        if (slug == null) return false;
        String s = slug.trim().toLowerCase(Locale.ROOT);
        for (Field f : values()) if (f.slug.equals(s)) return true;
        return false;
    }
}
