package com.resumepipeline.github;

import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepoMapServiceTest {

    private final LlmClient llm = mock(LlmClient.class);

    private static RepoSnapshot snap() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("src/core/JobStore.java", "public class JobStore { int ttl = 600; }");
        f.put("src/api/Api.java", "public class Api { JobStore s; @GetMapping(\"/jobs\") public void jobs() {} }");
        f.put("src/ui/View.java", "public class View { JobStore s; }");
        return new RepoSnapshot(f, false);
    }

    @Test
    void buildsBottomUpAndCutsUnsourcedNumbers() {
        when(llm.summarizeModule(any(), any(), any())).thenAnswer(i -> {
            LlmClient.ModuleSummaryRequest r = i.getArgument(0);
            if (r.modulePath().equals("src/ui")) throw new RuntimeException("provider hiccup");
            return new LlmClient.ModuleSummary("Keeps jobs for 600 seconds. Handles 50K jobs per day.", "Lets users watch progress.");
        });
        when(llm.summarizeProject(any(), any(), any())).thenReturn(new LlmClient.ProjectSummaryResult(
                "A job tracker with 3 modules. Serves 90% of traffic.", "developers",
                List.of(new LlmClient.SubsystemSummary("Jobs", "Tracks work.", List.of("Backend", "blockchain"), List.of("src/core", "src/api", "src/nope")),
                        new LlmClient.SubsystemSummary("Dup", "Claims core again.", List.of("systems"), List.of("src/core")),
                        new LlmClient.SubsystemSummary("UI", "Shows it.", List.of("frontend"), List.of("src/ui"))),
                List.of(new LlmClient.FlowSummary("Submit", List.of("API", "Jobs")))));

        RepoMap map = new RepoMapService(llm).build("app", "abcdef1", snap(), ProgressLog.noOp(), new TokenAccumulator());

        RepoMap.Module core = map.modules().stream().filter(m -> m.path().equals("src/core")).findFirst().orElseThrow();
        assertEquals("Keeps jobs for 600 seconds.", core.summary()); // "50K" appears nowhere in the code
        RepoMap.Module ui = map.modules().stream().filter(m -> m.path().equals("src/ui")).findFirst().orElseThrow();
        assertNull(ui.summary(), "a failed module leaves a gap, not a failed run");

        RepoMap.ProjectSummary p = map.project();
        assertFalse(p.overview().contains("90%")); // unit claim with no source: cut
        assertEquals(List.of("Jobs", "UI"), p.subsystems().stream().map(RepoMap.Subsystem::name).toList()); // Dup had nothing left
        assertEquals(List.of("backend"), p.subsystems().get(0).lenses());     // normalized, unknown slug dropped
        assertEquals(List.of("src/core", "src/api"), p.subsystems().get(0).modules()); // unknown path dropped
        assertEquals(1, p.flows().size());
    }

    @Test
    void projectPromptGetsFactsAndModuleSummaries() {
        when(llm.summarizeModule(any(), any(), any())).thenReturn(new LlmClient.ModuleSummary("Stores jobs.", "Progress."));
        when(llm.summarizeProject(any(), any(), any())).thenReturn(new LlmClient.ProjectSummaryResult("x", "y", List.of(), List.of()));

        new RepoMapService(llm).build("app", "abcdef1", snap(), ProgressLog.noOp(), new TokenAccumulator());

        verify(llm).summarizeProject(argThat(r -> r.modules().stream().anyMatch(m -> m.startsWith("src/core — Stores jobs."))
                && r.facts().stream().anyMatch(f -> f.contains("API endpoints"))
                && r.lenses().contains("general")), any(), any());
        verify(llm, times(3)).summarizeModule(any(), any(), any());
    }
}
