package com.resumepipeline.field;

import java.util.List;

/**
 * Everything the pipeline varies by discipline, as data. The software preset carries the exact
 * wording the pipeline used before fields existed, so a software user's output does not change.
 *
 * @param field       the field this preset serves ({@link Field#OTHER} runs the software preset)
 * @param name        what the UI calls the field
 * @param lenses      the lenses a user of this field can pick, in display order; slugs live in
 *                    {@code CategoryLenses.LENSES}
 * @param skillLabels the five skills-row labels, in the order of the profile's five skills columns
 */
public record FieldPreset(Field field, String name, List<LensInfo> lenses, List<String> skillLabels) {

    /** A lens as the UI shows it. */
    public record LensInfo(String slug, String label, String blurb) {}

    /** The lens slugs, in display order. */
    public List<String> lensSlugs() {
        return lenses.stream().map(LensInfo::slug).toList();
    }
}
