package com.resumepipeline.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.jd.JdFetcher;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.RoutingLlmClient;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.llm.settings.LlmSettingsService;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Manual speed/quality harness for the application pipeline's LLM stages. Runs every JD
 * through two arms with real provider calls and prints per-stage latency, tokens, and how
 * far arm B's output drifts from arm A's:
 *
 * <ul>
 *   <li>A "clean"   — cleanJd (model re-writes the JD) → rank ∥ fit → recruiter. Today's pipeline.
 *   <li>B "extract" — extractJd (model returns company/role/keywords only, raw JD passed on)
 *       → rank ∥ fit → recruiter.
 * </ul>
 *
 * <p>"wait now" is fetch + parse + rank + recruiter: today the recruiter pass is joined after
 * the PDF compile, and it outlasts the compile. "wait async" drops the recruiter, i.e. the
 * time to a PDF once the recruiter pass runs after the resume is returned. Compile time is
 * left out of both — it is identical across arms and needs tectonic.
 *
 * <p>Selection is approximated: the pre-filter is ApplicationService's keyword round-robin
 * (without variant collapsing) and the recruiter grades the top 8 ranked bullets rather than
 * BulletSelector's output. Both arms get the same approximation, so the comparison holds.
 *
 * <p>Off by default — it makes real LLM calls. Inputs:
 * <pre>
 *   PIPE_BENCH=1
 *   PIPE_PROVIDER=gemini|openai|opencode   PIPE_API_KEY=...   PIPE_BASE_URL=(openai/opencode only)
 *   PIPE_GEN_MODEL / PIPE_MATCH_MODEL / PIPE_CLEAN_MODEL   (defaults: gemini-2.5-flash / -flash / -flash-lite)
 *   PIPE_JDS=dir      *.txt = JD text; *.url = one URL per line, fetched with JdFetcher (fetch is timed)
 *   PIPE_BULLETS=file JSON array of {"id","text","tags":[...],"project"}
 *   PIPE_RUNS=1       repeats per JD per arm
 *   PIPE_PAUSE_MS=0   sleep between arm runs, to stay under free-tier per-minute quotas
 *   PIPE_ARMS=        arm name prefix to run alone, e.g. B
 * </pre>
 * Export bullets with:
 * <pre>
 *   psql ... -At -c "select json_agg(json_build_object('id',b.id,'text',b.text,'tags',b.tags,'project',p.name))
 *     from bullet b join project p on p.id=b.project_id where p.user_id='&lt;uuid&gt;'" &gt; bullets.json
 * </pre>
 * Run: {@code mvn test -Dtest=PipelineSpeedHarness}
 */
class PipelineSpeedHarness {

    private record BankBullet(String id, String text, List<String> tags, String project, Bullet entity) {}

    private record Jd(String name, String text, long fetchMs) {}

    private record ArmRun(long parseMs, long rankMs, long fitMs, long recruiterMs,
                          int inTok, int outTok, double costUsd,
                          List<String> keywords, List<String> top8, Integer fit, Integer recruiter) {
        long waitNow()   { return parseMs + Math.max(rankMs, fitMs) + recruiterMs; }
        long waitAsync() { return parseMs + Math.max(rankMs, fitMs); }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PIPE_BENCH", matches = "1")
    void compareArms() throws Exception {
        GenerationConfigService cfg = mock(GenerationConfigService.class);
        when(cfg.get(any())).thenReturn(new GenerationConfig());
        String flash = "gemini-2.5-flash";
        LlmClient llm = RoutingLlmClient.build(new LlmSettingsService.Resolved(
                env("PIPE_PROVIDER", "gemini"), System.getenv("PIPE_API_KEY"), System.getenv("PIPE_BASE_URL"),
                env("PIPE_GEN_MODEL", flash), env("PIPE_MATCH_MODEL", flash),
                env("PIPE_CLEAN_MODEL", "gemini-2.5-flash-lite")), cfg);

        List<BankBullet> bank = loadBullets(Path.of(System.getenv("PIPE_BULLETS")));
        List<Jd> jds = loadJds(Path.of(System.getenv("PIPE_JDS")));
        int runs = Integer.parseInt(env("PIPE_RUNS", "1"));
        long pauseMs = Long.parseLong(env("PIPE_PAUSE_MS", "0"));
        System.out.printf("%nBank: %d bullets across %d projects. JDs: %d. Runs per arm: %d.%n",
                bank.size(), bank.stream().map(BankBullet::project).distinct().count(), jds.size(), runs);

        Map<String, BiFunction<String, TokenAccumulator, LlmClient.JdCleanResult>> arms = new LinkedHashMap<>();
        arms.put("A clean", (jd, t) -> llm.cleanJd(jd, ProgressLog.noOp(), t));
        arms.put("B extract", (jd, t) -> llm.extractJd(jd, ProgressLog.noOp(), t));
        String only = env("PIPE_ARMS", "");   // e.g. "B" to run one arm and save quota
        if (!only.isBlank()) arms.keySet().removeIf(k -> !k.startsWith(only));

        Map<String, List<ArmRun>> all = new LinkedHashMap<>();
        arms.keySet().forEach(k -> all.put(k, new ArrayList<>()));

        System.out.printf("%n%-24s %-10s %7s %7s %7s %7s %7s %9s %9s %8s %8s %5s %5s %6s %6s%n",
                "JD", "arm", "fetch", "parse", "rank", "fit", "recr", "waitNow", "waitAsync",
                "inTok", "outTok", "fit", "recr", "kw∩A", "top8∩A");
        for (Jd jd : jds) {
            for (int r = 0; r < runs; r++) {
                ArmRun baseline = null;
                for (var arm : arms.entrySet()) {
                    Thread.sleep(pauseMs);
                    ArmRun run = runWithRetry(llm, arm.getValue(), jd.text(), bank);
                    if (run == null) {
                        System.out.printf("%-24s %-10s FAILED after retries%n", abbreviate(jd.name(), 24), arm.getKey());
                        continue;
                    }
                    all.get(arm.getKey()).add(run);
                    if (baseline == null) baseline = run;
                    System.out.printf("%-24s %-10s %7d %7d %7d %7d %7d %9d %9d %8d %8d %5s %5s %6.2f %6.2f%n",
                            abbreviate(jd.name(), 24), arm.getKey(), jd.fetchMs(), run.parseMs(), run.rankMs(),
                            run.fitMs(), run.recruiterMs(), jd.fetchMs() + run.waitNow(),
                            jd.fetchMs() + run.waitAsync(), run.inTok(), run.outTok(),
                            run.fit(), run.recruiter(),
                            jaccard(lower(baseline.keywords()), lower(run.keywords())),
                            jaccard(new HashSet<>(baseline.top8()), new HashSet<>(run.top8())));
                }
            }
        }

        System.out.printf("%nAverages (ms, excl. fetch)%n%-10s %7s %7s %7s %7s %9s %9s %8s %8s %9s %6s %6s%n",
                "arm", "parse", "rank", "fit", "recr", "waitNow", "waitAsync", "inTok", "outTok", "cost$", "fit", "recr");
        for (var e : all.entrySet()) {
            List<ArmRun> rs = e.getValue();
            System.out.printf("%-10s %7.0f %7.0f %7.0f %7.0f %9.0f %9.0f %8.0f %8.0f %9.4f %6.1f %6.1f%n",
                    e.getKey(), avg(rs, ArmRun::parseMs), avg(rs, ArmRun::rankMs), avg(rs, ArmRun::fitMs),
                    avg(rs, ArmRun::recruiterMs), avg(rs, ArmRun::waitNow), avg(rs, ArmRun::waitAsync),
                    avg(rs, ArmRun::inTok), avg(rs, ArmRun::outTok), avg(rs, ArmRun::costUsd),
                    avgOf(rs, ArmRun::fit), avgOf(rs, ArmRun::recruiter));
        }
    }

    /** Free-tier keys hit 429 (per-minute quota) and 503 (overload) often; back off and retry. */
    private ArmRun runWithRetry(LlmClient llm, BiFunction<String, TokenAccumulator, LlmClient.JdCleanResult> parse,
                                String jdText, List<BankBullet> bank) throws InterruptedException {
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                return runArm(llm, parse, jdText, bank);
            } catch (RuntimeException e) {
                System.out.println("  attempt " + attempt + " failed: " + abbreviate(String.valueOf(e.getMessage()), 90));
                Thread.sleep(60_000);
            }
        }
        return null;
    }

    private ArmRun runArm(LlmClient llm, BiFunction<String, TokenAccumulator, LlmClient.JdCleanResult> parse,
                          String jdText, List<BankBullet> bank) {
        TokenAccumulator tokens = new TokenAccumulator();
        long t0 = System.currentTimeMillis();
        LlmClient.JdCleanResult clean = parse.apply(jdText, tokens);
        long parseMs = System.currentTimeMillis() - t0;

        Set<String> kw = lower(clean.keywords());
        ToLongFunction<Bullet> score = KeywordScorer.score(kw);
        ToLongFunction<BankBullet> s = b -> score.applyAsLong(b.entity());
        List<BankBullet> candidates = bank.stream()
                .collect(Collectors.groupingBy(BankBullet::project)).values().stream()
                .flatMap(g -> g.stream().sorted(Comparator.comparingLong(s).reversed()).limit(4))
                .sorted(Comparator.comparingLong(s).reversed())
                .limit(25)
                .toList();

        List<LlmClient.ProjectSummary> projects = bank.stream().map(BankBullet::project).distinct()
                .map(p -> new LlmClient.ProjectSummary(p, "PROJECT", "", "", ""))
                .toList();
        long tFit = System.currentTimeMillis();
        CompletableFuture<long[]> fitF = CompletableFuture.supplyAsync(() -> {
            LlmClient.FitResult f = llm.scoreFit(new LlmClient.FitRequest(clean.cleanJd(), clean.company(),
                    clean.role(), clean.keywords(), null, List.of(), projects), ProgressLog.noOp(), tokens);
            return new long[] {System.currentTimeMillis() - tFit, f == null ? -1 : f.overall()};
        });

        long tRank = System.currentTimeMillis();
        LlmClient.RankResult rank = llm.rankBullets(new LlmClient.RankRequest(clean.cleanJd(), clean.company(),
                clean.role(), clean.keywords(), null,
                candidates.stream().map(b -> new LlmClient.BulletForMatch(b.id(), b.text(), b.tags(), b.project())).toList(),
                List.of(), List.of()), ProgressLog.noOp(), tokens);
        long rankMs = System.currentTimeMillis() - tRank;
        long[] fit = fitF.join();

        Map<String, BankBullet> byId = candidates.stream().collect(Collectors.toMap(BankBullet::id, b -> b));
        List<BankBullet> top8 = rank.rankedBullets().stream()
                .sorted(Comparator.comparingInt(LlmClient.RankedBullet::rank))
                .map(rb -> byId.get(rb.bulletId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(8)
                .toList();

        long tRec = System.currentTimeMillis();
        LlmClient.RecruiterResult rec = llm.reviewResume(new LlmClient.RecruiterRequest(clean.cleanJd(),
                clean.company(), clean.role(), clean.keywords(), null,
                top8.stream().map(b -> new LlmClient.RenderedBullet(b.id(), b.text(), b.project())).toList(),
                Map.of(), List.of()), ProgressLog.noOp(), tokens);
        long recMs = System.currentTimeMillis() - tRec;

        return new ArmRun(parseMs, rankMs, fit[0], recMs,
                tokens.getPromptTokens(), tokens.getCandidatesTokens(), tokens.getCostUsd().doubleValue(),
                clean.keywords(), top8.stream().map(BankBullet::id).toList(),
                fit[1] < 0 ? null : (int) fit[1], rec == null ? null : rec.overall());
    }

    private static List<BankBullet> loadBullets(Path file) throws Exception {
        List<BankBullet> out = new ArrayList<>();
        Map<String, UUID> projectIds = new LinkedHashMap<>();
        for (JsonNode n : new ObjectMapper().readTree(Files.readString(file))) {
            List<String> tags = new ArrayList<>();
            if (n.has("tags") && n.get("tags").isArray()) n.get("tags").forEach(t -> tags.add(t.asText()));
            String project = n.path("project").asText("");
            Bullet entity = new Bullet(projectIds.computeIfAbsent(project, p -> UUID.randomUUID()),
                    n.get("text").asText(), tags.toArray(new String[0]));
            out.add(new BankBullet(n.get("id").asText(), n.get("text").asText(), tags, project, entity));
        }
        return out;
    }

    private static List<Jd> loadJds(Path dir) throws Exception {
        JdFetcher fetcher = new JdFetcher();
        List<Jd> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir).sorted()) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(".txt")) {
                    out.add(new Jd(name, Files.readString(p), 0));
                } else if (name.endsWith(".url")) {
                    for (String url : Files.readAllLines(p)) {
                        if (url.isBlank()) continue;
                        long t = System.currentTimeMillis();
                        String text = fetcher.fetch(url.strip()).text();
                        out.add(new Jd(java.net.URI.create(url.strip()).getHost(), text, System.currentTimeMillis() - t));
                    }
                }
            }
        }
        return out;
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    private static Set<String> lower(List<String> xs) {
        return xs.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static double avg(List<ArmRun> rs, ToLongFunction<ArmRun> f) {
        return rs.stream().mapToLong(f).average().orElse(0);
    }

    private static double avg(List<ArmRun> rs, java.util.function.ToDoubleFunction<ArmRun> f) {
        return rs.stream().mapToDouble(f).average().orElse(0);
    }

    private static double avgOf(List<ArmRun> rs, Function<ArmRun, Integer> f) {
        return rs.stream().map(f).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(Double.NaN);
    }

    private static String abbreviate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
