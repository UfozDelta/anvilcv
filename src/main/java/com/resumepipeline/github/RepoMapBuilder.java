package com.resumepipeline.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The deterministic half of the repo map: no LLM. Extracts public symbols and routes per file
 * with per-language patterns (Aider-style skeleton, regex instead of tree-sitter), links modules
 * by cross-module symbol references, ranks them with PageRank so the code everything else leans
 * on comes first, and counts facts (tests, endpoints, migrations, dependencies) that bullets can
 * quote as sourced numbers.
 *
 * <p>Patterns are approximate by design: a missed symbol costs a little ranking signal, never a
 * wrong claim, because nothing here is shown as a fact except the counts, and each count names
 * what it counted.
 */
public final class RepoMapBuilder {

    private RepoMapBuilder() {}

    static final int MAX_MODULES = 200;
    static final int MAX_SYMBOLS = 20;
    private static final int PAGERANK_ITERATIONS = 30;
    private static final double DAMPING = 0.85;

    private static final Map<String, String> LANG = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("py", "Python"), Map.entry("go", "Go"),
            Map.entry("rs", "Rust"), Map.entry("cs", "C#"), Map.entry("rb", "Ruby"), Map.entry("php", "PHP"),
            Map.entry("swift", "Swift"), Map.entry("c", "C"), Map.entry("h", "C"), Map.entry("cpp", "C++"),
            Map.entry("hpp", "C++"), Map.entry("scala", "Scala"), Map.entry("sql", "SQL"));

    // ---- symbol patterns (MULTILINE) ----
    private static final Pattern JAVA_TYPE = Pattern.compile("^\\s*(?:public\\s+|protected\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+|static\\s+)*(class|interface|enum|record|object|data class)\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern JAVA_METHOD = Pattern.compile("^\\s*(?:public|protected)\\s+(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+|default\\s+)*[\\w<>\\[\\],.?\\s]+?\\s+(\\w+)\\s*\\([^)]*\\)", Pattern.MULTILINE);
    private static final Pattern JAVA_ROUTE = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern TS_EXPORT = Pattern.compile("^\\s*export\\s+(?:default\\s+)?(?:async\\s+)?(function|class|const|interface|type|enum)\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern TS_ROUTE = Pattern.compile("\\b(?:app|router|server)\\.(get|post|put|delete|patch)\\(\\s*['\"`](/[^'\"`]*)");
    private static final Pattern PY_DEF = Pattern.compile("^(?:async\\s+)?(def|class)\\s+([A-Za-z]\\w*)", Pattern.MULTILINE);
    private static final Pattern PY_ROUTE = Pattern.compile("@\\w+\\.(get|post|put|delete|patch|route)\\(\\s*['\"]([^'\"]+)");
    private static final Pattern GO_DEF = Pattern.compile("^(?:func\\s+(?:\\([^)]*\\)\\s*)?|type\\s+)([A-Z]\\w*)", Pattern.MULTILINE);
    private static final Pattern GENERIC_DEF = Pattern.compile("^\\s*(?:pub\\s+)?(?:fn|struct|trait|impl|def|class|module|function)\\s+([A-Za-z]\\w*)", Pattern.MULTILINE);

    private static final Pattern IDENT = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]{3,}\\b");
    private static final Pattern TEST_CASE = Pattern.compile("@Test\\b|^\\s*(?:it|test)\\(\\s*['\"`]|^\\s*(?:async\\s+)?def\\s+test_|^func\\s+Test[A-Z_]", Pattern.MULTILINE);
    private static final Set<String> COMMON = Set.of(
            "main", "init", "index", "test", "string", "value", "data", "type", "list", "get", "set",
            "name", "self", "this", "true", "false", "null", "none", "void", "props", "state", "config",
            "result", "error", "default", "handler", "request", "response", "utils", "helper");

    record FileInfo(String path, String module, String lang, int loc, List<String> symbols,
                    List<String> defNames, List<String> routes, boolean test) {}

    public static RepoMap build(String sha, RepoSnapshot snap) {
        List<FileInfo> infos = new ArrayList<>();
        for (var e : snap.files().entrySet()) {
            String lang = LANG.get(ext(e.getKey()));
            if (lang == null) continue;
            infos.add(analyze(e.getKey(), lang, e.getValue()));
        }

        // Symbol name -> modules defining it. Names defined in 3+ modules are too common to link on.
        Map<String, Set<String>> defs = new HashMap<>();
        for (FileInfo f : infos) {
            for (String d : f.defNames()) defs.computeIfAbsent(d, k -> new HashSet<>()).add(f.module());
        }
        defs.values().removeIf(s -> s.size() > 2);

        Map<String, Map<String, Integer>> edges = new HashMap<>(); // from -> to -> weight
        for (FileInfo f : infos) {
            Set<String> seen = new HashSet<>();
            Matcher m = IDENT.matcher(snap.read(f.path()));
            while (m.find()) {
                String id = m.group();
                if (!seen.add(id)) continue;
                Set<String> owners = defs.get(id);
                if (owners == null) continue;
                for (String to : owners) {
                    if (to.equals(f.module())) continue;
                    edges.computeIfAbsent(f.module(), k -> new HashMap<>()).merge(to, 1, Integer::sum);
                }
            }
        }

        Map<String, List<FileInfo>> byModule = infos.stream()
                .collect(Collectors.groupingBy(FileInfo::module, TreeMap::new, Collectors.toList()));
        Map<String, Double> rank = pageRank(byModule.keySet(), edges);

        List<RepoMap.Module> modules = new ArrayList<>();
        for (var e : byModule.entrySet()) {
            List<FileInfo> fs = e.getValue();
            FileInfo top = fs.stream().max(Comparator.comparingInt((FileInfo f) -> f.defNames().size()).thenComparingInt(FileInfo::loc)).orElseThrow();
            List<String> symbols = fs.stream().flatMap(f -> f.symbols().stream()).distinct().limit(MAX_SYMBOLS).toList();
            List<String> routes = fs.stream().flatMap(f -> f.routes().stream()).distinct().toList();
            List<String> deps = edges.getOrDefault(e.getKey(), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5).map(Map.Entry::getKey).toList();
            List<String> langs = fs.stream().map(FileInfo::lang).distinct().sorted().toList();
            modules.add(new RepoMap.Module(e.getKey(), fs.size(), fs.stream().mapToInt(FileInfo::loc).sum(),
                    langs, round(rank.getOrDefault(e.getKey(), 0.0)), symbols, routes, deps, top.path(), null, null));
        }
        modules.sort(Comparator.comparingDouble(RepoMap.Module::rank).reversed().thenComparing(RepoMap.Module::path));
        if (modules.size() > MAX_MODULES) modules = new ArrayList<>(modules.subList(0, MAX_MODULES));

        return new RepoMap(sha, facts(snap, infos), modules, null);
    }

    static FileInfo analyze(String path, String lang, String content) {
        List<String> symbols = new ArrayList<>();
        List<String> defNames = new ArrayList<>();
        List<String> routes = new ArrayList<>();
        switch (lang) {
            case "Java", "Kotlin", "C#", "Scala" -> {
                collect(JAVA_TYPE, content, 2, m -> m.group(1) + " " + m.group(2), symbols, defNames);
                collect(JAVA_METHOD, content, 1, m -> signature(m.group()), symbols, defNames);
                Matcher r = JAVA_ROUTE.matcher(content);
                while (r.find()) routes.add(r.group(1).toUpperCase(Locale.ROOT).replace("REQUEST", "ANY") + " " + r.group(2));
            }
            case "TypeScript", "JavaScript" -> {
                collect(TS_EXPORT, content, 2, m -> m.group(1) + " " + m.group(2), symbols, defNames);
                Matcher r = TS_ROUTE.matcher(content);
                while (r.find()) routes.add(r.group(1).toUpperCase(Locale.ROOT) + " " + r.group(2));
                if (path.matches(".*(^|/)app/.*route\\.(ts|js)$")) routes.add("ROUTE /" + path.replaceAll(".*app/", "").replaceAll("/?route\\.(ts|js)$", ""));
            }
            case "Python" -> {
                collect(PY_DEF, content, 2, m -> m.group(1) + " " + m.group(2), symbols, defNames);
                Matcher r = PY_ROUTE.matcher(content);
                while (r.find()) routes.add(r.group(1).toUpperCase(Locale.ROOT) + " " + r.group(2));
            }
            case "Go" -> collect(GO_DEF, content, 1, m -> signature(m.group()), symbols, defNames);
            default -> collect(GENERIC_DEF, content, 1, m -> signature(m.group()), symbols, defNames);
        }
        int slash = path.lastIndexOf('/');
        String module = slash < 0 ? "(root)" : path.substring(0, slash);
        return new FileInfo(path, module, lang, loc(content), symbols, defNames, routes, isTest(path));
    }

    private static void collect(Pattern p, String content, int nameGroup, java.util.function.Function<Matcher, String> label,
                                List<String> symbols, List<String> defNames) {
        Matcher m = p.matcher(content);
        while (m.find()) {
            String name = m.group(nameGroup);
            if (name == null) continue;
            symbols.add(label.apply(m));
            // Common names stay in the skeleton but never link modules — they collide everywhere.
            if (name.length() >= 4 && !COMMON.contains(name.toLowerCase(Locale.ROOT))) defNames.add(name);
        }
    }

    private static String signature(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 117) + "..." : one;
    }

    static Map<String, Double> pageRank(Set<String> nodes, Map<String, Map<String, Integer>> edges) {
        int n = nodes.size();
        Map<String, Double> r = new HashMap<>();
        if (n == 0) return r;
        for (String k : nodes) r.put(k, 1.0 / n);
        for (int it = 0; it < PAGERANK_ITERATIONS; it++) {
            Map<String, Double> next = new HashMap<>();
            for (String k : nodes) next.put(k, (1 - DAMPING) / n);
            double dangling = 0;
            for (String from : nodes) {
                Map<String, Integer> out = edges.getOrDefault(from, Map.of());
                int total = out.values().stream().mapToInt(Integer::intValue).sum();
                if (total == 0) { dangling += r.get(from); continue; }
                for (var e : out.entrySet()) {
                    if (next.containsKey(e.getKey())) next.merge(e.getKey(), DAMPING * r.get(from) * e.getValue() / total, Double::sum);
                }
            }
            double share = DAMPING * dangling / n;
            for (String k : nodes) next.merge(k, share, Double::sum);
            r = next;
        }
        return r;
    }

    // ---------------------------------------------------------------- counted facts

    static List<RepoMap.Fact> facts(RepoSnapshot snap, List<FileInfo> infos) {
        List<RepoMap.Fact> out = new ArrayList<>();

        Map<String, Integer> locByLang = new TreeMap<>();
        for (FileInfo f : infos) if (!f.lang().equals("SQL")) locByLang.merge(f.lang(), f.loc(), Integer::sum);
        int totalLoc = locByLang.values().stream().mapToInt(Integer::intValue).sum();
        if (totalLoc > 0) {
            String langs = locByLang.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3).map(e -> e.getKey() + " " + String.format("%,d", e.getValue())).collect(Collectors.joining(", "));
            out.add(new RepoMap.Fact("lines of code", String.format("%,d", totalLoc), "non-blank source lines (" + langs + ")"));
            out.add(new RepoMap.Fact("source files", String.valueOf(infos.stream().filter(f -> !f.test() && !f.lang().equals("SQL")).count()), "non-test source files"));
        }

        List<FileInfo> tests = infos.stream().filter(FileInfo::test).toList();
        int cases = 0;
        for (FileInfo t : tests) {
            Matcher m = TEST_CASE.matcher(snap.read(t.path()));
            while (m.find()) cases++;
        }
        if (cases > 0) out.add(new RepoMap.Fact("test cases", String.valueOf(cases), "@Test / it( / test( / def test_ / func Test across " + tests.size() + " test files"));

        long routes = infos.stream().mapToLong(f -> f.routes().size()).sum();
        if (routes > 0) out.add(new RepoMap.Fact("API endpoints", String.valueOf(routes), "route declarations (@*Mapping, app.get, @app.route, route.ts)"));

        long migrations = snap.files().keySet().stream()
                .filter(p -> p.matches(".*(^|/)(migrations?|migrate)/.*\\.(sql|py|rb|ts|js)$")).count();
        if (migrations > 0) out.add(new RepoMap.Fact("database migrations", String.valueOf(migrations), "files under migration directories"));

        int deps = dependencyCount(snap);
        if (deps > 0) out.add(new RepoMap.Fact("declared dependencies", String.valueOf(deps), "package.json / pom.xml / requirements.txt / go.mod / Cargo.toml"));

        long workflows = snap.files().keySet().stream().filter(p -> p.startsWith(".github/workflows/")).count();
        if (workflows > 0) out.add(new RepoMap.Fact("CI workflows", String.valueOf(workflows), ".github/workflows"));

        long dockerfiles = snap.files().keySet().stream().filter(p -> p.matches("(.*/)?Dockerfile.*|(.*/)?(docker-)?compose\\.ya?ml")).count();
        if (dockerfiles > 0) out.add(new RepoMap.Fact("container configs", String.valueOf(dockerfiles), "Dockerfile / compose files"));
        return out;
    }

    private static int dependencyCount(RepoSnapshot snap) {
        int n = 0;
        ObjectMapper json = new ObjectMapper();
        for (var e : snap.files().entrySet()) {
            String p = e.getKey(), name = p.substring(p.lastIndexOf('/') + 1), c = e.getValue();
            try {
                switch (name) {
                    case "package.json" -> {
                        JsonNode root = json.readTree(c);
                        n += root.path("dependencies").size() + root.path("devDependencies").size();
                    }
                    case "pom.xml" -> n += count(c, "<dependency>");
                    case "requirements.txt" -> n += (int) c.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#") && !l.startsWith("-")).count();
                    case "go.mod" -> n += (int) c.lines().map(String::trim).filter(l -> l.matches("[\\w.\\-/]+\\.[\\w.\\-/]+\\s+v\\S+.*")).count();
                    case "Cargo.toml" -> n += cargoDeps(c);
                    default -> {}
                }
            } catch (Exception ignored) {
                // Malformed manifest: count nothing from it rather than guess.
            }
        }
        return n;
    }

    private static int cargoDeps(String toml) {
        int n = 0;
        boolean in = false;
        for (String l : toml.lines().map(String::trim).toList()) {
            if (l.startsWith("[")) { in = l.matches("\\[(dev-)?dependencies]"); continue; }
            if (in && l.contains("=") && !l.startsWith("#")) n++;
        }
        return n;
    }

    private static int count(String s, String needle) {
        int n = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    static boolean isTest(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.matches(".*(^|/)(tests?|__tests__|spec)/.*") || p.matches(".*(test|tests|spec)\\.(java|kt|ts|tsx|js|jsx|cs)$")
                || p.matches(".*(^|/)test_[^/]*\\.py$") || p.matches(".*_test\\.(py|go)$");
    }

    private static int loc(String s) {
        return (int) s.lines().filter(l -> !l.isBlank()).count();
    }

    private static String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static double round(double d) {
        return Math.round(d * 10000) / 10000.0;
    }
}
