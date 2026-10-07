package com.resumepipeline.github;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/** Renders a {@link RepoMap} as prompt text for each consumer: explorer, shared generation context, per-lens focus. */
public final class RepoMapRenderer {

    private RepoMapRenderer() {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EXPLORER_MODULES = 30;
    private static final int FOCUS_SYMBOLS = 6;
    private static final int FALLBACK_MODULES = 3;

    public static RepoMap parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, RepoMap.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(RepoMap map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize repo map", e);
        }
    }

    /** First observation for the explorer: where to look, before it spends reads. */
    public static String forExplorer(RepoMap map) {
        StringBuilder sb = new StringBuilder();
        project(sb, map);
        facts(sb, map);
        sb.append("\nModules, most central first (rank = PageRank over cross-module references):\n");
        map.modules().stream().limit(EXPLORER_MODULES).forEach(m -> {
            sb.append("- ").append(m.path()).append("  [rank ").append(m.rank()).append(", ").append(m.files())
                    .append(" files, central file ").append(m.topFile()).append("]\n");
            if (m.summary() != null && !m.summary().isBlank()) sb.append("    ").append(m.summary()).append('\n');
            if (!m.routes().isEmpty()) sb.append("    routes: ").append(String.join(", ", m.routes().stream().limit(8).toList())).append('\n');
            if (!m.symbols().isEmpty()) sb.append("    symbols: ").append(String.join("; ", m.symbols().stream().limit(FOCUS_SYMBOLS).toList())).append('\n');
        });
        return sb.toString();
    }

    /**
     * Shared by every lens in a generation run — so it must not vary per lens (it sits in the
     * cacheable prompt prefix). The top of the hierarchy: what the project is, for whom, its
     * subsystems and flows, and the counted facts.
     */
    public static String overview(RepoMap map) {
        StringBuilder sb = new StringBuilder("REPO MAP (built from the code at ")
                .append(map.sha(), 0, Math.min(7, map.sha().length())).append(")\n");
        project(sb, map);
        facts(sb, map);
        if (map.project() != null && !map.project().flows().isEmpty()) {
            sb.append("\nKey flows:\n");
            for (RepoMap.Flow f : map.project().flows()) sb.append("- ").append(f.name()).append(": ").append(String.join(" -> ", f.steps())).append('\n');
        }
        return sb.toString();
    }

    /**
     * The per-lens slice: subsystems tagged with this lens (or the ones the user picked), each
     * with its modules' summaries, routes, and key symbols. Falls back to the top-ranked modules
     * when nothing is tagged, so a lens is never left with only the overview.
     */
    public static String lensFocus(RepoMap map, String lens, List<String> subsystemFilter) {
        return lensFocus(map, lens == null ? List.<String>of() : List.of(lens), subsystemFilter);
    }

    /** As above for several lenses at once: a subsystem tagged with any of them is in. */
    public static String lensFocus(RepoMap map, List<String> lenses, List<String> subsystemFilter) {
        if (map == null) return null;
        Map<String, RepoMap.Module> byPath = new HashMap<>();
        map.modules().forEach(m -> byPath.put(m.path(), m));
        List<RepoMap.Subsystem> subs = map.project() == null ? List.of() : map.project().subsystems();
        List<RepoMap.Subsystem> picked = subsystemFilter != null && !subsystemFilter.isEmpty()
                ? subs.stream().filter(s -> subsystemFilter.contains(s.name())).toList()
                : subs.stream().filter(s -> s.lenses().stream().anyMatch(lenses::contains)).toList();

        StringBuilder sb = new StringBuilder();
        if (picked.isEmpty()) {
            sb.append("No subsystem is tagged for this lens; the most central modules:\n");
            map.modules().stream().limit(FALLBACK_MODULES).forEach(m -> module(sb, m));
            return sb.toString();
        }
        for (RepoMap.Subsystem s : picked) {
            sb.append("### ").append(s.name()).append(" — ").append(s.purpose()).append('\n');
            for (String path : s.modules()) {
                RepoMap.Module m = byPath.get(path);
                if (m != null) module(sb, m);
            }
        }
        return sb.toString();
    }

    private static void module(StringBuilder sb, RepoMap.Module m) {
        sb.append("- module ").append(m.path()).append(" (").append(String.join("/", m.languages())).append(", ")
                .append(m.files()).append(" files, ").append(String.format("%,d", m.loc())).append(" lines)\n");
        if (m.summary() != null && !m.summary().isBlank()) sb.append("    what/how: ").append(m.summary()).append('\n');
        if (m.purpose() != null && !m.purpose().isBlank()) sb.append("    why: ").append(m.purpose()).append('\n');
        if (!m.routes().isEmpty()) sb.append("    endpoints (").append(m.routes().size()).append("): ")
                .append(String.join(", ", m.routes().stream().limit(8).toList())).append('\n');
        if (!m.symbols().isEmpty()) sb.append("    key symbols: ").append(String.join("; ", m.symbols().stream().limit(FOCUS_SYMBOLS).toList())).append('\n');
    }

    private static void project(StringBuilder sb, RepoMap map) {
        RepoMap.ProjectSummary p = map.project();
        if (p == null) return;
        if (!p.overview().isBlank()) sb.append("Overview: ").append(p.overview()).append('\n');
        if (!p.audience().isBlank()) sb.append("Used by: ").append(p.audience()).append('\n');
        if (!p.subsystems().isEmpty()) {
            sb.append("Subsystems:\n");
            for (RepoMap.Subsystem s : p.subsystems()) sb.append("- ").append(s.name()).append(": ").append(s.purpose()).append('\n');
        }
    }

    private static void facts(StringBuilder sb, RepoMap map) {
        if (map.facts().isEmpty()) return;
        // Not "safe to quote": that label is how test, file and line counts became bullet metrics.
        sb.append("\nCounted facts (computed from the code — accurate, but counts of the code itself are context, not results):\n");
        for (RepoMap.Fact f : map.facts()) sb.append("- ").append(f.value()).append(' ').append(f.label()).append(" (").append(f.source()).append(")\n");
    }
}
