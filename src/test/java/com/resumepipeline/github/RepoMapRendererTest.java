package com.resumepipeline.github;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepoMapRendererTest {

    private static RepoMap.Module mod(String path, String summary) {
        return new RepoMap.Module(path, 2, 120, List.of("Java"), 0.3, List.of("class " + path.replace('/', '_')),
                List.of(), List.of(), path + "/A.java", summary, "why " + path);
    }

    private static final RepoMap MAP = new RepoMap("abcdef1234",
            List.of(new RepoMap.Fact("test cases", "352", "@Test across 41 files")),
            List.of(mod("src/auth", "BCrypt login with sessions."), mod("src/pipe", "Ranks bullets on virtual threads."), mod("src/misc", "Helpers.")),
            new RepoMap.ProjectSummary("Tailors resumes.", "job seekers",
                    List.of(new RepoMap.Subsystem("Auth", "Keeps accounts apart.", List.of("security"), List.of("src/auth")),
                            new RepoMap.Subsystem("Pipeline", "Tailors per JD.", List.of("backend", "ai-ml"), List.of("src/pipe"))),
                    List.of(new RepoMap.Flow("Tailor", List.of("paste JD", "rank", "render PDF")))));

    @Test
    void lensPicksTaggedSubsystemOnly() {
        String s = RepoMapRenderer.lensFocus(MAP, "security", null);
        assertTrue(s.contains("### Auth") && s.contains("BCrypt login"));
        assertFalse(s.contains("virtual threads"));
    }

    @Test
    void userPickedSubsystemsOverrideLensTags() {
        String s = RepoMapRenderer.lensFocus(MAP, "security", List.of("Pipeline"));
        assertTrue(s.contains("### Pipeline"));
        assertFalse(s.contains("BCrypt"));
    }

    @Test
    void untaggedLensFallsBackToCentralModules() {
        String s = RepoMapRenderer.lensFocus(MAP, "frontend", null);
        assertTrue(s.startsWith("No subsystem is tagged"));
        assertTrue(s.contains("src/auth") && s.contains("src/pipe"));
    }

    @Test
    void overviewIsLensIndependentAndCarriesFactsAndFlows() {
        String o = RepoMapRenderer.overview(MAP);
        assertTrue(o.contains("Tailors resumes.") && o.contains("352 test cases") && o.contains("paste JD -> rank -> render PDF"));
        assertFalse(o.contains("BCrypt"), "module detail belongs in the per-lens slice");
    }

    @Test
    void jsonRoundTrip() {
        assertEquals(MAP, RepoMapRenderer.parse(RepoMapRenderer.toJson(MAP)));
        assertNull(RepoMapRenderer.parse("not json"));
    }
}
