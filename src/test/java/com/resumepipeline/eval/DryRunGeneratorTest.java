package com.resumepipeline.eval;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmParseException;
import com.resumepipeline.llm.LlmUsageLog;
import com.resumepipeline.llm.LlmUsageLogRepository;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.render.PdfCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DryRunGeneratorTest {

    @Mock BulletRepository realBullets;   // stands for the Spring bean: must never be reached
    @Mock ProjectService projectService;
    @Mock LlmClient llm;
    @Mock LlmUsageLogRepository usage;
    @Mock GenerationConfigService configService;
    @Mock ProjectRepository projectRepo;
    @Mock ApplicationRenderer renderer;
    @Mock PdfCompiler compiler;
    @Mock ApplicationRepository applicationRepo;

    @Test
    void runsTheRealGeneratorWithoutTouchingTheBulletTable() {
        UUID owner = UUID.randomUUID(), projectId = UUID.randomUUID();
        when(projectService.get(owner, projectId)).thenReturn(
                new Project(owner, Project.Kind.PROJECT, "P", "desc", null, "Eng", "Acme", "NYC", "2024"));
        when(configService.get(any())).thenReturn(new GenerationConfig());
        LlmClient.Story story = new LlmClient.Story("s1", "Pricing cache", List.of("pricing cache"), List.of("backend"));
        when(llm.findStories(any(), any(), any())).thenAnswer(inv -> {
            inv.getArgument(2, TokenAccumulator.class).add("m", 100, 10);
            return new LlmClient.StoryResult(List.of(story), List.of());
        });
        when(llm.writeSlotCandidates(any(), any(), any(), anyInt(), any(), any(), any())).thenReturn(
                new LlmClient.SlotCandidates(List.of(
                        new LlmClient.Candidate("Built a Redis cache for pricing.", List.of("backend")),
                        new LlmClient.Candidate("Designed an ETL job for billing data.", List.of("data"))), 15, 13));
        when(llm.scoreCandidates(any(), any(), any(), any())).thenReturn(List.of(5, 4));

        DryRunGenerator gen = new DryRunGenerator(projectService, llm, usage, configService, projectRepo,
                renderer, compiler, applicationRepo);
        List<Bullet> out = gen.generate(owner, projectId, List.of("backend"), ProgressLog.noOp());

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(b -> b.getId() == null), "returned bullets are unsaved");
        assertEquals("backend", out.get(0).getCategory());
        verifyNoInteractions(realBullets, compiler, applicationRepo);

        // From-scratch run: the generator is not shown any existing bank to avoid.
        ArgumentCaptor<LlmClient.StoryRequest> req = ArgumentCaptor.forClass(LlmClient.StoryRequest.class);
        verify(llm).findStories(req.capture(), any(), any());
        assertTrue(req.getValue().source().existingBullets().isEmpty());

        // Spend is tagged apart from real users' bullet_generation.
        ArgumentCaptor<LlmUsageLog> logged = ArgumentCaptor.forClass(LlmUsageLog.class);
        verify(usage).save(logged.capture());
        assertEquals("eval_generation", logged.getValue().getSource());
    }

    @Test
    void storyFailureFailsTheEvalInsteadOfFallingBack() {
        UUID owner = UUID.randomUUID(), projectId = UUID.randomUUID();
        when(projectService.get(owner, projectId)).thenReturn(
                new Project(owner, Project.Kind.PROJECT, "P", "desc", null, "Eng", "Acme", "NYC", "2024"));
        when(llm.findStories(any(), any(), any())).thenThrow(new LlmParseException("bad json", null));

        DryRunGenerator gen = new DryRunGenerator(projectService, llm, usage, configService, projectRepo,
                renderer, compiler, applicationRepo);
        assertThrows(LlmParseException.class,
                () -> gen.generate(owner, projectId, List.of("backend"), ProgressLog.noOp()));
        verify(llm, never()).generateBullets(any(), any(), any());
    }

    @Test
    void inMemoryRepoBlocksEveryWriteButSave() {
        BulletRepository repo = DryRunGenerator.inMemory(BulletRepository.class);
        Bullet b = new Bullet(UUID.randomUUID(), "x", new String[0]);
        assertSame(b, repo.save(b));
        assertTrue(repo.findByProjectIdOrderByCreatedAtAsc(UUID.randomUUID()).isEmpty());
        assertTrue(repo.findById(UUID.randomUUID()).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> repo.deleteById(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class, repo::deleteAll);
        assertThrows(UnsupportedOperationException.class, repo::flush);
    }
}
