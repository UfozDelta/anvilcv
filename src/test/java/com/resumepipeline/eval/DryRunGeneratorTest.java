package com.resumepipeline.eval;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
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
    @Mock LlmUsageService usage;
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
        when(llm.generateBullets(any(), any(), any())).thenReturn(new LlmClient.BulletGenerationResult(List.of(
                new LlmClient.GeneratedBullet("Built a Redis cache for pricing.", List.of("backend")),
                new LlmClient.GeneratedBullet("Designed an ETL job for billing data.", List.of("data")))));

        DryRunGenerator gen = new DryRunGenerator(projectService, llm, usage, configService, projectRepo,
                renderer, compiler, applicationRepo);
        List<Bullet> out = gen.generate(owner, projectId, List.of("backend"), ProgressLog.noOp());

        assertEquals(2, out.size());
        assertTrue(out.stream().allMatch(b -> b.getId() == null), "returned bullets are unsaved");
        assertEquals("backend", out.get(0).getCategory());
        verifyNoInteractions(realBullets, compiler, applicationRepo);

        // From-scratch run: the generator is not shown any existing bank to avoid.
        ArgumentCaptor<LlmClient.GenerateBulletsRequest> req =
                ArgumentCaptor.forClass(LlmClient.GenerateBulletsRequest.class);
        verify(llm).generateBullets(req.capture(), any(), any());
        assertTrue(req.getValue().existingBullets().isEmpty());
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
