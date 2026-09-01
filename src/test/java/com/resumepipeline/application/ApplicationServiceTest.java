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
}
