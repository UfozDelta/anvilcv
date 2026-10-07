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

    @Test
    void generateBankMakesTwoCallsWhateverTheLensCount() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(measurer.measure(any())).thenReturn(java.util.Map.of());
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(
                List.of(story("s1", "backend", "security"), story("s2", "data")), List.of()));
        when(llm.writeStoryBullets(any(), any(), any(), any())).thenReturn(new LlmClient.BulletGenerationResult(List.of(
                new LlmClient.GeneratedBullet("Built a ledger service that settles payouts nightly.", List.of(), "s1", "backend"),
                new LlmClient.GeneratedBullet("Locked payout approvals behind a two-person review rule.", List.of(), "s1", "security"),
                new LlmClient.GeneratedBullet("Ingested exchange fills from three brokers into one schema.", List.of(), "s2", "data"))));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<String> cats = List.of("backend", "data", "security", "devops");
        List<Bullet> out = service.generateBank(user, proj, cats, ProgressLog.noOp());

        ArgumentCaptor<LlmClient.StoryRequest> req = ArgumentCaptor.forClass(LlmClient.StoryRequest.class);
        verify(llm).findStories(req.capture(), any(), any());
        verify(llm).writeStoryBullets(any(), any(), any(), any());
        verify(llm, never()).generateBullets(any(), any(), any());
        assertEquals(cats, req.getValue().lenses());

        assertEquals(3, out.size());
        // Category = the lens a wording was written for; wordings of one story share a storyId.
        assertEquals(List.of("backend", "security", "data"), out.stream().map(Bullet::getCategory).toList());
        assertNotNull(out.get(0).getStoryId());
        assertEquals(out.get(0).getStoryId(), out.get(1).getStoryId());
        assertNotEquals(out.get(0).getStoryId(), out.get(2).getStoryId());
    }

    @Test
    void unreadableStoryPassFallsBackToOneCallPerLens() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        when(measurer.measure(any())).thenReturn(java.util.Map.of());
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(story("s1", "backend")), List.of()));
        when(llm.writeStoryBullets(any(), any(), any(), any())).thenThrow(new LlmParseException("bad json", null));
        when(llm.generateBullets(argThat(r -> r != null && "backend".equals(r.category())), any(), any()))
                .thenReturn(new LlmClient.BulletGenerationResult(List.of(new LlmClient.GeneratedBullet(
                        "Built a ledger service that settles payouts nightly.", List.of(), null, null))));
        when(llm.generateBullets(argThat(r -> r != null && "data".equals(r.category())), any(), any()))
                .thenThrow(new LlmParseException("bad json", null));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<String> progress = new java.util.ArrayList<>();

        List<Bullet> out = service.generateBank(user, proj, List.of("backend", "data"), progress::add);

        assertEquals(1, out.size(), "the unreadable lens is skipped, the other one is kept");
        assertEquals("backend", out.get(0).getCategory());
        verify(llm, times(2)).generateBullets(any(), any(), any());
        assertTrue(progress.stream().anyMatch(m -> m.contains("falling back")));
        assertTrue(progress.stream().anyMatch(m -> m.contains("data") && m.contains("skipped")));
    }

    @Test
    void fallbackRethrowsWhenEveryLensFails() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        LlmParseException storyFailure = new LlmParseException("bad", null);
        when(llm.findStories(any(), any(), any())).thenThrow(storyFailure);
        when(llm.generateBullets(any(), any(), any())).thenThrow(new LlmParseException("bad", null));

        assertSame(storyFailure, assertThrows(LlmParseException.class,
                () -> service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp())));
        verify(repo, never()).save(any());
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
    void fallbackCanBeDisabled() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.findStories(any(), any(), any())).thenThrow(new LlmParseException("bad", null));
        service.disableLensFallback();

        assertThrows(LlmParseException.class,
                () -> service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp()));
        verify(llm, never()).generateBullets(any(), any(), any());
    }

    @Test
    void generateBankWritesNothingWhenNoStorySurvives() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(), List.of("backend")));

        assertTrue(service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp()).isEmpty());
        verify(llm, never()).writeStoryBullets(any(), any(), any(), any());
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
        when(llm.writeStoryBullets(any(), any(), any(), any())).thenReturn(new LlmClient.BulletGenerationResult(List.of()));

        service.generateBank(user, proj, List.of("backend"), ProgressLog.noOp());

        ArgumentCaptor<LlmClient.StoryRequest> req = ArgumentCaptor.forClass(LlmClient.StoryRequest.class);
        verify(llm).findStories(req.capture(), any(), any());
        assertEquals(1, req.getValue().maxStories());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmClient.Story>> written = ArgumentCaptor.forClass(List.class);
        verify(llm).writeStoryBullets(any(), written.capture(), any(), any());
        assertEquals(List.of("s1"), written.getValue().stream().map(LlmClient.Story::id).toList());
    }

    @Test
    void storiesThatAllRepeatTheBankSaveNothing() {
        UUID user = UUID.randomUUID(), proj = UUID.randomUUID();
        when(projectService.get(user, proj)).thenReturn(project(user, Project.Kind.PROJECT));
        Story saved = new Story(UUID.randomUUID(), proj, "Order book resync", new String[]{LONG_QUOTE}, new String[]{"systems"});
        when(repo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(wording(proj, saved.getId(), "PENDING", "x")));
        when(storyRepo.findByProjectIdOrderByCreatedAtAsc(proj)).thenReturn(List.of(saved));
        when(llm.findStories(any(), any(), any())).thenReturn(new LlmClient.StoryResult(List.of(
                new LlmClient.Story("s1", "Resync on gaps", List.of(LONG_QUOTE), List.of("systems"))), List.of()));
        List<String> progress = new java.util.ArrayList<>();

        assertTrue(service.generateBank(user, proj, List.of("systems"), progress::add).isEmpty());

        verify(llm, never()).writeStoryBullets(any(), any(), any(), any());
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
