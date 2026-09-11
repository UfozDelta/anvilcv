package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.jd.JdFetcher;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.render.PdfCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Caveats of the no-Spring-context approach for this service:
 *   - the static PARALLEL_EXECUTOR (virtual threads) and field {@code new ObjectMapper()}
 *     are NOT mocked — compile + cover-letter futures run on real threads, Jackson is real;
 *   - tests assert end-state and interactions, never cross-future timing.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository repo;
    @Mock OutcomeHistoryRepository outcomeHistoryRepo;
    @Mock BulletRepository bulletRepo;
    @Mock ProjectRepository projectRepo;
    @Mock JdFetcher jdFetcher;
    @Mock LlmClient llm;
    @Mock ApplicationRenderer renderer;
    @Mock PdfCompiler compiler;
    @Mock ProfileService profileService;
    @Mock LlmUsageService llmUsageService;
    @Mock SkillRowMeasurer skillRowMeasurer;
    @InjectMocks ApplicationService service;

    @Nested
    class Crud {

        @Test
        void listUsesPlainQueryWhenOutcomeBlank() {
            UUID user = UUID.randomUUID();
            service.list(user, "  ");
            verify(repo).findAllByUserIdOrderByCreatedAtDesc(user);
            verify(repo, never()).findByUserIdAndOutcomeOrderByCreatedAtDesc(any(), any());
        }

        @Test
        void listFiltersWhenOutcomeProvided() {
            UUID user = UUID.randomUUID();
            service.list(user, "offer");
            verify(repo).findByUserIdAndOutcomeOrderByCreatedAtDesc(user, "offer");
        }

        @Test
        void getThrowsWhenMissing() {
            UUID user = UUID.randomUUID(), id = UUID.randomUUID();
            when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.get(user, id));
        }

        @Test
        void updateOutcomeSetsAndSaves() {
            UUID user = UUID.randomUUID(), id = UUID.randomUUID();
            Application a = new Application();
            when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.of(a));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Application out = service.updateOutcome(user, id, "rejected");
            assertEquals("rejected", out.getOutcome());
            verify(outcomeHistoryRepo).save(any());
        }

        @Test
        void updateOutcomeSkipsHistoryWhenOutcomeUnchanged() {
            UUID user = UUID.randomUUID(), id = UUID.randomUUID();
            Application a = new Application();
            a.setOutcome("interview");
            when(repo.findByUserIdAndId(user, id)).thenReturn(Optional.of(a));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            service.updateOutcome(user, id, "interview");
            verify(outcomeHistoryRepo, never()).save(any());
        }
    }

    @Nested
    class CreateGuards {

        @Test
        void rejectsWhenNoJdTextOrUrl() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(UUID.randomUUID(), null, null, "backend", false, ProgressLog.noOp()));
        }

        @Test
        void throwsWhenBulletBankEmpty() {
            UUID user = UUID.randomUUID();
            when(llm.cleanJd(any(), any(), any()))
                    .thenReturn(new LlmClient.JdCleanResult("clean", "Acme", "Eng", List.of("java")));
            when(bulletRepo.findSelectableByProjectUserId(user)).thenReturn(List.of());

            assertThrows(IllegalStateException.class,
                    () -> service.create(user, "jd text", null, "backend", false, ProgressLog.noOp()));
        }
    }

    @Nested
    class CreatePipeline {

        UUID user;
        UUID proj;
        Bullet bullet;

        @BeforeEach
        void setup() {
            user = UUID.randomUUID();
            proj = UUID.randomUUID();
            bullet = TestFixtures.bullet(UUID.randomUUID(), proj, new String[]{"backend"});

            Project project = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            when(llm.cleanJd(any(), any(), any()))
                    .thenReturn(new LlmClient.JdCleanResult("clean jd", "Acme", "Eng", List.of("java")));
            when(bulletRepo.findSelectableByProjectUserId(user)).thenReturn(List.of(bullet));
            when(projectRepo.findAllByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(project));
            Profile profile = new Profile();
            profile.setUserId(user);
            when(profileService.get(user)).thenReturn(profile);
            when(profileService.readEducation(profile)).thenReturn(List.of());
            when(llm.rankBullets(any(), any(), any())).thenReturn(new LlmClient.RankResult(
                    List.of(new LlmClient.RankedBullet(bullet.getId().toString(), 1, "fits")),
                    List.of("java"), List.of(), List.of(), Map.of()));
            when(llm.scoreFit(any(), any(), any())).thenReturn(new LlmClient.FitResult(
                    80, 70, 75, "Strong Fit", List.of("owns the stack"), List.of("no Terraform")));
            when(llm.reviewResume(any(), any(), any())).thenReturn(new LlmClient.RecruiterResult(
                    80, 60, 70, "Solid", bullet.getId().toString(), "Kubernetes at scale",
                    List.of("no metrics", "no ownership"),
                    List.of(new LlmClient.BulletVerdict(bullet.getId().toString(), "weak", "vague"))));
            when(renderer.render(any(), any(), any(), any(), any())).thenReturn("\\documentclass{article}");
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void happyPathPersistsPdfAndRecordsUsage() {
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1, 2, 3}, "ok log"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertArrayEquals(new byte[]{1, 2, 3}, out.getPdfBlob());
            assertEquals("Acme", out.getCompany());
            assertEquals(1, out.getSelectedBulletIds().length);
            verify(llm, never()).coverLetter(any(), any(), any()); // not requested
            verify(llmUsageService).record(eq(user), eq("application_pipeline"), any(), any(), isNull());
        }

        @Test
        void fitScorePersistsOnHappyPath() {
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1}, "log"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertEquals(75, out.getFitScore());
            assertEquals("Strong Fit", out.getFitVerdict());
            assertTrue(out.getFitDimensions().contains("\"technical\":80"));
            assertArrayEquals(new String[]{"owns the stack"}, out.getFitStrengths());
            assertArrayEquals(new String[]{"no Terraform"}, out.getFitGaps());
        }

        @Test
        void fitScoreFailureDoesNotFailThePipeline() {
            when(llm.scoreFit(any(), any(), any())).thenThrow(new RuntimeException("fit model down"));
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1, 2}, "log"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertArrayEquals(new byte[]{1, 2}, out.getPdfBlob());
            assertNull(out.getFitScore());
            assertNull(out.getFitVerdict());
            assertEquals(0, out.getFitStrengths().length);
        }

        @Test
        void recruiterScorecardPersistsOnHappyPath() {
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(
                    new byte[]{1}, "Output written on in.pdf (1 page, 4096 bytes)."));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertEquals(70, out.getRecruiterScore());
            assertEquals("Solid", out.getRecruiterVerdict());
            assertTrue(out.getRecruiterDimensions().contains("\"evidenceStrength\":80"));
            assertTrue(out.getRecruiterBulletVerdicts().contains("\"verdict\":\"weak\""));
            assertFalse(out.isRecruiterStale());
            assertEquals(1, out.getPageCount());
            assertArrayEquals(new String[]{"no metrics", "no ownership"}, out.getRecruiterWeaknesses());
            assertEquals("Kubernetes at scale", out.getRecruiterThinnestRequirement());
            assertEquals(bullet.getId(), out.getRecruiterWeakestBulletId());
        }

        @Test
        void recruiterFailureDoesNotFailThePipeline() {
            when(llm.reviewResume(any(), any(), any())).thenThrow(new RuntimeException("recruiter model down"));
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{4, 5}, "log"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertArrayEquals(new byte[]{4, 5}, out.getPdfBlob());
            assertNull(out.getRecruiterScore());
            assertNull(out.getRecruiterVerdict());
            assertEquals("{}", out.getRecruiterDimensions());
            assertEquals("[]", out.getRecruiterBulletVerdicts());
            assertEquals(0, out.getRecruiterWeaknesses().length);
            assertNull(out.getRecruiterThinnestRequirement());
            assertNull(out.getRecruiterWeakestBulletId());
        }

        @Test
        void coverLetterGeneratedWhenRequested() {
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{9}, "log"));
            when(llm.coverLetter(any(), any(), any())).thenReturn("Dear Acme team...");

            Application out = service.create(user, "jd text", null, "backend", true, ProgressLog.noOp());

            assertEquals("Dear Acme team...", out.getCoverLetter());
            verify(llm).coverLetter(any(), any(), any());
        }

        @Test
        void tectonicFailureStillPersistsApplication() {
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.failure("exit 1", "bad latex"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            assertNull(out.getPdfBlob());
            assertTrue(out.getTectonicLog().startsWith("FAILED: exit 1"));
            verify(repo).save(out); // persisted despite compile failure
        }

        @Test
        void usageRecordedAfterSave() {
            // LlmUsageService.record() swallows its own persistence failures (see
            // LlmUsageServiceTest), so a usage-logging error cannot fail the pipeline.
            // Here assert the ordering contract: record runs after the application is saved.
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1}, "log"));

            Application out = service.create(user, "jd text", null, "backend", false, ProgressLog.noOp());

            InOrder order = inOrder(repo, llmUsageService);
            order.verify(repo).save(any());
            order.verify(llmUsageService).record(eq(user), eq("application_pipeline"), any(),
                    eq(out.getId()), isNull());
        }

        @Test
        void fetchesJdFromUrlWhenTextAbsent() {
            when(jdFetcher.fetch("https://jobs.example.com/1")).thenReturn("fetched jd body");
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1}, "log"));

            service.create(user, null, "https://jobs.example.com/1", "backend", false, ProgressLog.noOp());

            verify(jdFetcher).fetch("https://jobs.example.com/1");
        }
    }

    @Nested
    class Rerender {

        @Test
        void rerenderDoesNotCallLlm() {
            UUID user = UUID.randomUUID(), appId = UUID.randomUUID(), proj = UUID.randomUUID();
            Application a = new Application();
            Bullet b = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");

            when(repo.findByUserIdAndId(user, appId)).thenReturn(Optional.of(a));
            when(bulletRepo.findByIdsAndProjectUserId(any(), eq(user))).thenReturn(List.of(b));
            when(projectRepo.findByIdIn(any())).thenReturn(List.of(p));
            when(renderer.render(any(), any(), any(), any(), any())).thenReturn("\\doc");
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1}, "log"));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rerender(user, appId, List.of(b.getId()), ProgressLog.noOp());

            verifyNoInteractions(llm);
        }

        @Test
        void rerenderMarksScorecardStaleAndRefreshesTheFreeHalf() {
            UUID user = UUID.randomUUID(), appId = UUID.randomUUID(), proj = UUID.randomUUID();
            Application a = new Application();
            // Scored against the previous selection; the LLM half must survive untouched.
            a.setRecruiterScore(70);
            a.setRecruiterVerdict("Solid");
            a.setRecruiterDimensions("{\"evidenceStrength\":80}");
            a.setRecruiterBulletVerdicts("[{\"bulletId\":\"x\"}]");
            // Neither keyword appears in the fixture bullet text, so the literal check drops both.
            a.setAtsMatched(new String[]{"kubernetes"});
            a.setAtsMissing(new String[]{"java"});

            Bullet b = TestFixtures.bullet(UUID.randomUUID(), proj, new String[0]);
            Project p = TestFixtures.project(proj, Project.Kind.PROJECT, "P");
            when(repo.findByUserIdAndId(user, appId)).thenReturn(Optional.of(a));
            when(bulletRepo.findByIdsAndProjectUserId(any(), eq(user))).thenReturn(List.of(b));
            when(projectRepo.findByIdIn(any())).thenReturn(List.of(p));
            when(renderer.render(any(), any(), any(), any(), any())).thenReturn("\\doc");
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(
                    new byte[]{1}, "Output written on in.pdf (2 pages, 8192 bytes)."));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Application out = service.rerender(user, appId, List.of(b.getId()), ProgressLog.noOp());

            verify(llm, never()).reviewResume(any(), any(), any());
            assertTrue(out.isRecruiterStale());
            assertEquals(70, out.getRecruiterScore());          // kept, not blanked
            assertEquals("Solid", out.getRecruiterVerdict());
            assertEquals(2, out.getPageCount());                // recomputed from the new compile
            // "kubernetes" was LLM-claimed but is not literally on the new page, so it moves to missing.
            assertEquals(0, out.getAtsMatched().length);
            assertEquals(2, out.getAtsMissing().length);
        }
    }

    /**
     * Scoped refit. The contract under test is narrow and easy to regress: when a project id is
     * given, no entry other than that one may change — not even by gaining a bullet, which is
     * what pass 4's floor would otherwise do to any under-filled entry it finds.
     */
    @Nested
    class RefitSelection {

        private final UUID user = UUID.randomUUID();
        private final UUID appId = UUID.randomUUID();
        private final UUID expA = UUID.randomUUID();
        private final UUID projB = UUID.randomUUID();
        private final UUID projC = UUID.randomUUID();

        /** Four bullets per project so every entry has bank depth left to be topped up from. */
        private List<Bullet> bank(UUID... projects) {
            List<Bullet> out = new java.util.ArrayList<>();
            for (UUID p : projects) {
                for (int i = 0; i < 4; i++) out.add(TestFixtures.bullet(UUID.randomUUID(), p, new String[0]));
            }
            return out;
        }

        private String rankingJson(List<Bullet> bank) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < bank.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"bulletId\":\"").append(bank.get(i).getId())
                  .append("\",\"rank\":").append(i + 1).append(",\"why\":\"w\"}");
            }
            return sb.append(']').toString();
        }

        private Application stub(List<Bullet> bank, List<Bullet> onPage, List<Bullet> locked) {
            Application a = new Application();
            a.setBulletRanking(rankingJson(bank));
            a.setSelectedBulletIds(onPage.stream().map(Bullet::getId).toArray(UUID[]::new));
            a.setLockedBulletIds(locked.stream().map(Bullet::getId).toArray(UUID[]::new));

            when(repo.findByUserIdAndId(user, appId)).thenReturn(Optional.of(a));
            when(bulletRepo.findSelectableByProjectUserId(user)).thenReturn(bank);
            when(projectRepo.findAllByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(
                    TestFixtures.project(expA, Project.Kind.EXPERIENCE, "Experience A"),
                    TestFixtures.project(projB, Project.Kind.PROJECT, "Project B"),
                    TestFixtures.project(projC, Project.Kind.PROJECT, "Project C")));
            when(renderer.render(any(), any(), any(), any(), any())).thenReturn("\\doc");
            when(compiler.compile(any())).thenReturn(PdfCompiler.Result.success(new byte[]{1}, "log"));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return a;
        }

        private List<UUID> idsIn(Application a, UUID projectId, List<Bullet> bank) {
            Map<UUID, Bullet> byId = bank.stream().collect(java.util.stream.Collectors.toMap(Bullet::getId, b -> b));
            return java.util.Arrays.stream(a.getSelectedBulletIds())
                    .filter(id -> byId.containsKey(id) && projectId.equals(byId.get(id).getProjectId()))
                    .toList();
        }

        @Test
        void scopedRefitLeavesEveryOtherEntryUntouched() {
            List<Bullet> bank = bank(expA, projB, projC);
            // expA deliberately under-filled at 2: pass 4's floor tops surviving projects up to
            // MAX_PER_PROJECT, so without the post-filter this entry would silently gain a third.
            List<Bullet> onPage = List.of(bank.get(0), bank.get(1),            // expA  x2
                                          bank.get(4), bank.get(5), bank.get(6), // projB x3
                                          bank.get(8));                          // projC x1
            Application a = stub(bank, onPage, List.of());
            List<UUID> expABefore = idsIn(a, expA, bank);
            List<UUID> projBBefore = idsIn(a, projB, bank);

            Application out = service.refitSelection(user, appId, projC, ProgressLog.noOp());

            assertEquals(expABefore, idsIn(out, expA, bank), "experience entry must not change");
            assertEquals(projBBefore, idsIn(out, projB, bank), "other project entry must not change");
            assertEquals(2, expABefore.size(), "the under-filled entry stays under-filled");
        }

        @Test
        void scopedRefitKeepsALockedBulletInsideTheTargetEntry() {
            List<Bullet> bank = bank(expA, projB, projC);
            Bullet pinnedInTarget = bank.get(8);   // projC
            List<Bullet> onPage = List.of(bank.get(0), bank.get(4), pinnedInTarget);
            stub(bank, onPage, List.of(pinnedInTarget));

            Application out = service.refitSelection(user, appId, projC, ProgressLog.noOp());

            assertTrue(java.util.Arrays.asList(out.getSelectedBulletIds()).contains(pinnedInTarget.getId()),
                    "a locked bullet in the refitted entry must survive the re-pick");
        }

        @Test
        void scopedRefitRejectsAProjectTheUserDoesNotOwn() {
            List<Bullet> bank = bank(expA, projB, projC);
            Application a = new Application();
            a.setBulletRanking(rankingJson(bank));
            when(repo.findByUserIdAndId(user, appId)).thenReturn(Optional.of(a));
            when(bulletRepo.findSelectableByProjectUserId(user)).thenReturn(bank);
            when(projectRepo.findAllByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(
                    TestFixtures.project(expA, Project.Kind.EXPERIENCE, "Experience A")));

            UUID someoneElse = UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,
                    () -> service.refitSelection(user, appId, someoneElse, ProgressLog.noOp()));
            verify(repo, never()).save(any());
            verifyNoInteractions(compiler);
        }

        @Test
        void nullScopeStillRefitsEveryEntry() {
            List<Bullet> bank = bank(expA, projB, projC);
            // One bullet per entry, so a whole-page refit has room to grow all three.
            List<Bullet> onPage = List.of(bank.get(0), bank.get(4), bank.get(8));
            stub(bank, onPage, List.of());

            Application out = service.refitSelection(user, appId, null, ProgressLog.noOp());

            assertTrue(out.getSelectedBulletIds().length > onPage.size(),
                    "an unscoped refit fills the page rather than preserving the prior entries");
            assertTrue(out.isRecruiterStale());
        }
    }
}
