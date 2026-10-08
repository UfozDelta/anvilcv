package com.resumepipeline.github;

import com.resumepipeline.llm.BulletTextRules;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmClient.EvidenceRef;
import com.resumepipeline.llm.LlmClient.ExploreStep;
import com.resumepipeline.llm.LlmClient.ExtractResult;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Server-side port of the anvilcv-context-mcp extractor: an LLM tool loop over one repo at one
 * commit. The model picks tools; this class runs them against {@link RepoReader}, keeps the
 * transcript, and enforces the budgets. The model never touches GitHub directly.
 *
 * <p>Everything the model returns is treated as untrusted and re-checked against what was
 * actually observed this run: evidence citing an unread file or unseen commit is dropped,
 * evidence spans are re-sliced from the real file (never the model's copy), and any sentence
 * quoting a number that appears nowhere in the observations is deleted — the same
 * {@link BulletTextRules#fabricatedNumbers} gate bullet generation uses.
 */
@Service
public class RepoExplorer {

    private static final Logger log = LoggerFactory.getLogger(RepoExplorer.class);

    public static final Set<String> CATEGORIES = Set.copyOf(CategoryLenses.LENSES.keySet());

    private static final int MAX_READ_CHARS = 12_000;
    private static final int MAX_TREE_LINES = 400;
    private static final int MAX_EVIDENCE_LINES = 15;
    private static final int MAX_REPAIRS = 1;

    public record Budget(int maxSteps, int maxFilesRead, int maxTranscriptChars, int maxPromptTokens, Duration maxDuration) {
        public static final Budget DEFAULT = new Budget(40, 30, 120_000, 1_000_000, Duration.ofMinutes(8));
    }

    public record Steering(List<String> pinPaths, List<String> excludePaths, String notes, List<String> lenses) {
        public Steering {
            pinPaths = pinPaths == null ? List.of() : pinPaths;
            excludePaths = excludePaths == null ? List.of() : excludePaths;
            lenses = lenses == null ? List.of() : lenses;
        }
    }

    /** A verified citation. {@code text} is sliced from the real file or commit, never taken from the model. */
    public record Evidence(String field, String claim, String path, int startLine, int endLine, String commit, String text) {}

    public record Outcome(ExtractResult result, List<Evidence> evidence, List<String> droppedClaims, int steps) {}

    private final LlmClient llm;
    private final String instructions;
    private final Budget budget;

    @Autowired
    public RepoExplorer(LlmClient llm) {
        this(llm, loadInstructions(), Budget.DEFAULT);
    }

    RepoExplorer(LlmClient llm, String instructions, Budget budget) {
        this.llm = llm;
        this.instructions = instructions;
        this.budget = budget;
    }

    private static String loadInstructions() {
        try (var in = new ClassPathResource("repo-explorer-instructions.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("repo-explorer-instructions.md missing from classpath", e);
        }
    }

    /** Mutable per-run state. */
    private static final class Run {
        final Map<String, String> files = new LinkedHashMap<>();   // path -> full content
        final Map<String, String> commits = new LinkedHashMap<>(); // sha -> "sha author date subject"
        // Raw source text seen this run, for the number check. Not the transcript: its line
        // numbers and file sizes would vouch for any small number.
        final StringBuilder observed = new StringBuilder();
        final StringBuilder transcript = new StringBuilder();
        List<GithubClient.TreeEntry> tree;
    }

    public Outcome explore(RepoReader repo, Steering steering, ProgressLog progress, TokenAccumulator tokens) {
        return explore(repo, steering, null, progress, tokens);
    }

    /**
     * @param mapText the rendered repo map, shown as the first observation so the explorer spends
     *                its reads on the central modules instead of discovering them. Its counted
     *                facts and number-checked summaries count as observed source.
     */
    public Outcome explore(RepoReader repo, Steering steering, String mapText, ProgressLog progress, TokenAccumulator tokens) {
        Run run = new Run();
        Instant deadline = Instant.now().plus(budget.maxDuration());
        String prompt = instructions + steeringBlock(steering);

        if (mapText != null && !mapText.isBlank()) {
            observe(run, "repo_map (prebuilt by AnvilCV)", mapText);
            run.observed.append(mapText).append('\n');
        }
        observe(run, "list_tree \"\"", listTree(run, repo, steering, ""));
        for (String pin : steering.pinPaths()) {
            observe(run, "read_file " + pin + " (pinned by user)", readFile(run, repo, steering, pin));
        }

        int exhaustedAt = 0; // step the budget ran out at; 0 = not yet
        int repairs = 0;
        for (int step = 1; ; step++) {
            boolean exhausted = exhaustedAt > 0;
            if (!exhausted && overBudget(run, step, tokens, deadline)) {
                exhaustedAt = step;
                exhausted = true;
                run.transcript.append("\nBUDGET EXHAUSTED — call finish now with what you have verified.\n");
                progress.emit("Explore: budget reached, asking for final answer...");
            } else if (exhausted && step >= exhaustedAt + 1 + MAX_REPAIRS) {
                throw new IllegalStateException("Explorer did not finish within its budget");
            }

            ExploreStep s = llm.exploreStep(new LlmClient.ExploreStepRequest(prompt, run.transcript.toString()), progress, tokens);
            String action = s == null || s.action() == null ? "" : s.action().trim();
            progress.emit("Explore " + step + ": " + action + (s != null && s.path() != null && !s.path().isBlank() ? " " + s.path() : "")
                    + (s != null && s.query() != null && !s.query().isBlank() ? " \"" + s.query() + "\"" : ""));

            if (action.equals("finish")) {
                ExtractResult normalized = normalize(s.result());
                List<String> errors = validate(normalized);
                if (errors.isEmpty()) {
                    log.info("REPO_EXPLORE repo={} sha={} steps={} files={}", repo.repo(), repo.sha(), step, run.files.size());
                    return verify(run, normalized, step);
                }
                if (repairs++ >= MAX_REPAIRS) throw new IllegalStateException("Explorer output invalid: " + String.join("; ", errors));
                run.transcript.append("\nFINISH REJECTED: ").append(String.join("; ", errors))
                        .append(". Call finish again with a corrected result.\n");
                continue;
            }
            if (exhausted) {
                run.transcript.append("\nBUDGET EXHAUSTED — the only allowed action is finish.\n");
                continue;
            }
            String call = action + " " + (s.path() != null ? s.path() : "") + (s.query() != null ? s.query() : "");
            observe(run, call.trim(), runTool(run, repo, steering, s));
        }
    }

    private boolean overBudget(Run run, int step, TokenAccumulator tokens, Instant deadline) {
        return step > budget.maxSteps()
                || run.transcript.length() > budget.maxTranscriptChars()
                || (tokens != null && tokens.getPromptTokens() > budget.maxPromptTokens())
                || Instant.now().isAfter(deadline);
    }

    private String runTool(Run run, RepoReader repo, Steering steering, ExploreStep s) {
        String path = s.path() == null ? "" : s.path().trim();
        return switch (s.action().trim()) {
            case "list_tree" -> listTree(run, repo, steering, path);
            case "read_file" -> run.files.size() >= budget.maxFilesRead() && !run.files.containsKey(path)
                    ? "ERROR: file budget (" + budget.maxFilesRead() + " files) used up — finish with what you have."
                    : readFile(run, repo, steering, path);
            case "search_code" -> search(run, repo, steering, s.query());
            case "git_log" -> gitLog(run, repo, steering, path);
            default -> "ERROR: unknown action \"" + s.action() + "\". Use list_tree, read_file, search_code, git_log, or finish.";
        };
    }

    private void observe(Run run, String call, String output) {
        run.transcript.append("\nCALL: ").append(call).append("\nOBSERVATION:\n").append(output).append('\n');
    }

    // ---------------------------------------------------------------- tools

    private String listTree(Run run, RepoReader repo, Steering steering, String prefix) {
        try {
            if (run.tree == null) {
                GithubClient.Tree t = github(() -> repo.tree());
                run.tree = t.entries().stream().filter(e -> allowed(steering, e.path())).toList();
                run.tree.forEach(e -> run.observed.append(e.path()).append('\n'));
            }
            String p = prefix.replaceAll("^/+", "");
            List<String> lines = run.tree.stream().filter(e -> e.path().startsWith(p))
                    .map(e -> e.path() + "  (" + e.size() + " B)").toList();
            if (lines.isEmpty()) return "(no files under \"" + p + "\")";
            String more = lines.size() > MAX_TREE_LINES
                    ? "\n... " + (lines.size() - MAX_TREE_LINES) + " more — list_tree a subdirectory" : "";
            return String.join("\n", lines.subList(0, Math.min(lines.size(), MAX_TREE_LINES))) + more;
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String readFile(Run run, RepoReader repo, Steering steering, String path) {
        String p = path.replaceAll("^/+", "");
        if (!allowed(steering, p)) return "ERROR: " + p + " is excluded (vendored, lockfile, binary, or excluded by the user).";
        try {
            String body = run.files.containsKey(p) ? run.files.get(p) : github(() -> repo.read(p));
            if (body == null) return "ERROR: empty file";
            if (body.length() > GithubService.MAX_FILE_BYTES) return "ERROR: file too large (" + body.length() + " chars)";
            if (run.files.put(p, body) == null) run.observed.append(body).append('\n');
            String[] lines = body.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length && sb.length() < MAX_READ_CHARS; i++) {
                sb.append(i + 1).append("| ").append(lines[i]).append('\n');
            }
            if (sb.length() >= MAX_READ_CHARS) sb.append("... truncated (").append(lines.length).append(" lines total)\n");
            return sb.toString();
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String search(Run run, RepoReader repo, Steering steering, String query) {
        if (query == null || query.isBlank()) return "ERROR: search_code needs a query";
        try {
            List<GithubClient.SearchHit> hits = github(() -> repo.search(query)).stream()
                    .filter(h -> allowed(steering, h.path())).toList();
            if (hits.isEmpty()) return "(no matches)";
            StringBuilder sb = new StringBuilder();
            for (GithubClient.SearchHit h : hits) {
                sb.append(h.path()).append('\n');
                for (String f : h.fragments()) {
                    sb.append("    ").append(f.replace("\n", "\n    ")).append('\n');
                    run.observed.append(f).append('\n');
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String gitLog(Run run, RepoReader repo, Steering steering, String path) {
        if (!path.isEmpty() && !allowed(steering, path)) return "ERROR: " + path + " is excluded.";
        try {
            List<GithubClient.Commit> commits = github(() -> repo.log(path));
            if (commits.isEmpty()) return "(no commits)";
            StringBuilder sb = new StringBuilder();
            for (GithubClient.Commit c : commits) {
                String subject = c.message().lines().findFirst().orElse("");
                String line = c.sha().substring(0, Math.min(10, c.sha().length())) + " " + c.author() + " " + c.date() + " " + subject;
                if (run.commits.put(c.sha(), line) == null) run.observed.append(c.message()).append('\n');
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** One wait-and-retry on a short rate limit; longer ones go back to the model as an error. */
    private static <T> T github(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (GithubException.RateLimited e) {
            if (e.resetSeconds() > 20) throw e;
            try {
                Thread.sleep(e.resetSeconds() * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return call.get();
        } catch (RestClientResponseException e) {
            throw new GithubException(e.getStatusCode().value() == 404 ? "not found" : "GitHub error " + e.getStatusCode().value());
        }
    }

    static boolean allowed(Steering steering, String path) {
        if (RepoFilter.isNoise(path)) return false;
        for (String ex : steering.excludePaths()) {
            String e = ex.replaceAll("^/+", "").replaceAll("/+$", "");
            if (!e.isEmpty() && (path.equals(e) || path.startsWith(e + "/"))) return false;
        }
        return true;
    }

    private static String steeringBlock(Steering s) {
        StringBuilder sb = new StringBuilder("\n\n## User steering for this run\n\n");
        boolean any = false;
        if (!s.lenses().isEmpty()) { sb.append("Focus lenses: ").append(String.join(", ", s.lenses())).append('\n'); any = true; }
        if (s.notes() != null && !s.notes().isBlank()) { sb.append("Notes from the author (context, not evidence — still cite code): ").append(s.notes().trim()).append('\n'); any = true; }
        if (!s.pinPaths().isEmpty()) { sb.append("Pinned files (already read below): ").append(String.join(", ", s.pinPaths())).append('\n'); any = true; }
        if (!s.excludePaths().isEmpty()) { sb.append("Excluded paths (refused): ").append(String.join(", ", s.excludePaths())).append('\n'); any = true; }
        return any ? sb.toString() : "";
    }

    // ---------------------------------------------------------------- output checks

    static ExtractResult normalize(ExtractResult r) {
        if (r == null) return null;
        List<String> cats = r.category() == null ? List.of()
                : r.category().stream().filter(Objects::nonNull).map(c -> c.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        return new ExtractResult(nz(r.name()), nz(r.techStack()), nz(r.description()), nz(r.yourRole()), nz(r.ownership()),
                nz(r.scaleImpact()), nz(r.hardestProblem()), nz(r.technicalDecisions()), nz(r.userImpact()),
                nz(r.securityPosture()), cats, r.evidence() == null ? List.of() : r.evidence());
    }

    /** The schema.json constraints: category is 1-2 known slugs. String keys are normalized, not rejected. */
    static List<String> validate(ExtractResult r) {
        if (r == null) return List.of("finish had no result");
        List<String> errors = new ArrayList<>();
        if (r.category().isEmpty() || r.category().size() > 2) errors.add("category must have 1-2 entries");
        for (String c : r.category()) if (!CATEGORIES.contains(c)) errors.add("unknown category \"" + c + "\"");
        return errors;
    }

    private Outcome verify(Run run, ExtractResult r, int steps) {
        List<Evidence> evidence = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (EvidenceRef ref : r.evidence()) {
            if (ref == null) continue;
            Evidence e = resolve(run, ref);
            if (e == null) dropped.add("evidence " + describe(ref) + " — not something read this run");
            else evidence.add(e);
        }

        String corpus = run.observed.toString();
        ExtractResult clean = new ExtractResult(
                strip(r.name(), corpus, "name", dropped),
                strip(r.techStack(), corpus, "techStack", dropped),
                strip(r.description(), corpus, "description", dropped),
                strip(r.yourRole(), corpus, "yourRole", dropped),
                strip(r.ownership(), corpus, "ownership", dropped),
                strip(r.scaleImpact(), corpus, "scaleImpact", dropped),
                strip(r.hardestProblem(), corpus, "hardestProblem", dropped),
                strip(r.technicalDecisions(), corpus, "technicalDecisions", dropped),
                strip(r.userImpact(), corpus, "userImpact", dropped),
                strip(r.securityPosture(), corpus, "securityPosture", dropped),
                r.category(), r.evidence());
        return new Outcome(clean, evidence, dropped, steps);
    }

    private static Evidence resolve(Run run, EvidenceRef ref) {
        if (ref.commit() != null && !ref.commit().isBlank()) {
            String c = ref.commit().trim();
            return run.commits.entrySet().stream()
                    .filter(e -> c.length() >= 7 && e.getKey().startsWith(c))
                    .findFirst()
                    .map(e -> new Evidence(ref.field(), ref.claim(), null, 0, 0, e.getKey(), e.getValue()))
                    .orElse(null);
        }
        if (ref.path() == null) return null;
        String content = run.files.get(ref.path().replaceAll("^/+", ""));
        if (content == null) return null;
        String[] lines = content.split("\n", -1);
        int start = ref.startLine(), end = Math.max(ref.startLine(), ref.endLine());
        if (start < 1 || start > lines.length) return null;
        end = Math.min(Math.min(end, lines.length), start + MAX_EVIDENCE_LINES - 1);
        String text = String.join("\n", Arrays.copyOfRange(lines, start - 1, end));
        return new Evidence(ref.field(), ref.claim(), ref.path().replaceAll("^/+", ""), start, end, null, text);
    }

    /** Drops each sentence that quotes a number found nowhere in the observations. */
    static String strip(String field, String corpus, String name, List<String> dropped) {
        if (field.isBlank()) return field;
        StringBuilder out = new StringBuilder();
        for (String sentence : field.split("(?<=[.!?])\\s+(?=[A-Z*`#])|(?<=\n)")) {
            List<String> fab = BulletTextRules.fabricatedNumbers(sentence, corpus);
            if (fab.isEmpty()) {
                out.append(sentence);
                if (!sentence.endsWith("\n")) out.append(' ');
            } else {
                dropped.add(name + ": \"" + sentence.trim() + "\" — unsourced " + fab);
            }
        }
        return out.toString().trim();
    }

    private static String describe(EvidenceRef r) {
        return r.commit() != null && !r.commit().isBlank() ? r.commit() : r.path() + ":" + r.startLine() + "-" + r.endLine();
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
