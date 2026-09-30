package com.resumepipeline.github;

import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Builds the full hierarchical repo map: the deterministic skeleton from {@link RepoMapBuilder},
 * then LLM summaries bottom-up — the top-ranked modules first (in parallel), then the project,
 * which groups modules into subsystems tagged with the lenses they feed and names the main flows.
 *
 * <p>Summaries are untrusted like everything else the model writes: any sentence quoting a number
 * absent from the repo and the counted facts is cut, subsystem module paths must exist, and lens
 * tags must be real slugs.
 */
@Service
public class RepoMapService {

    private static final Logger log = LoggerFactory.getLogger(RepoMapService.class);

    static final int SUMMARIZE_TOP = 15;
    private static final int PARALLEL = 4;
    private static final int CODE_CHARS = 6_000;
    private static final int README_CHARS = 4_000;
    private static final int MANIFEST_CHARS = 1_500;

    private final LlmClient llm;

    public RepoMapService(LlmClient llm) {
        this.llm = llm;
    }

    public RepoMap build(String projectName, String sha, RepoSnapshot snap, ProgressLog progress, TokenAccumulator tokens) {
        RepoMap map = RepoMapBuilder.build(sha, snap);
        progress.emit("Map: " + snap.files().size() + " files, " + map.modules().size() + " modules, "
                + map.facts().size() + " counted facts" + (snap.truncated() ? " (repo truncated)" : ""));
        String corpus = corpus(snap, map);

        List<RepoMap.Module> top = map.modules().subList(0, Math.min(SUMMARIZE_TOP, map.modules().size()));
        Map<String, LlmClient.ModuleSummary> summaries = summarizeModules(projectName, snap, top, progress, tokens);
        List<String> dropped = new ArrayList<>();
        List<RepoMap.Module> modules = new ArrayList<>();
        for (RepoMap.Module m : map.modules()) {
            LlmClient.ModuleSummary s = summaries.get(m.path());
            modules.add(s == null ? m : m.withSummary(
                    RepoExplorer.strip(nz(s.summary()), corpus, m.path(), dropped),
                    RepoExplorer.strip(nz(s.purpose()), corpus, m.path(), dropped)));
        }
        map = map.withModules(modules);

        progress.emit("Map: summarizing project...");
        LlmClient.ProjectSummaryResult p = llm.summarizeProject(new LlmClient.ProjectSummaryRequest(
                projectName, head(readme(snap), README_CHARS), manifests(snap),
                map.facts().stream().map(f -> f.value() + " " + f.label() + " — " + f.source()).toList(),
                modules.stream().filter(m -> m.summary() != null)
                        .map(m -> m.path() + " — " + m.summary() + " (" + nz(m.purpose()) + ")").toList(),
                List.copyOf(new TreeSet<>(RepoExplorer.CATEGORIES))), progress, tokens);
        map = map.withProject(clean(p, map, corpus, dropped));

        for (String d : dropped) progress.emit("cut: " + d);
        log.info("REPO_MAP sha={} files={} modules={} summarized={} subsystems={} cut={}", sha, snap.files().size(),
                modules.size(), summaries.size(), map.project().subsystems().size(), dropped.size());
        return map;
    }

    private Map<String, LlmClient.ModuleSummary> summarizeModules(String projectName, RepoSnapshot snap, List<RepoMap.Module> top,
                                                                  ProgressLog progress, TokenAccumulator tokens) {
        Map<String, LlmClient.ModuleSummary> out = new ConcurrentHashMap<>();
        Semaphore gate = new Semaphore(PARALLEL);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (RepoMap.Module m : top) {
                pool.submit(() -> {
                    try {
                        gate.acquire();
                        try {
                            List<String> files = snap.files().keySet().stream()
                                    .filter(p -> p.startsWith(m.path() + "/") && p.indexOf('/', m.path().length() + 1) < 0
                                            || m.path().equals("(root)") && p.indexOf('/') < 0)
                                    .map(p -> p.substring(p.lastIndexOf('/') + 1)).limit(30).toList();
                            out.put(m.path(), llm.summarizeModule(new LlmClient.ModuleSummaryRequest(projectName, m.path(), files,
                                    m.symbols(), m.routes(), m.dependsOn(), head(snap.read(m.topFile()), CODE_CHARS)), progress, tokens));
                            progress.emit("Map: summarized " + m.path());
                        } finally {
                            gate.release();
                        }
                    } catch (Exception e) {
                        // One bad module leaves a gap in the map, not a failed run.
                        log.warn("REPO_MAP module={} failed: {}", m.path(), e.getMessage());
                        progress.emit("Map: skipped " + m.path() + " (" + e.getMessage() + ")");
                    }
                    return null;
                });
            }
        }
        return out;
    }

    static RepoMap.ProjectSummary clean(LlmClient.ProjectSummaryResult p, RepoMap map, String corpus, List<String> dropped) {
        if (p == null) return new RepoMap.ProjectSummary("", "", List.of(), List.of());
        Set<String> paths = new HashSet<>();
        map.modules().forEach(m -> paths.add(m.path()));
        Set<String> claimed = new HashSet<>();
        List<RepoMap.Subsystem> subs = new ArrayList<>();
        for (LlmClient.SubsystemSummary s : p.subsystems() == null ? List.<LlmClient.SubsystemSummary>of() : p.subsystems()) {
            if (s == null || s.name() == null || s.name().isBlank()) continue;
            List<String> mods = s.modules() == null ? List.of()
                    : s.modules().stream().filter(paths::contains).filter(claimed::add).toList();
            List<String> lenses = s.lenses() == null ? List.of()
                    : s.lenses().stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).filter(RepoExplorer.CATEGORIES::contains).distinct().toList();
            if (mods.isEmpty()) continue;
            subs.add(new RepoMap.Subsystem(s.name().trim(), RepoExplorer.strip(nz(s.purpose()), corpus, s.name(), dropped), lenses, mods));
        }
        List<RepoMap.Flow> flows = new ArrayList<>();
        for (LlmClient.FlowSummary f : p.flows() == null ? List.<LlmClient.FlowSummary>of() : p.flows()) {
            if (f == null || f.name() == null || f.steps() == null || f.steps().isEmpty()) continue;
            flows.add(new RepoMap.Flow(f.name().trim(), f.steps().stream().filter(Objects::nonNull).map(String::trim).toList()));
        }
        return new RepoMap.ProjectSummary(RepoExplorer.strip(nz(p.overview()), corpus, "overview", dropped),
                nz(p.audience()).trim(), subs, flows);
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d[\\d,]*");

    /**
     * What summaries may quote numbers from: the code plus the counted facts, reduced to their
     * distinct numbers once. The number check only reads digit runs, and rescanning megabytes of
     * source for every summary sentence would be pure waste.
     */
    static String corpus(RepoSnapshot snap, RepoMap map) {
        Set<String> nums = new HashSet<>();
        for (String v : snap.files().values()) {
            java.util.regex.Matcher m = NUMBER.matcher(v);
            while (m.find()) nums.add(m.group());
        }
        map.facts().forEach(f -> nums.add(f.value()));
        return String.join(" ", nums);
    }

    private static String readme(RepoSnapshot snap) {
        return snap.files().entrySet().stream()
                .filter(e -> e.getKey().matches("(?i)readme(\\.\\w+)?"))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    private static String manifests(RepoSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        for (String name : List.of("package.json", "pom.xml", "pyproject.toml", "Cargo.toml", "go.mod", "build.gradle", "build.gradle.kts")) {
            String c = snap.read(name);
            if (c != null) sb.append("--- ").append(name).append('\n').append(head(c, MANIFEST_CHARS)).append('\n');
        }
        return sb.toString();
    }

    private static String head(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "\n... (truncated)";
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
