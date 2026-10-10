package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.field.Field;
import com.resumepipeline.field.FieldPresets;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MULTI_FIELD_PLAN step 3: the lenses a prompt speaks in follow the user's field; software is untouched. */
class FieldLensesTest {

    // ---- the registry ----

    @Test void softwareLensesAreExactlyTheShippedFour() {
        assertEquals(List.of("ai-ml", "backend", "data", "general"), List.copyOf(CategoryLenses.lensesFor(Field.SOFTWARE).keySet()));
        assertSame(CategoryLenses.LENSES, CategoryLenses.lensesFor(Field.SOFTWARE));
        assertSame(CategoryLenses.LENSES, CategoryLenses.lensesFor(Field.OTHER));
        assertSame(CategoryLenses.LENSES, CategoryLenses.lensesFor(null));
    }

    @Test void everyPresetOffersExactlyTheLensesTheRegistryHas() {
        for (Field f : Field.values()) {
            assertEquals(f.preset().lensSlugs(), List.copyOf(CategoryLenses.lensesFor(f).keySet()), f.name());
        }
    }

    @Test void mechanicalLensesAreNotOfferedToSoftware() {
        assertEquals(List.of("design", "testing"), CategoryLenses.validate(List.of("design", "Testing", "backend"), Field.MECHANICAL));
        assertEquals(List.of("backend"), CategoryLenses.validate(List.of("design", "backend"), Field.SOFTWARE));
    }

    @Test void validateStillCapsAtTwo() {
        assertEquals(List.of("design", "testing"),
                CategoryLenses.validate(List.of("design", "testing", "manufacturing"), Field.MECHANICAL));
    }

    @Test void storedSlugsOfEveryFieldSurviveNormalizingButTyposDoNot() {
        assertEquals("design", CategoryLenses.normalize("design"));
        assertEquals("backend", CategoryLenses.normalize("backend"));
        assertEquals("general", CategoryLenses.normalize("frontend"));
    }

    @Test void generalReadsDifferentlyPerField() {
        String sw = CategoryLenses.lensFor("general", Field.SOFTWARE);
        String me = CategoryLenses.lensFor("general", Field.MECHANICAL);
        assertTrue(sw.contains("General Software Engineering"));
        assertTrue(me.contains("General Mechanical Engineering"));
        assertNull(CategoryLenses.lensFor("backend", Field.MECHANICAL));
    }

    // ---- the prompts ----

    private static OpenCodeLlmClient client(RestClient.Builder builder) {
        return new OpenCodeLlmClient(builder, "g", "m", "c", new GenerationConfigService(null) {
            @Override public GenerationConfig get(UUID userId) { return new GenerationConfig(); }
        });
    }

    private static final String JD_REPLY = """
            {"choices":[{"message":{"content":"{\\"cleanJd\\":\\"clean\\",\\"company\\":\\"Acme\\",\\"role\\":\\"Design Engineer\\",\\"keywords\\":[\\"SolidWorks\\"],\\"lenses\\":[\\"design\\",\\"bogus\\"]}"}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5}}
            """;

    @Test void mechanicalJdParseOffersAndAcceptsMechanicalLenses() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("design, testing, manufacturing, program, general")))
                .andExpect(content().string(not(containsString("ai-ml"))))
                .andRespond(withSuccess(JD_REPLY, MediaType.APPLICATION_JSON));

        LlmClient.JdCleanResult r = client(builder).cleanJd("raw", Field.MECHANICAL, ProgressLog.noOp(), new TokenAccumulator());

        assertEquals(List.of("design"), r.lenses());   // "bogus" dropped, "design" kept
        server.verify();
    }

    @Test void softwareJdParseIsUnchanged() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("ai-ml, backend, data, general")))
                .andExpect(content().string(not(containsString("manufacturing"))))
                .andRespond(withSuccess(JD_REPLY, MediaType.APPLICATION_JSON));

        LlmClient.JdCleanResult r = client(builder).cleanJd("raw", ProgressLog.noOp(), new TokenAccumulator());

        assertEquals(List.of(), r.lenses());   // "design" is not a software lens
        server.verify();
    }

    private static LlmClient.GenerateBulletsRequest source(Field field) {
        return new LlmClient.GenerateBulletsRequest(UUID.randomUUID(), LlmClient.SourceKind.PROJECT, "general", "Bracket",
                "Redesigned a mounting bracket.", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, field);
    }

    private static final String STORIES_REPLY = """
            {"choices":[{"message":{"content":"{\\"stories\\":[]}"}}],"usage":{"prompt_tokens":10,"completion_tokens":5}}
            """;

    @Test void mechanicalStoryPassDefinesMechanicalLensesOnly() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("LENS: Design & Analysis.")))
                .andExpect(content().string(not(containsString("LENS: Backend & Data Architecture."))))
                .andRespond(withSuccess(STORIES_REPLY, MediaType.APPLICATION_JSON));

        client(builder).findStories(new LlmClient.StoryRequest(source(Field.MECHANICAL), List.of("design")),
                ProgressLog.noOp(), new TokenAccumulator());
        server.verify();
    }

    @Test void softwareStoryPassDefinesSoftwareLensesOnly() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("LENS: Backend & Data Architecture.")))
                .andExpect(content().string(not(containsString("LENS: Design & Analysis."))))
                .andRespond(withSuccess(STORIES_REPLY, MediaType.APPLICATION_JSON));

        client(builder).findStories(new LlmClient.StoryRequest(source(Field.SOFTWARE), List.of("backend")),
                ProgressLog.noOp(), new TokenAccumulator());
        server.verify();
    }

    @Test void aRequestBuiltTheOldWayIsASoftwareRequest() {
        LlmClient.GenerateBulletsRequest old = new LlmClient.GenerateBulletsRequest(UUID.randomUUID(),
                LlmClient.SourceKind.PROJECT, "general", "P", "d", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of(), null);
        assertEquals(Field.SOFTWARE, old.field());
        assertEquals(Field.SOFTWARE, source(null).field());
        assertNotNull(FieldPresets.of(Field.MECHANICAL));
    }
}
