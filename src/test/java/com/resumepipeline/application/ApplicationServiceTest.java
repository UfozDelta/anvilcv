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
            when(bulletRepo.findByProjectUserId(user)).thenReturn(List.of());

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
            when(bulletRepo.findByProjectUserId(user)).thenReturn(List.of(bullet));
            when(projectRepo.findAllByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(project));
            Profile profile = new Profile();
            profile.setUserId(user);
            when(profileService.get(user)).thenReturn(profile);
            when(profileService.readEducation(profile)).thenReturn(List.of());
            when(llm.rankBullets(any(), any(), any())).thenReturn(new LlmClient.RankResult(
                    List.of(new LlmClient.RankedBullet(bullet.getId().toString(), 1, "fits")),
                    List.of("java"), List.of(), List.of(), Map.of()));
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
    }
}
