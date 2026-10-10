package com.resumepipeline.field;

import com.resumepipeline.field.FieldPreset.LensInfo;
import com.resumepipeline.field.FieldPreset.Prompt;

import java.util.List;
import java.util.regex.Pattern;

/** The preset registry. One entry per field that has real wording; {@link Field#OTHER} reuses software. */
public final class FieldPresets {

    private FieldPresets() {}

    /**
     * Counts of the author's own artifacts: activity, not results. A recruiter reads "120 commits"
     * or "300 unit tests" as padding. Deliberately narrow: domain counts that describe the product
     * ("12 report types", "3 regions", "30 REST endpoints") are scope, not vanity, and stay.
     */
    private static final Pattern SOFTWARE_VANITY = Pattern.compile(
            "\\b\\d[\\d,.]*\\+?\\s*(?:\\*\\*)?\\s*(?:in-repo |automated )?(?:commits?|s?loc|lines? of \\w+|source files|files"
                    + "|(?:unit |integration |e2e |end-to-end )?tests?|test cases|assert(?:ion)?s?(?: checks)?"
                    + "|(?:database |db )?tables|(?:flyway |database |db |schema )?migrations|contributors)\\b"
                    + "|\\bzero broken (?:main )?builds\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * The mechanical equivalents: how many drawings, models or meetings someone produced says nothing
     * about what the design achieved. Narrow on purpose, like the software one: "12 mounting points",
     * "4 load cases" and "6 subsystems" describe the design and stay.
     */
    private static final Pattern MECHANICAL_VANITY = Pattern.compile(
            "\\b\\d[\\d,.]*\\+?\\s*(?:\\*\\*)?\\s*(?:CAD |SolidWorks |part |assembly )?(?:files|models|drawings|sheets|revisions"
                    + "|ECOs?|ECNs?|BOMs?|reports|meetings|design reviews|emails|hours of (?:CAD|drafting|modeling)"
                    + "|simulations|iterations|prototypes)\\b",
            Pattern.CASE_INSENSITIVE);

    // The labels the Technical Skills block printed, and the words the prompts used, before fields existed.
    private static final FieldPreset SOFTWARE = new FieldPreset(
            Field.SOFTWARE, "Software engineering",
            List.of(
                    new LensInfo("ai-ml", "AI / ML", "RAG, agents, embeddings, prompt design"),
                    new LensInfo("backend", "Backend", "APIs, schemas, indexes, migrations"),
                    new LensInfo("data", "Data Engineering", "ingestion, parsers, ETL, geospatial"),
                    new LensInfo("general", "General", "everything else: product, tooling, ops")),
            List.of("Languages", "Frameworks", "Databases & AI", "DevOps & Tools", "AI & Integrations"),
            new Prompt(
                    "outside the codebase would notice — latency, cost, error or\nfailure rate, users served, data volume handled, time saved",
                    "commits, lines of code, files, tests, tables, migrations, \"zero broken builds\"",
                    "Built · Designed ·\nShipped · Implemented · Migrated · Automated · Integrated · Reduced · Wrote",
                    "Built, Designed, Shipped, Engineered, Implemented,\nMigrated, Integrated",
                    "table names, function names, file paths, env-var names",
                    "\"react\", \"kubernetes\",\n    \"postgresql\", \"grpc\"",
                    "  ✗ Maintained zero broken main builds across 120 commits.\n"
                            + "    (activity counts, not a result)\n"
                            + "  ✗ Built a backend test suite of 190 unit tests. This enabled rapid development.\n"
                            + "    (artifact count, plus a filler second sentence)\n"
                            + "  ✗ Worked on backend stuff using various tools and got things faster.\n"
                            + "    (passive opener, vague, no result)",
                    "CRUD screens, login, config, setup, a plain\n    test suite or CI",
                    "commits, lines of code, files, tests, tables, migrations",
                    "commits, lines of code,\nfiles, tests, tables, migrations, builds",
                    "Tech stack",
                    "Use general when the role is not mainly AI/ML, backend or data.",
                    SOFTWARE_VANITY));

    private static final FieldPreset MECHANICAL = new FieldPreset(
            Field.MECHANICAL, "Mechanical engineering",
            List.of(
                    new LensInfo("design", "Design & Analysis", "CAD, FEA, tolerancing, material and process selection"),
                    new LensInfo("testing", "Testing & Validation", "test plans, rigs, instrumentation, DFMEA, correlation"),
                    new LensInfo("manufacturing", "Manufacturing & Process", "fabrication, tooling, quality, cost and yield"),
                    new LensInfo("program", "Project & Program", "scope, schedule, cross-team delivery, vendors"),
                    new LensInfo("general", "General Engineering", "everything else")),
            List.of("CAD & Modeling", "Simulation & Analysis", "Manufacturing & Quality", "Lab & Tools", "Standards & Interests"),
            new Prompt(
                    "outside the design team would notice — mass, cost, safety factor,\ntolerance held, cycle time, yield or scrap rate, test pass rate",
                    "CAD files, drawings, models, revisions, simulations run, meetings attended, \"zero open ECOs\"",
                    "Designed · Analyzed ·\nValidated · Prototyped · Machined · Fabricated · Specified · Qualified · Commissioned · Reduced",
                    "Designed, Analyzed, Validated, Prototyped, Fabricated,\nSpecified, Qualified",
                    "part numbers, drawing numbers, PDM or file paths, internal project codes",
                    "\"solidworks\", \"ansys\",\n    \"gd&t\", \"dfmea\"",
                    "  ✗ Completed 120 CAD drawings across 40 revisions.\n"
                            + "    (activity counts, not a result)\n"
                            + "  ✗ Created a SolidWorks model of the bracket. This enabled rapid design iteration.\n"
                            + "    (artifact count, plus a filler second sentence)\n"
                            + "  ✗ Worked on mechanical stuff using various tools and got things better.\n"
                            + "    (passive opener, vague, no result)",
                    "routine drafting, picking standard parts from a catalog, updating a BOM, attending\n    design reviews",
                    "CAD files, drawings, models, revisions, simulations run, meetings",
                    "CAD files,\ndrawings, models, revisions, simulations run, meetings",
                    "Tools & methods",
                    "Use general when the role is not mainly design, testing, manufacturing or program work.",
                    MECHANICAL_VANITY));

    public static FieldPreset of(Field field) {
        return field == Field.MECHANICAL ? MECHANICAL : SOFTWARE;
    }

    /** Every preset a UI can offer, in the order it should list them. */
    public static List<FieldPreset> all() {
        return List.of(SOFTWARE, MECHANICAL);
    }
}
