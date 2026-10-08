package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Story pass parsing and verification, the story writer's lens pinning, and the new text rules. */
class StoryGenerationTest {

    private static final String SOURCE = """
            Project description:
            A **trading terminal** that streams order books from 4 venues over WebSockets.
            Sequence-gap detection triggers a full resync, so the book is never silently stale.
            Median render time fell from 40ms to 12ms after moving to canvas.
            """;

    // ---- text rules ----

    @Test
    void sentenceCountIgnoresAbbreviationsAndDottedNames() {
        assertEquals(1, BulletTextRules.sentenceCount("Built a Next.js app (e.g. SSR pages) vs. a SPA on v5.4."));
        assertEquals(2, BulletTextRules.sentenceCount("Built a cache. Cut latency 40%."));
        assertEquals(3, BulletTextRules.sentenceCount("Built a cache. Cut latency. Saved money."));
    }

    @Test
    void paddedCatchesFillerAndThirdSentencesOnly() {
        assertTrue(BulletTextRules.isPadded("Built a cache. This ensured robust performance."));
        assertTrue(BulletTextRules.isPadded("Built a cache. Cut latency. Saved money."));
        assertFalse(BulletTextRules.isPadded("Built a Redis cache in front of the quote API. Cut p95 latency from 300ms to 90ms."));
        assertFalse(BulletTextRules.isPadded("Built a Next.js dashboard, e.g. for ops, that ensures alerts reach on-call."));
    }

    @Test
    void vanityCountIsNarrow() {
        assertEquals("87 commits", BulletTextRules.vanityCount("Shipped the app across 87 commits."));
        assertEquals("1,200 lines of Python", BulletTextRules.vanityCount("Wrote 1,200 lines of Python."));
        assertEquals("40 unit tests", BulletTextRules.vanityCount("Added **40** unit tests.".replace("**40** unit", "40 unit")));
        assertEquals(null, BulletTextRules.vanityCount("Served 41 REST endpoints to 3 client apps."));
        assertEquals(null, BulletTextRules.vanityCount("Integrated 4 venues and 16 indicator classes."));
        assertEquals(null, BulletTextRules.vanityCount("Cut test runtime from 9 minutes to 2."));
    }

    @Test
    void openingVerbIgnoresBoldAndCase() {
        assertEquals("engineered", BulletTextRules.openingVerb("**Engineered** a thing."));
        assertEquals("re-architected", BulletTextRules.openingVerb("Re-architected it."));
    }

    @Test
    void quotesSurviveMarkdownWrapsAndCurlyPunctuation() {
        assertTrue(BulletTextRules.isQuotedIn("streams order books from 4 venues over WebSockets", SOURCE));
        assertTrue(BulletTextRules.isQuotedIn("A trading terminal that streams order books", SOURCE));
        assertTrue(BulletTextRules.isQuotedIn("Sequence-gap detection triggers a full resync, so the book is never silently stale",
                SOURCE.replace("resync, so", "resync,\nso")));
        assertTrue(BulletTextRules.isQuotedIn("Median render time fell from 40ms to 12ms after moving to canvas", SOURCE));
        assertFalse(BulletTextRules.isQuotedIn("Median render time fell from 400ms to 12ms using WebGL shaders", SOURCE));
        assertFalse(BulletTextRules.isQuotedIn("4 venues", SOURCE));    // too short to prove anything
    }

    // ---- story validation ----

    private static BaseLlmClient.StoryJson story(String id, List<String> evidence, List<String> lenses) {
        BaseLlmClient.StoryJson s = new BaseLlmClient.StoryJson();
        s.id = id;
        s.title = "t " + id;
        s.evidence = evidence;
        s.lenses = lenses;
        return s;
    }

    @Test
    void validateDropsUnprovenStoriesAndUnrequestedLenses() {
        List<String> requested = List.of("systems", "frontend", "security");
        LlmClient.StoryResult r = BaseLlmClient.validateStories(List.of(
                story("s1", List.of("Sequence-gap detection triggers a full resync", "made up quote about kafka streams"),
                        List.of("Systems", "backend")),
                story("s2", List.of("rewrote the whole thing in Rust for 10x speed"), List.of("systems")),
                story("s3", List.of("Median render time fell from 40ms to 12ms"), List.of("ai-ml")),
                story("s1", List.of("Median render time fell from 40ms to 12ms after moving to canvas"), List.of("frontend"))),
                requested, SOURCE);

        assertEquals(2, r.stories().size());
        assertEquals(List.of("Sequence-gap detection triggers a full resync"), r.stories().get(0).evidence());
        assertEquals(List.of("systems"), r.stories().get(0).lenses());
        assertFalse(r.stories().get(1).id().equals("s1"), "duplicate model ids are made unique");
        assertEquals(List.of("security"), r.unsupportedLenses());
    }

    private static final String STORIES_REPLY = """
            {"choices":[{"message":{"content":"{\\"stories\\":[{\\"id\\":\\"s1\\",\\"title\\":\\"Book resync\\",\\"evidence\\":[\\"Sequence-gap detection triggers a full resync\\"],\\"lenses\\":[\\"systems\\"]}]}"}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5}}
            """;

    @Test
    void bankBlockListsLiveCoveredAndDismissedWork() {
        String block = BaseLlmClient.bankBlock(new LlmClient.BankCoverage(
                List.of(new LlmClient.KnownStory("Order book resync", List.of("systems", "backend"))),
                List.of("Wrote the venue adapters."),
                List.of("Dark mode toggle")));
        assertTrue(block.contains("ALREADY IN THE BANK"));
        assertTrue(block.contains("  - Order book resync   [lenses: systems, backend]"));
        assertTrue(block.contains("  - Wrote the venue adapters."));
        assertTrue(block.contains("do not pick:\n  - Dark mode toggle"));
        assertEquals("", BaseLlmClient.bankBlock(LlmClient.BankCoverage.EMPTY));
    }

    @Test
    void slotPromptAsksForVariedAngles() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GenerationConfig cfg = new GenerationConfig();
        cfg.setWordFilterEnabled(false);
        OpenCodeLlmClient client = new OpenCodeLlmClient(builder, "g", "m", "c", new GenerationConfigService(null) {
            @Override public GenerationConfig get(UUID userId) { return cfg; }
        });
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("failure prevented")))
                .andExpect(content().string(containsString("No verb may open more than two")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"bullets\\":[]}"}}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.GenerateBulletsRequest src = new LlmClient.GenerateBulletsRequest(UUID.randomUUID(),
                LlmClient.SourceKind.PROJECT, "general", "Terminal", SOURCE, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), null);
        client.writeSlotCandidates(src, new LlmClient.Story("s1", "Ledger", List.of("quote"), List.of("backend")),
                "backend", 15, List.of(), ProgressLog.noOp(), new TokenAccumulator());
        server.verify();
    }

    @Test
    void onlyTheStoryPassIsShownTheBank() {  // the slot writes never see the bank
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GenerationConfig cfg = new GenerationConfig();
        cfg.setWordFilterEnabled(false);
        OpenCodeLlmClient client = new OpenCodeLlmClient(builder, "g", "m", "c", new GenerationConfigService(null) {
            @Override public GenerationConfig get(UUID userId) { return cfg; }
        });
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(containsString("Venue failover drill")))
                .andExpect(content().string(containsString("Pick up to 5 NEW STORIES")))
                .andRespond(withSuccess(STORIES_REPLY, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andExpect(content().string(not(containsString("Venue failover drill"))))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"bullets\\":[]}"}}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        LlmClient.GenerateBulletsRequest src = new LlmClient.GenerateBulletsRequest(UUID.randomUUID(),
                LlmClient.SourceKind.PROJECT, "general", "Terminal", SOURCE, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), null);
        LlmClient.StoryRequest req = new LlmClient.StoryRequest(src, List.of("systems"), new LlmClient.BankCoverage(
                List.of(new LlmClient.KnownStory("Venue failover drill", List.of("systems"))), List.of(), List.of()), 5);

        LlmClient.StoryResult stories = client.findStories(req, ProgressLog.noOp(), new TokenAccumulator());
        client.writeSlotCandidates(src, new LlmClient.Story("s1", "Failover runbook", List.of("q"), List.of("systems")),
                "systems", 15, List.of(), ProgressLog.noOp(), new TokenAccumulator());
        server.verify();
    }

    @Test
    void unreadableStoryReplyIsRetriedOnce() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenCodeLlmClient client = new OpenCodeLlmClient(builder, "g", "m", "c", new GenerationConfigService(null) {
            @Override public GenerationConfig get(UUID userId) { return new GenerationConfig(); }
        });
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"stories\\":[{\\"id\\":"}}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo("http://localhost:8080/chat/completions"))
                .andRespond(withSuccess(STORIES_REPLY, MediaType.APPLICATION_JSON));

        LlmClient.GenerateBulletsRequest src = new LlmClient.GenerateBulletsRequest(UUID.randomUUID(),
                LlmClient.SourceKind.PROJECT, "general", "Terminal", SOURCE, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), null);
        TokenAccumulator tokens = new TokenAccumulator();
        LlmClient.StoryResult stories = client.findStories(
                new LlmClient.StoryRequest(src, List.of("systems")), ProgressLog.noOp(), tokens);

        assertEquals(1, stories.stories().size());
        assertEquals(20, tokens.getPromptTokens(), "both calls are billed");
        server.verify();
    }

    @Test
    void writingRulesCarryNoOtherUsersProjectData() {
        String rules = BaseLlmClient.writingRules(new GenerationConfig());
        for (String leak : List.of("MLS", "64K", "180ms", "credit ledger", "React-Leaflet", "2dsphere", "AES-256-GCM")) {
            assertFalse(rules.contains(leak), "prompt still carries " + leak);
        }
    }
}
