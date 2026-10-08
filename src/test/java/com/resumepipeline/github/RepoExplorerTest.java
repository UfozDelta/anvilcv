package com.resumepipeline.github;

import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmClient.EvidenceRef;
import com.resumepipeline.llm.LlmClient.ExploreStep;
import com.resumepipeline.llm.LlmClient.ExtractResult;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepoExplorerTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final RepoReader repo = mock(RepoReader.class);
    private static final RepoExplorer.Budget BIG = new RepoExplorer.Budget(20, 10, 100_000, 1_000_000, Duration.ofMinutes(1));
    private static final RepoExplorer.Steering NONE = new RepoExplorer.Steering(null, null, null, null);

    private static final String POOL = "spring:\n  datasource:\n    hikari:\n      maximum-pool-size: 5\n      max-lifetime: 240000\n";

    @BeforeEach
    void setUp() {
        when(repo.repo()).thenReturn("me/app");
        when(repo.sha()).thenReturn("abcdef1234");
        when(repo.tree()).thenReturn(new GithubClient.Tree(List.of(
                new GithubClient.TreeEntry("src/main/resources/application.yml", 90),
                new GithubClient.TreeEntry("node_modules/x/index.js", 10),
                new GithubClient.TreeEntry("secret/keys.txt", 10)), false));
        when(repo.read("src/main/resources/application.yml")).thenReturn(POOL);
        when(repo.log("")).thenReturn(List.of(new GithubClient.Commit("0123456789abcdef", "me", "2026-01-01", "fix: pool exhaustion\n\nbody")));
    }

    private RepoExplorer explorer(RepoExplorer.Budget b) {
        return new RepoExplorer(llm, "INSTRUCTIONS", b);
    }

    private static ExploreStep tool(String action, String path) {
        return new ExploreStep(action, path, null, "because", null);
    }

    private static ExtractResult result(List<String> category, String scale, List<EvidenceRef> evidence) {
        return new ExtractResult("app", "Spring Boot", "Overview.", "", "", scale, "", "", "", "", category, evidence);
    }

    @Test
    void runsToolLoopAndKeepsOnlyVerifiedOutput() {
        when(llm.exploreStep(any(), any(), any())).thenReturn(
                tool("read_file", "src/main/resources/application.yml"),
                tool("git_log", ""),
                new ExploreStep("finish", null, null, "done", result(List.of("backend", "Data"),
                        "Caps the Hikari pool at 5 connections. Serves 30K users daily.",
                        List.of(
                                new EvidenceRef("scaleImpact", "pool cap", "src/main/resources/application.yml", 4, 5, null),
                                new EvidenceRef("ownership", "fixed pool", null, 0, 0, "0123456"),
                                new EvidenceRef("techStack", "invented", "src/Never.java", 1, 3, null)))));

        RepoExplorer.Outcome out = explorer(BIG).explore(repo, NONE, ProgressLog.noOp(), new TokenAccumulator());

        assertEquals(List.of("backend", "data"), out.result().category()); // normalized to schema slugs
        // "30K users" never appeared in anything read -> sentence cut; the sourced one stays.
        assertEquals("Caps the Hikari pool at 5 connections.", out.result().scaleImpact());
        assertTrue(out.droppedClaims().stream().anyMatch(d -> d.contains("30K")));
        // Evidence text is re-sliced from the real file, commit resolved to its full sha, unread file dropped.
        assertEquals(2, out.evidence().size());
        assertEquals("      maximum-pool-size: 5\n      max-lifetime: 240000", out.evidence().get(0).text());
        assertEquals("0123456789abcdef", out.evidence().get(1).commit());
        assertTrue(out.droppedClaims().stream().anyMatch(d -> d.contains("src/Never.java")));
    }

    @Test
    void transcriptCarriesObservationsAndHidesNoiseAndExcludedPaths() {
        when(llm.exploreStep(any(), any(), any())).thenReturn(
                tool("read_file", "secret/keys.txt"),
                new ExploreStep("finish", null, null, "done", result(List.of("backend"), "", List.of())));
        RepoExplorer.Steering steer = new RepoExplorer.Steering(
                List.of("src/main/resources/application.yml"), List.of("secret/"), "Focus on the DB layer", List.of("data"));

        explorer(BIG).explore(repo, steer, ProgressLog.noOp(), new TokenAccumulator());

        verify(repo, never()).read("secret/keys.txt");
        ArgumentCaptor<LlmClient.ExploreStepRequest> req = ArgumentCaptor.forClass(LlmClient.ExploreStepRequest.class);
        verify(llm, atLeastOnce()).exploreStep(req.capture(), any(), any());
        LlmClient.ExploreStepRequest last = req.getValue();
        assertTrue(last.instructions().contains("Focus on the DB layer"));
        assertTrue(last.instructions().contains("Focus lenses: data"));
        assertTrue(last.transcript().contains("4|       maximum-pool-size: 5"), "pinned file pre-read with line numbers");
        assertTrue(last.transcript().contains("secret/keys.txt is excluded"));
        assertFalse(last.transcript().contains("node_modules"));
    }

    @Test
    void repoMapIsFirstObservationAndItsFactsCountAsSource() {
        when(llm.exploreStep(any(), any(), any())).thenReturn(
                new ExploreStep("finish", null, null, "done", result(List.of("backend"), "Backed by 352 test cases.", List.of())));

        RepoExplorer.Outcome out = explorer(BIG).explore(repo, NONE,
                "Counted facts:\n- 352 test cases (@Test across 41 files)\n", ProgressLog.noOp(), new TokenAccumulator());

        assertEquals("Backed by 352 test cases.", out.result().scaleImpact());
        ArgumentCaptor<LlmClient.ExploreStepRequest> req = ArgumentCaptor.forClass(LlmClient.ExploreStepRequest.class);
        verify(llm).exploreStep(req.capture(), any(), any());
        assertTrue(req.getValue().transcript().indexOf("repo_map") < req.getValue().transcript().indexOf("list_tree"));
    }

    @Test
    void budgetExhaustionForcesFinishThenGivesUp() {
        RepoExplorer.Budget tiny = new RepoExplorer.Budget(2, 10, 100_000, 1_000_000, Duration.ofMinutes(1));
        when(llm.exploreStep(any(), any(), any())).thenReturn(tool("list_tree", ""));

        assertThrows(IllegalStateException.class,
                () -> explorer(tiny).explore(repo, NONE, ProgressLog.noOp(), new TokenAccumulator()));
        // 2 budgeted steps + 1 forced-finish turn + 1 repair turn, then stop — never unbounded.
        verify(llm, times(4)).exploreStep(any(), any(), any());
    }

    @Test
    void fileBudgetRefusesFurtherReads() {
        RepoExplorer.Budget oneFile = new RepoExplorer.Budget(10, 1, 100_000, 1_000_000, Duration.ofMinutes(1));
        when(repo.read("b.txt")).thenReturn("b");
        when(llm.exploreStep(any(), any(), any())).thenReturn(
                tool("read_file", "src/main/resources/application.yml"),
                tool("read_file", "b.txt"),
                new ExploreStep("finish", null, null, "done", result(List.of("backend"), "", List.of())));

        explorer(oneFile).explore(repo, NONE, ProgressLog.noOp(), new TokenAccumulator());

        verify(repo, never()).read("b.txt");
    }

    @Test
    void invalidFinishGetsOneRepairTurn() {
        when(llm.exploreStep(any(), any(), any())).thenReturn(
                new ExploreStep("finish", null, null, "done", result(List.of("web-scale"), "", List.of())),
                new ExploreStep("finish", null, null, "done", result(List.of("general"), "", List.of())));

        RepoExplorer.Outcome out = explorer(BIG).explore(repo, NONE, ProgressLog.noOp(), new TokenAccumulator());

        assertEquals(List.of("general"), out.result().category());
    }

    @Test
    void schemaValidationMatchesContextExtractSchema() {
        assertTrue(RepoExplorer.validate(RepoExplorer.normalize(result(List.of("backend", "data"), "", null))).isEmpty());
        assertFalse(RepoExplorer.validate(RepoExplorer.normalize(result(List.of(), "", null))).isEmpty());
        assertFalse(RepoExplorer.validate(RepoExplorer.normalize(result(List.of("backend", "data", "comms"), "", null))).isEmpty());
        assertFalse(RepoExplorer.validate(RepoExplorer.normalize(result(List.of("blockchain"), "", null))).isEmpty());
        assertFalse(RepoExplorer.validate(null).isEmpty());
        // Null string keys normalize to "" (schema: required strings, empty allowed).
        ExtractResult nulls = new ExtractResult(null, null, null, null, null, null, null, null, null, null, List.of("backend"), null);
        assertEquals("", RepoExplorer.normalize(nulls).hardestProblem());
    }
}
