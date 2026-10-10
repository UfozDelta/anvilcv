package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.field.Field;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * MULTI_FIELD_PLAN step 5 guard: the software prompts are pinned byte for byte, so moving wording into
 * a field preset can never change what a software user's model is told. Run once with
 * {@code GOLDEN_WRITE=1} to (re)write the snapshots after an intended change, and read the diff.
 */
class PromptGoldenTest {

    private static final Path DIR = Path.of("src", "test", "resources", "golden");

    private static void golden(String name, String actual) throws IOException {
        Path file = DIR.resolve(name + ".txt");
        if (System.getenv("GOLDEN_WRITE") != null) {
            Files.createDirectories(DIR);
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(file), "missing golden " + file + " (run once with GOLDEN_WRITE=1)");
        assertEquals(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual.replace("\r\n", "\n"),
                "prompt drifted from " + file);
    }

    private static final String ANY_REPLY = """
            {"choices":[{"message":{"content":"{\\"bullets\\":[],\\"stories\\":[],\\"company\\":\\"c\\",\\"role\\":\\"r\\",\\"keywords\\":[],\\"lenses\\":[],\\"cleanJd\\":\\"j\\"}"}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5}}
            """;

    /** The messages' text, in order. The request JSON's key order varies per JVM, the prompt text does not. */
    private static String promptsOf(byte[] json) {
        try {
            StringBuilder sb = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode m : new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("messages")) {
                sb.append("=== ").append(m.get("role").asText()).append(" ===").append((char) 10)
                  .append(withoutSchemaLine(m.get("content").asText())).append((char) 10);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The system message ends with the reply's JSON shape, whose key order is a Map.of() per-JVM accident. */
    private static String withoutSchemaLine(String content) {
        return java.util.Arrays.stream(content.split("\n", -1)).filter(l -> !l.startsWith("{")).collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Runs {@code call} against a client whose only request body is captured. */
    private static String capture(java.util.function.Consumer<OpenCodeLlmClient> call) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicReference<String> body = new AtomicReference<>();
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(r -> body.set(promptsOf(((MockClientHttpRequest) r).getBodyAsBytes())))
                .andRespond(withSuccess(ANY_REPLY, MediaType.APPLICATION_JSON));
        GenerationConfig cfg = new GenerationConfig();
        cfg.setWordFilterEnabled(false);
        call.accept(new OpenCodeLlmClient(builder, "g", "m", "c", new GenerationConfigService(null) {
            @Override public GenerationConfig get(UUID userId) { return cfg; }
        }));
        return body.get();
    }

    private static LlmClient.GenerateBulletsRequest req(Field field, LlmClient.SourceKind kind) {
        return new LlmClient.GenerateBulletsRequest(new UUID(0, 1), kind, "backend", "Ledger",
                "Settles payouts nightly.", "Architecture notes.", null, "Java 21, PostgreSQL", "Owner", "The settlement job",
                "4 regions", "Idempotent retries", "Chose batch over streaming", "Finance team", "SOC 2",
                "Engineer", "Acme", "NYC", "2024", List.of("Existing bullet."), List.of("data"), null, field);
    }

    private static final LlmClient.Story STORY = new LlmClient.Story("s1", "Nightly settlement", List.of("Settles payouts nightly."), List.of("backend"));

    @Test void writingRules() throws IOException {
        golden("software-writingRules", BaseLlmClient.writingRules(new GenerationConfig()));
    }

    @Test void recoveryNote() throws IOException {
        List<BaseLlmClient.Reject> rejects = List.of(
                new BaseLlmClient.Reject("A very long bullet.", BaseLlmClient.RejectReason.LENGTH),
                new BaseLlmClient.Reject("Worked on the thing.", BaseLlmClient.RejectReason.OPENER),
                new BaseLlmClient.Reject("Shipped it in 120 commits.", BaseLlmClient.RejectReason.VANITY),
                new BaseLlmClient.Reject("Built a cache. This ensured speed.", BaseLlmClient.RejectReason.PADDING));
        golden("software-recoveryNote", BaseLlmClient.recoveryNote(rejects, List.of(), 2, new GenerationConfig()));
    }

    @Test void generateBulletsProject() throws IOException {
        golden("software-generateBullets-project", capture(c ->
                c.generateBullets(req(Field.SOFTWARE, LlmClient.SourceKind.PROJECT), ProgressLog.noOp(), new TokenAccumulator())));
    }

    @Test void generateBulletsExperience() throws IOException {
        golden("software-generateBullets-experience", capture(c ->
                c.generateBullets(req(Field.SOFTWARE, LlmClient.SourceKind.EXPERIENCE), ProgressLog.noOp(), new TokenAccumulator())));
    }

    @Test void findStories() throws IOException {
        golden("software-findStories", capture(c ->
                c.findStories(new LlmClient.StoryRequest(req(Field.SOFTWARE, LlmClient.SourceKind.PROJECT), List.of("backend", "data")),
                        ProgressLog.noOp(), new TokenAccumulator())));
    }

    @Test void writeSlotCandidates() throws IOException {
        golden("software-writeSlotCandidates", capture(c ->
                c.writeSlotCandidates(req(Field.SOFTWARE, LlmClient.SourceKind.PROJECT), STORY, "backend", 6, List.of("Already written."),
                        ProgressLog.noOp(), new TokenAccumulator())));
    }

    @Test void cleanJd() throws IOException {
        golden("software-cleanJd", capture(c -> c.cleanJd("Backend engineer, Java.", Field.SOFTWARE, ProgressLog.noOp(), new TokenAccumulator())));
    }

    @Test void extractJd() throws IOException {
        golden("software-extractJd", capture(c -> c.extractJd("Backend engineer, Java.", Field.SOFTWARE, ProgressLog.noOp(), new TokenAccumulator())));
    }
}
