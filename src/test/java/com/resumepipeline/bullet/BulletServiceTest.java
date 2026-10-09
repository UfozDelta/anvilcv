package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmParseException;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulletServiceTest {

    @Mock BulletRepository repo;
    @Mock ProjectService projectService;
    @Mock LlmClient llm;
    @Mock LlmUsageService llmUsageService;
    @Mock GenerationConfigService configService;
    @Mock ApplicationRepository applicationRepo;
    @Mock BulletLineMeasurer measurer;
    @Mock BulletMeasureDiagnosticRepository diagnosticRepo;
    @Mock StoryRepository storyRepo;
    @Mock TransactionOperations tx;
    @InjectMocks BulletService service;

    @BeforeEach
    void runTransactionsInline() {
        lenient().when(tx.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private static Project project(UUID user, Project.Kind kind) {
        return new Project(user, kind, "P", "desc", null, "Eng", "Acme", "NYC", "2024");
    }

    // ---- ownership checks ----

    @Test
    void listForProjectVerifiesOwnership() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        service.listForProject(user, proj);
        verify(projectService).get(user, proj);
        verify(repo).findByProjectIdOrderByCreatedAtAsc(proj);
    }

    @Test
    void createVerifiesOwnership() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.create(user, proj, "text", new String[]{"backend"}, "general");
        verify(projectService).get(user, proj);
    }

    @Test
    void updateThrows404WhenBulletMissing() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.update(user, id, "x", null));
    }

    @Test
    void updateLeavesFieldsUnchangedWhenNull() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID(), id = UUID.randomUUID();
        Bullet b = new Bullet(proj, "original", new String[]{"backend"}, "general");
        when(repo.findById(id)).thenReturn(Optional.of(b));
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bullet out = service.update(user, id, null, null);

        assertEquals("original", out.getText());
        assertArrayEquals(new String[]{"backend"}, out.getTags());
    }

    @Test
    void editingTheTextInvalidatesScoredPagesRenderingIt() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID(), id = UUID.randomUUID();
        Bullet b = new Bullet(proj, "original", new String[]{"backend"}, "general");
        when(repo.findById(id)).thenReturn(Optional.of(b));
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(user, id, "rewritten", null);

        // The bullet keeps its id, so every application still renders it - under a scorecard
        // that graded the old wording. Nothing else in the app flags that.
        verify(applicationRepo).markRecruiterStaleForBullets(user, id.toString());
        // ...and the stored PDF still prints the old wording.
        verify(applicationRepo).markPdfStaleForBullets(user, id.toString());
    }

    @Test
    void editingOnlyTagsLeavesScorecardsAlone() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID(), id = UUID.randomUUID();
        Bullet b = new Bullet(proj, "original", new String[]{"backend"}, "general");
        when(repo.findById(id)).thenReturn(Optional.of(b));
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(user, id, null, new String[]{"data"});

        // Tags steer selection and are not graded, so no scorecard changed...
        verify(applicationRepo, never()).markRecruiterStaleForBullets(any(), any());
        // ...but a project heading prints them when its tech stack is blank, so the PDF may have.
        verify(applicationRepo).markPdfStaleForBullets(user, id.toString());
    }

    @Test
    void aFailedStaleFlagNeverLosesTheEdit() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID(), id = UUID.randomUUID();
        Bullet b = new Bullet(proj, "original", new String[]{"backend"}, "general");
        when(repo.findById(id)).thenReturn(Optional.of(b));
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepo.markRecruiterStaleForBullets(any(), any()))
                .thenThrow(new RuntimeException("db down"));

        // The edit is the user's actual request; bookkeeping must not be able to fail it.
        Bullet out = service.update(user, id, "rewritten", null);

        assertEquals("rewritten", out.getText());
    }

    @Test
    void deleteThrows404WhenBulletMissing() {
        UUID user = UUID.randomUUID(), id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.delete(user, id));
    }

    // ---- generation ----

    @Test
    void generateMapsExperienceKindAndPersistsBullets() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.EXPERIENCE));
        when(llm.generateBullets(any(), any(), any())).thenReturn(
                new LlmClient.BulletGenerationResult(List.of(
                        new LlmClient.GeneratedBullet("b1.", List.of("backend")),
                        new LlmClient.GeneratedBullet("b2.", List.of("data")))));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Bullet> out = service.generateForProjectAndCategory(user, proj, "backend", ProgressLog.noOp());

        ArgumentCaptor<LlmClient.GenerateBulletsRequest> req =
                ArgumentCaptor.forClass(LlmClient.GenerateBulletsRequest.class);
        verify(llm).generateBullets(req.capture(), any(), any());
        assertEquals(LlmClient.SourceKind.EXPERIENCE, req.getValue().kind());
        assertEquals("backend", req.getValue().category());
        assertEquals(2, out.size());
        verify(repo, times(2)).save(any());
    }

    @Test
    void generateDefaultsBlankCategoryToGeneral() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.generateBullets(any(), any(), any())).thenReturn(
                new LlmClient.BulletGenerationResult(List.of()));

        service.generateForProjectAndCategory(user, proj, "  ", ProgressLog.noOp());

        ArgumentCaptor<LlmClient.GenerateBulletsRequest> req =
                ArgumentCaptor.forClass(LlmClient.GenerateBulletsRequest.class);
        verify(llm).generateBullets(req.capture(), any(), any());
        assertEquals("general", req.getValue().category());
        assertEquals(LlmClient.SourceKind.PROJECT, req.getValue().kind());
    }

    @Test
    void generateAlwaysCallsRecordEvenWhenLlmThrows() {
        // The finally block must always invoke llmUsageService.record. Note: record itself
        // no-ops when promptTokens==0 (verified in LlmUsageServiceTest), so here we stub the
        // LLM to add tokens to the accumulator before throwing, then assert record was called
        // with a non-empty accumulator.
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.generateBullets(any(), any(), any())).thenAnswer(inv -> {
            TokenAccumulator t = inv.getArgument(2);
            t.add("gemini-2.5-flash", 100, 20);
            throw new RuntimeException("LLM blew up");
        });

        assertThrows(RuntimeException.class,
                () -> service.generateForProjectAndCategory(user, proj, "backend", ProgressLog.noOp()));

        ArgumentCaptor<TokenAccumulator> tok = ArgumentCaptor.forClass(TokenAccumulator.class);
        verify(llmUsageService).record(eq(user), eq("bullet_generation"), tok.capture(), isNull(), eq(proj));
        assertEquals(100, tok.getValue().getPromptTokens());
    }

    @Test
    void generateBankRejectsEmptyCategories() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.generateBank(user, proj, List.of(), ProgressLog.noOp()));
    }

    @Test
    void generateBankRejectsUnknownCategory() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.generateBank(user, proj, List.of("not-a-real-lens"), ProgressLog.noOp()));
    }

    private static LlmClient.Story story(String id, String... lenses) {
        return new LlmClient.Story(id, "title " + id, List.of("quote"), List.of(lenses));
    }

    private static LlmClient.Candidate cand(String text, String... tags) {
        return new LlmClient.Candidate(text, List.of(tags));
    }

    private static LlmClient.SlotCandidates batch(LlmClient.Candidate... kept) {
        return new LlmClient.SlotCandidates(List.of(kept), 15, 15 - kept.length);
    }

    private void stubStoryRun(UUID user, UUID proj) {
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(measurer.measure(any())).thenReturn(java.util.Map.of());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generateBankRunsOneSlotPerNewStoryOnItsBestLens() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(
                List.of(story("s1", "backend", "data"), story("s2", "data")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
            LlmClient.Story s = inv.getArgument(1);
            return s.id().equals("s1")
                    ? batch(cand("Built a ledger service that settles payouts nightly."))
                    : batch(cand("Ingested exchange fills from three brokers into one schema."));
        });
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(new LlmClient.JudgeScore(5)));

        List<Bullet> out = service.generateBank(user, proj, List.of("backend", "data"), ProgressLog.noOp());

        assertEquals(List.of("backend", "data"), out.stream().map(Bullet::getCategory).toList());
        verify(llm).writeSlotCandidates(any(), argThat(s -> s.id().equals("s1")), eq("backend"), eq(15), any(), any(), any());
        verify(llm).writeSlotCandidates(any(), argThat(s -> s.id().equals("s2")), eq("data"), eq(15), any(), any(), any());
    }

    @Test
    void aFailingSlotKeepsTheSlotsBeforeIt() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(
                story("s1", "backend"), story("s2", "data"), story("s3", "general")), List.of()));
        String a = "Cut payout latency batching settlements nightly across ledgers.";
        String b = "Chose batch settlement over per transfer writes for lower load.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
            LlmClient.Story st = inv.getArgument(1);
            if (st.id().equals("s1")) return batch(cand(a));
            if (st.id().equals("s2")) return batch(cand(b));
            throw new RuntimeException("boom");
        });
        when(llm.scoreCandidates(any(), any(), any(), any())).thenAnswer(inv -> {
            List<LlmClient.Candidate> cs = inv.getArgument(1);
            return cs.stream().map(c -> new LlmClient.JudgeScore(5)).toList();
        });

        BulletService.PartialRunException e = assertThrows(BulletService.PartialRunException.class,
                () -> service.generateBank(user, proj, List.of("backend", "data", "general"), ProgressLog.noOp()));

        assertTrue(e.getMessage().startsWith("2 wording(s) saved before the failure: boom"), e.getMessage());
        verify(repo, times(2)).save(any());
    }

    @Test
    void parseFailureInASlotFailsTheRunAndSavesNothing() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new LlmParseException("bad json", null));

        BulletService.PartialRunException e = assertThrows(BulletService.PartialRunException.class,
                () -> service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp()));
        assertTrue(e.getMessage().startsWith("0 wording(s) saved before the failure: "), e.getMessage());
        assertTrue(e.getCause() instanceof LlmParseException);
        verify(repo, never()).save(any());
        verify(llm, never()).scoreCandidates(any(), any(), any(), any());
    }

    @Test
    void unreadableJudgeFallsBackToTheTopTwoByCodeScore() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        // Scores: cut 80 (number + result verb), reduced 65 (result verb), built 60 (tag not in stack, no outcome).
        String built = "Built a ledger service in Go for payouts.";
        String reduced = "Reduced reconciliation errors across regions.";
        String cut = "Cut payout latency 40% by batching settlements.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(built, "Go"), cand(reduced), cand(cut)));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of());

        List<Bullet> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        assertEquals(List.of(cut, reduced), out.stream().map(Bullet::getText).toList());
        // The fallback has no judge, so there is no note to keep.
        assertTrue(out.stream().allMatch(b -> b.getJudgeNote() == null));
    }

    /** Scores by candidate text: "Cut" 5, "Built" 4, anything else 1. Works for any presented order. */
    private void stubScores(org.mockito.stubbing.Answer<List<LlmClient.JudgeScore>> answer) {
        when(llm.scoreCandidates(any(), any(), any(), any())).thenAnswer(answer);
    }

    private static List<LlmClient.JudgeScore> scoreByPrefix(org.mockito.invocation.InvocationOnMock inv) {
        List<LlmClient.Candidate> cs = inv.getArgument(1);
        return cs.stream().map(c -> new LlmClient.JudgeScore(c.text().startsWith("Cut") ? 5 : c.text().startsWith("Built") ? 4 : 1)).toList();
    }

    @Test
    void judgeScoresDecideTheBulletsKept() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        String built = "Built a ledger service in Go for payouts.";
        String reduced = "Reduced reconciliation errors across regions.";
        String cut = "Cut payout latency 40% by batching settlements.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(built, "Go"), cand(reduced), cand(cut)));
        stubScores(inv -> scoreByPrefix(inv));

        List<Bullet> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        // cut 5 is pick #1; built 4 is within one point of it and is pick #2. reduced 1 is not.
        assertEquals(List.of(cut, built), out.stream().map(Bullet::getText).toList());
        verify(llm, times(2)).scoreCandidates(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void theJudgeIsShownEachCandidatesAngle() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(
                new LlmClient.SlotCandidates(List.of(
                        new LlmClient.Candidate("Cut payout latency 40% by batching settlements.", List.of(), "outcome")), 1, 0));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(new LlmClient.JudgeScore(5)));

        service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        ArgumentCaptor<List<LlmClient.Candidate>> judged = ArgumentCaptor.forClass(List.class);
        verify(llm, atLeastOnce()).scoreCandidates(any(), judged.capture(), any(), any());
        assertEquals("outcome", judged.getValue().get(0).angle());
    }

    /** Runs one slot with these candidates and judge scores; returns the saved wordings in order. */
    private List<String> slotWith(List<LlmClient.Candidate> cands, Map<String, Integer> scoreByText) {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(new LlmClient.SlotCandidates(cands, cands.size(), 0));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenAnswer(inv -> {
            List<LlmClient.Candidate> cs = inv.getArgument(1);
            return cs.stream().map(x -> new LlmClient.JudgeScore(scoreByText.getOrDefault(x.text(), 1))).toList();
        });
        return service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp())
                .stream().map(Bullet::getText).toList();
    }

    private static final String PAIR_A = "Cut payout latency 40% by batching settlements.";

    @Test
    void pairKeepsADifferentAngleWordingThatIsNotATwin() {
        String b = "Chose batch settlement over per-transfer writes for lower load.";
        assertEquals(List.of(PAIR_A, b), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), "outcome"),
                new LlmClient.Candidate(b, List.of(), "decision")), Map.of(PAIR_A, 5, b, 4)));
    }

    @Test
    void pairRejectsTheSameAngle() {
        String b = "Ran settlement batches at lower load with fewer retries.";
        assertEquals(List.of(PAIR_A), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), "outcome"),
                new LlmClient.Candidate(b, List.of(), "outcome")), Map.of(PAIR_A, 5, b, 4)));
    }

    @Test
    void pairRejectsANearTwinEvenWithADifferentAngle() {
        String twin = "Cut payout latency 40% by batching the settlement jobs.";
        assertEquals(List.of(PAIR_A), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), "outcome"),
                new LlmClient.Candidate(twin, List.of(), "decision")), Map.of(PAIR_A, 5, twin, 4)));
    }

    @Test
    void pairWithUnknownAngleKeepsADistinctWording() {
        String b = "Chose batch settlement over per-transfer writes for lower load.";
        assertEquals(List.of(PAIR_A, b), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), null),
                new LlmClient.Candidate(b, List.of(), null)), Map.of(PAIR_A, 5, b, 4)));
    }

    @Test
    void pairWithUnknownAngleStillRejectsANearTwin() {
        String twin = "Cut payout latency 40% by batching the settlement jobs.";
        assertEquals(List.of(PAIR_A), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), "outcome"),
                new LlmClient.Candidate(twin, List.of(), null)), Map.of(PAIR_A, 5, twin, 4)));
    }

    @Test
    void pairIsSingleWhenNothingQualifies() {
        String c = "Reduced reconciliation errors across regions.";
        assertEquals(List.of(PAIR_A), slotWith(List.of(
                new LlmClient.Candidate(PAIR_A, List.of(), "outcome"),
                new LlmClient.Candidate(c, List.of(), "decision")), Map.of(PAIR_A, 5, c, 2)));
    }

    @Test
    void pickTwoAppliesTheVarietyPenaltyToTheScore() {
        // X is pick #1 (average 5). A averages 4.5 and is 0.333 similar to X; B averages 4 and is unrelated.
        // A's score after the penalty is 4.5 - 2 * 0.333 = 3.83, so B (4.0) wins, though A has the higher average.
        String x = "Cut payout latency batching settlements nightly across ledgers.";
        String a = "Cut payout latency batching ledger reviews weekly fast";
        String b = "Chose batch settlement over per transfer writes for lower load.";
        java.util.concurrent.atomic.AtomicInteger run = new java.util.concurrent.atomic.AtomicInteger();
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(x), cand(a), cand(b)));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenAnswer(inv -> {
            List<LlmClient.Candidate> cs = inv.getArgument(1);
            boolean first = run.getAndIncrement() == 0;
            return cs.stream().map(c -> {
                if (c.text().equals(x)) return new LlmClient.JudgeScore(5);
                if (c.text().equals(a)) return new LlmClient.JudgeScore(first ? 5 : 4);
                return new LlmClient.JudgeScore(4);
            }).toList();
        });

        List<String> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp())
                .stream().map(Bullet::getText).toList();

        assertEquals(List.of(x, b), out);
    }

    @Test
    void oneUnreadableJudgeRunStillRanksFromTheOther() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        String built = "Built a ledger service in Go for payouts.";
        String reduced = "Reduced reconciliation errors across regions.";
        String cut = "Cut payout latency 40% by batching settlements.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(built, "Go"), cand(reduced), cand(cut)));
        // Top list in code order is cut, reduced, built. The second run is reversed: built, reduced, cut.
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(), List.of(new LlmClient.JudgeScore(4), new LlmClient.JudgeScore(1), new LlmClient.JudgeScore(5)));

        List<Bullet> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        assertEquals(List.of(cut, built), out.stream().map(Bullet::getText).toList());
    }

    @Test
    void varietyBreaksANearTieButNeverOverridesAClearWinner() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        String a = "Cut payout latency 40% by batching settlements.";
        String b = "Cut payout latency 40% by batching the settlement jobs.";
        String c = "Reduced reconciliation errors across regions.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(a), cand(b), cand(c)));
        // a and b tie at 5 and nearly repeat each other; c is one point lower and differs.
        stubScores(inv -> {
            List<LlmClient.Candidate> cs = inv.getArgument(1);
            return cs.stream().map(x -> new LlmClient.JudgeScore(x.text().startsWith("Reduced") ? 4 : 5)).toList();
        });

        List<String> texts = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp())
                .stream().map(Bullet::getText).toList();

        assertTrue(texts.contains(c), texts.toString());
        assertEquals(1, texts.stream().filter(t -> t.equals(a) || t.equals(b)).count(), texts.toString());
    }

    @Test
    void candidatesThatRepeatTheBankAreDroppedBeforeJudging() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(
                wording(proj, null, "REJECTED", "Built a ledger service in Go for payouts.")));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        String cut = "Cut payout latency 40% by batching settlements.";
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand("Built a ledger service in Go for payouts."), cand(cut)));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(new LlmClient.JudgeScore(5)));

        List<Bullet> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        assertEquals(List.of(cut), out.stream().map(Bullet::getText).toList());
        verify(llm, times(2)).scoreCandidates(any(), argThat(l -> l.size() == 1), any(), any());
    }

    @Test
    void wordingsForAStoryOutsideTheProjectAreNotFound() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(storyRepo.findById(any())).thenReturn(java.util.Optional.of(
                new Story(UUID.randomUUID(), UUID.randomUUID(), "Other", new String[0], new String[0])));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.checkWordings(user, proj, UUID.randomUUID(), List.of("backend")));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void wordingsForADeletedStoryConflict() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        Story st = new Story(UUID.randomUUID(), proj, "Ledger", new String[]{LONG_QUOTE}, new String[]{"backend"});
        when(storyRepo.findById(st.getId())).thenReturn(java.util.Optional.of(st));
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(
                wording(proj, st.getId(), "REJECTED", "Built the ledger.")));

        assertThrows(IllegalStateException.class,
                () -> service.checkWordings(user, proj, st.getId(), List.of("backend")));
    }

    @Test
    void unknownLensIsRejectedBeforeAnyLookup() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.checkWordings(user, proj, UUID.randomUUID(), List.of("frontend")));
        verifyNoInteractions(storyRepo);
    }

    @Test
    void moreWordingsRunEachChosenLensAndJoinTheStoryWithoutANewRow() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        Story st = new Story(UUID.randomUUID(), proj, "Ledger service", new String[]{LONG_QUOTE}, new String[]{"backend"});
        when(storyRepo.findById(st.getId())).thenReturn(java.util.Optional.of(st));
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(
                wording(proj, st.getId(), "PENDING", "Built a ledger service in Go for payouts.")));
        // "data" is a weak fit for this story (it carries only backend); a repeat lens runs again.
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(
                batch(cand("Cut payout latency 40% by batching settlements.")),
                batch(cand("Reduced reconciliation errors across regions.")));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(new LlmClient.JudgeScore(5)));

        List<Bullet> out = service.generateWordings(user, proj, st.getId(), List.of("data", "data"), List.of(), ProgressLog.noOp());

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(b -> st.getId().equals(b.getStoryId())));
        verify(llm, times(2)).writeSlotCandidates(any(), any(), eq("data"), anyInt(), any(), any(), any());
        verify(storyRepo, never()).saveAll(any());
        verify(storyRepo).save(st);
        assertEquals(List.of("backend", "data"), List.of(st.getLenses()));
    }

    @Test
    void slotTagNamesTheLensAndTheStory() {
        assertEquals("[backend · \"Ledger service\"]", BulletService.slotTag("backend", "Ledger service"));
    }

    @Test
    void nonParseStoryFailureDoesNotFallBack() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.findStories(any(), any(), any())).thenThrow(new RuntimeException("LLM call timed out"));

        assertThrows(RuntimeException.class,
                () -> service.generateBank(user, proj, List.of("backend", "data"), ProgressLog.noOp()));
        verify(llm, never()).generateBullets(any(), any(), any());
    }

    @Test
    void generateBankWritesNothingWhenNoStorySurvives() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(), List.of("backend")));

        assertTrue(service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp()).isEmpty());
        verify(llm, never()).writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void storyDedupKeepsTwoWordingsOfOneStoryButDropsARepeatAcrossStories() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String a = "Built a ledger service in Go that settles marketplace payouts nightly.";
        String aAlt = "Built a Go ledger service settling marketplace payouts nightly for sellers.";
        List<Bullet> out = service.saveStoryBullets(user, proj, List.of(
                new LlmClient.GeneratedBullet(a, List.of(), "s1", "backend"),
                new LlmClient.GeneratedBullet(aAlt, List.of(), "s1", "data"),
                new LlmClient.GeneratedBullet(aAlt, List.of(), "s2", "backend")),
                List.of(story("s1", "backend", "data"), story("s2", "backend")), List.of(), ProgressLog.noOp());

        assertEquals(List.of(a, aAlt), out.stream().map(Bullet::getText).toList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void theKeptWordingKeepsItsJudgeNote() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        stubStoryRun(user, proj);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(batch(cand(PAIR_A)));
        when(llm.scoreCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(new LlmClient.JudgeScore(5, "good: concrete result; bad: none")));

        List<Bullet> out = service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        assertEquals("good: concrete result; bad: none", out.get(0).getJudgeNote());
    }

    @Test
    void savedWordingsKeepTheirAngle() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<Bullet> out = service.saveStoryBullets(user, proj, List.of(
                new LlmClient.GeneratedBullet("Cut payout latency by batching settlements nightly.", List.of(), "s1", "backend", "scale")),
                List.of(story("s1", "backend")), List.of(), ProgressLog.noOp());
        assertEquals("scale", out.get(0).getAngle());
    }

    @Test
    void storyRowsAreSavedOnlyForStoriesThatKeptAWording() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String a = "Built a ledger service in Go that settles marketplace payouts nightly.";
        List<Bullet> out = service.saveStoryBullets(user, proj, List.of(
                new LlmClient.GeneratedBullet(a, List.of(), "s1", "backend"),
                new LlmClient.GeneratedBullet(a, List.of(), "s2", "backend")),   // repeat: dropped
                List.of(story("s1", "backend"), story("s2", "backend")), List.of(), ProgressLog.noOp());

        ArgumentCaptor<List<Story>> rows = ArgumentCaptor.forClass(List.class);
        verify(storyRepo).saveAll(rows.capture());
        assertEquals(1, rows.getValue().size());
        Story s1 = rows.getValue().get(0);
        assertEquals(out.get(0).getStoryId(), s1.getId());
        assertEquals("title s1", s1.getTitle());
        assertEquals(proj, s1.getProjectId());
        verify(tx).execute(any());
    }

    private static Bullet wording(UUID proj, UUID story, String status, String text) {
        Bullet b = new Bullet(proj, text, new String[0], "backend");
        b.setStoryId(story);
        b.setStatus(status);
        return b;
    }

    @Test
    void bankCoverageSplitsLiveDismissedAndStorylessWork() {
        UUID proj = UUID.randomUUID();
        Story live = new Story(UUID.randomUUID(), proj, "Ledger service", new String[0], new String[]{"backend"});
        Story dismissed = new Story(UUID.randomUUID(), proj, "Dark mode", new String[0], new String[]{"frontend"});
        Story orphan = new Story(UUID.randomUUID(), proj, "Deleted work", new String[0], new String[]{"data"});
        List<Bullet> bank = List.of(
                wording(proj, live.getId(), "REJECTED", "Built the ledger."),
                wording(proj, live.getId(), "PENDING", "Settled payouts nightly."),   // one live wording keeps it live
                wording(proj, dismissed.getId(), "REJECTED", "Added a dark mode toggle."),
                wording(proj, null, "PENDING", "x".repeat(300)),
                wording(proj, null, "REJECTED", "Rejected storyless bullet."));

        LlmClient.BankCoverage c = BulletService.bankCoverage(bank, List.of(live, dismissed, orphan));

        assertEquals(List.of(new LlmClient.KnownStory("Ledger service", List.of("backend"))), c.live());
        assertEquals(List.of("Dark mode"), c.dismissed());
        assertEquals(1, c.coveredWork().size());
        assertTrue(c.coveredWork().get(0).length() <= BulletService.COVERED_WORK_CHARS);
        assertEquals(java.util.Set.of(live.getId()), BulletService.liveStoryIds(bank));
    }

    private static final String LONG_QUOTE = "Sequence-gap detection triggers a full resync of the order book";

    @Test
    void dropRepeatsCatchesQuoteOverlapTitleRepeatsAndRepeatsWithinTheRun() {
        UUID proj = UUID.randomUUID();
        Story saved = new Story(UUID.randomUUID(), proj, "Order book resync",
                new String[]{"**" + LONG_QUOTE + "**, so the book is never stale."}, new String[]{"systems"});
        LlmClient.Story quoteRepeat = new LlmClient.Story("s1", "Gap detection",
                List.of(LONG_QUOTE.toUpperCase(), "short quote"), List.of("systems"));
        LlmClient.Story titleRepeat = new LlmClient.Story("s2", "Order book resync",
                List.of("A brand new quote about canvas rendering that is long enough"), List.of("frontend"));
        LlmClient.Story fresh = new LlmClient.Story("s3", "Canvas renderer",
                List.of("Median render time fell from 40ms to 12ms after moving to canvas"), List.of("frontend"));
        LlmClient.Story freshAgain = new LlmClient.Story("s4", "Faster charts",
                List.of("Median render time fell from 40ms to 12ms after moving to canvas"), List.of("frontend"));
        LlmClient.Story shortOnly = new LlmClient.Story("s5", "Login page", List.of("4 venues"), List.of("frontend"));

        List<LlmClient.Story> kept = BulletService.dropRepeats(
                List.of(quoteRepeat, titleRepeat, fresh, freshAgain, shortOnly), List.of(saved));

        assertEquals(List.of("s3", "s5"), kept.stream().map(LlmClient.Story::id).toList());
    }

    private void liveBank(UUID proj, int stories) {
        List<Bullet> bank = new java.util.ArrayList<>();
        for (int i = 0; i < stories; i++) bank.add(wording(proj, UUID.randomUUID(), "PENDING", "Bullet " + i));
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(bank);
    }

    @Test
    void aFullStoryBankMakesNoLlmCall() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        liveBank(proj, BulletService.STORY_CAP);
        List<String> progress = new java.util.ArrayList<>();

        assertTrue(service.generateBank(user, proj, List.of("backend"), progress::add).isEmpty());

        verifyNoInteractions(llm);
        assertTrue(progress.stream().anyMatch(m -> m.startsWith("Story bank full (12)")), progress.toString());
    }

    @Test
    void asksOnlyForTheRoomLeftAndKeepsNoMore() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        liveBank(proj, BulletService.STORY_CAP - 1);
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(
                new LlmClient.Story("s1", "Ledger service", List.of("q"), List.of("backend")),
                new LlmClient.Story("s2", "Payout approvals", List.of("q"), List.of("backend"))), List.of()));
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(batch());

        service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        ArgumentCaptor<LlmClient.StoryRequest> req = ArgumentCaptor.forClass(LlmClient.StoryRequest.class);
        verify(llm).findStories(req.capture(), any(), any());
        assertEquals(1, req.getValue().maxStories());
        verify(llm).writeSlotCandidates(any(), argThat(x -> x.id().equals("s1")), eq("backend"), anyInt(), any(), any(), any());
        verify(llm, never()).writeSlotCandidates(any(), argThat(x -> x.id().equals("s2")), any(), anyInt(), any(), any(), any());
    }

    @Test
    void storiesThatAllRepeatTheBankSaveNothing() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        Story saved = new Story(UUID.randomUUID(), proj, "Order book resync", new String[]{LONG_QUOTE}, new String[]{"data"});
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(wording(proj, saved.getId(), "PENDING", "x")));
        when(storyRepo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(saved));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(
                new LlmClient.Story("s1", "Resync on gaps", List.of(LONG_QUOTE), List.of("data"))), List.of()));
        List<String> progress = new java.util.ArrayList<>();

        assertTrue(service.generateBank(user, proj, List.of("data"), progress::add).isEmpty());

        verify(llm, never()).writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any());
        verify(llm, never()).generateBullets(any(), any(), any());
        assertTrue(progress.contains("All stories duplicated existing ones — nothing generated."), progress.toString());
    }

    @Test
    void aSecondGenerateOnTheSameProjectFailsFast() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        // The nested call runs while the first is still inside findStories.
        when(llm.findStories(any(), any(), any())).thenAnswer(inv -> {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp()));
            assertTrue(e.getMessage().contains("already being generated"));
            return new LlmClient.StoryResult(List.of(), List.of("backend"));
        });

        service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        verify(llm, times(1)).findStories(any(), any(), any());
        // Released afterwards: the next run gets through.
        service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());
        verify(llm, times(2)).findStories(any(), any(), any());
    }

    @Test
    void storyDedupNeverRepeatsTheStoredBank() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(configService.get(any())).thenReturn(new GenerationConfig());
        String a = "Built a ledger service in Go that settles marketplace payouts nightly.";
        List<Bullet> out = service.saveStoryBullets(user, proj, List.of(
                new LlmClient.GeneratedBullet(a, List.of(), "s1", "backend")), List.of(story("s1", "backend")),
                List.of(a), ProgressLog.noOp());
        assertTrue(out.isEmpty());
    }
}
