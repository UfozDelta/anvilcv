package com.resumepipeline.github;

import java.util.List;

/**
 * Hierarchical picture of one repo at one commit: project -> subsystems -> modules -> symbols,
 * plus facts counted straight from the code. The deterministic parts come from
 * {@link RepoMapBuilder}; the summaries from {@link RepoMapService} (LLM, bottom-up).
 * Stored as JSON on the project and reused until the pinned commit changes.
 */
public record RepoMap(String sha, List<Fact> facts, List<Module> modules, ProjectSummary project) {

    /**
     * A number counted from the code, e.g. ("test cases", "352", "@Test / it( / test_ across 41 files").
     * Sourced by construction, so bullets can quote it — the Y in an XYZ bullet.
     */
    public record Fact(String label, String value, String source) {}

    /** One directory of code. {@code rank} is PageRank over cross-module symbol references. */
    public record Module(String path, int files, int loc, List<String> languages, double rank,
                         List<String> symbols, List<String> routes, List<String> dependsOn,
                         String topFile, String summary, String purpose) {
        public Module withSummary(String summary, String purpose) {
            return new Module(path, files, loc, languages, rank, symbols, routes, dependsOn, topFile, summary, purpose);
        }
    }

    public record ProjectSummary(String overview, String audience, List<Subsystem> subsystems, List<Flow> flows) {}

    /** A named group of modules. {@code lenses} are CategoryLenses slugs this subsystem feeds. */
    public record Subsystem(String name, String purpose, List<String> lenses, List<String> modules) {}

    /** An end-to-end path through the system, e.g. "upload -> parse -> rank -> render PDF". */
    public record Flow(String name, List<String> steps) {}

    public RepoMap withModules(List<Module> modules) {
        return new RepoMap(sha, facts, modules, project);
    }

    public RepoMap withProject(ProjectSummary project) {
        return new RepoMap(sha, facts, modules, project);
    }
}
