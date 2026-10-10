package com.resumepipeline.field;

import com.resumepipeline.field.FieldPreset.LensInfo;

import java.util.List;

/** The preset registry. One entry per field that has real wording; {@link Field#OTHER} reuses software. */
public final class FieldPresets {

    private FieldPresets() {}

    // The labels the Technical Skills block printed before fields existed.
    private static final FieldPreset SOFTWARE = new FieldPreset(
            Field.SOFTWARE, "Software engineering",
            List.of(
                    new LensInfo("ai-ml", "AI / ML", "RAG, agents, embeddings, prompt design"),
                    new LensInfo("backend", "Backend", "APIs, schemas, indexes, migrations"),
                    new LensInfo("data", "Data Engineering", "ingestion, parsers, ETL, geospatial"),
                    new LensInfo("general", "General", "everything else: product, tooling, ops")),
            List.of("Languages", "Frameworks", "Databases & AI", "DevOps & Tools", "AI & Integrations"));

    private static final FieldPreset MECHANICAL = new FieldPreset(
            Field.MECHANICAL, "Mechanical engineering",
            List.of(
                    new LensInfo("design", "Design & Analysis", "CAD, FEA, tolerancing, material and process selection"),
                    new LensInfo("testing", "Testing & Validation", "test plans, rigs, instrumentation, DFMEA, correlation"),
                    new LensInfo("manufacturing", "Manufacturing & Process", "fabrication, tooling, quality, cost and yield"),
                    new LensInfo("program", "Project & Program", "scope, schedule, cross-team delivery, vendors"),
                    new LensInfo("general", "General Engineering", "everything else")),
            List.of("CAD & Modeling", "Simulation & Analysis", "Manufacturing & Quality", "Lab & Tools", "Standards & Interests"));

    public static FieldPreset of(Field field) {
        return field == Field.MECHANICAL ? MECHANICAL : SOFTWARE;
    }

    /** Every preset a UI can offer, in the order it should list them. */
    public static List<FieldPreset> all() {
        return List.of(SOFTWARE, MECHANICAL);
    }
}
