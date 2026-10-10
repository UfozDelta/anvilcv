package com.resumepipeline.field;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Everything the pipeline varies by discipline, as data. The software preset carries the exact
 * wording the pipeline used before fields existed, so a software user's prompts do not change
 * (PromptGoldenTest pins them).
 *
 * @param field       the field this preset serves ({@link Field#OTHER} runs the software preset)
 * @param name        what the UI calls the field
 * @param lenses      the lenses a user of this field can pick, in display order; slugs live in
 *                    {@code CategoryLenses}
 * @param skillLabels the five skills-row labels, in the order of the profile's five skills columns
 * @param prompt      the field's words inside the generation prompts; not sent to the browser
 */
public record FieldPreset(Field field, String name, List<LensInfo> lenses, List<String> skillLabels,
                          @JsonIgnore Prompt prompt) {

    /** A lens as the UI shows it. */
    public record LensInfo(String slug, String label, String blurb) {}

    /**
     * The phrases the generation prompts drop in, each worded for one field.
     *
     * @param resultLine      what a measurable result looks like, "outside the codebase would notice ..." onwards
     * @param activityCounts  counts of the author's own artifacts: activity, never results
     * @param verbs           the example strong action verbs
     * @param openerFixVerbs  the verbs a weak opener is repaired with
     * @param identifiers     the internal identifiers that never belong on a resume
     * @param tagExamples     example technology tags
     * @param badExamples     the "do not write this" examples, one block
     * @param routineWork     work every project has, which is not a story by itself
     * @param activityShort   the activity counts as the story pass lists them
     * @param vanityList      the activity counts as the recovery pass lists them
     * @param techLabel       what the project's tech-stack field is called in the source material
     * @param lensFallback    the JD-parse hint for when no lens fits
     * @param vanity          the activity-count phrases a recruiter reads as padding
     */
    public record Prompt(String resultLine, String activityCounts, String verbs, String openerFixVerbs,
                         String identifiers, String tagExamples, String badExamples, String routineWork,
                         String activityShort, String vanityList, String techLabel, String lensFallback,
                         Pattern vanity) {}

    /** The lens slugs, in display order. */
    public List<String> lensSlugs() {
        return lenses.stream().map(LensInfo::slug).toList();
    }
}
