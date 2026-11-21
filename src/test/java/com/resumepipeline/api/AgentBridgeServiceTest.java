package com.resumepipeline.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.api.dto.AgentDtos.*;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.bullet.Story;
import com.resumepipeline.bullet.StoryRepository;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.quota.QuotaService;
import com.resumepipeline.render.PdfCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** The bridge must use AnvilCV's own rules and persistence, no LLM calls. */
@ExtendWith(MockitoExtension.class)
class AgentBridgeServiceTest {

    @Mock StoryRepository stories;
    @Mock BulletRepository bullets;
    @Mock ApplicationRepository applications;
    @Mock ProjectService projects;
    @Mock GenerationConfigService configService;
    @Mock ApplicationRenderer renderer;
    @Mock PdfCompiler compiler;
    @Mock QuotaService quota;

    AgentBridgeService service;
    final UUID userId = UUID.randomUUID();
    final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AgentBridgeService(stories, bullets, applications, projects, configService,
                renderer, compiler, quota, new ObjectMapper());
    }

    @Test
    void createStoryNormalizesLensesAndSavesEvidence() {
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        when(bullets.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of());

        UUID id = service.createStory(userId, new CreateStoryRequest(projectId, "A platform change",
                List.of("quote one", "quote two"), List.of("backend", "frontend", "BOGUS")));

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(stories).save(captor.capture());
        Story saved = captor.getValue();
        assertEquals(id, saved.getId());
        assertEquals(projectId, saved.getProjectId());
        assertArrayEquals(new String[]{"quote one", "quote two"}, saved.getEvidence());
        assertArrayEquals(new String[]{"backend", "general"}, saved.getLenses());
    }

    @Test
    void createStoryRefusesWhenBankIsFull() {
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        List<Bullet> live = new ArrayList<>();
        for (int i = 0; i < BulletService.STORY_CAP; i++) {
            Bullet b = new Bullet(projectId, "bullet " + i, new String[0]);
            b.setStoryId(UUID.randomUUID());
            live.add(b);
        }
        when(bullets.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(live);

        assertThrows(IllegalStateException.class, () -> service.createStory(userId,
                new CreateStoryRequest(projectId, "New story", List.of(), List.of("general"))));
        verify(stories, never()).save(any());
    }

    @Test
    void createBulletsKeepsAngleJudgeNoteAndStatusAndCapsBold() {
        UUID storyId = UUID.randomUUID();
        Story story = new Story(storyId, projectId, "Story", new String[0], new String[]{"general"});
        when(stories.findById(storyId)).thenReturn(java.util.Optional.of(story));
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        when(configService.get(userId)).thenReturn(new GenerationConfig());
        when(bullets.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UUID> saved = service.createBullets(userId, storyId, new CreateBulletsRequest(List.of(
                new AgentBullet("Built **a thing** and **another** and **third** and **fourth** and **fifth** and **sixth**",
                        List.of("java"), "backend", "OUTCOME", "good note", "approved"),
                new AgentBullet("Designed a queue", List.of(), "bogus-lens", "nonsense", null, null))));

        assertEquals(2, saved.size());
        ArgumentCaptor<List<Bullet>> captor = ArgumentCaptor.forClass(List.class);
        verify(bullets).saveAll(captor.capture());
        List<Bullet> written = captor.getValue();
        assertEquals(storyId, written.get(0).getStoryId());
        assertEquals("outcome", written.get(0).getAngle());
        assertEquals("good note", written.get(0).getJudgeNote());
        assertEquals("APPROVED", written.get(0).getStatus());
        assertEquals("backend", written.get(0).getCategory());
        assertEquals("general", written.get(1).getCategory());
        assertNull(written.get(1).getAngle());
        assertEquals("PENDING", written.get(1).getStatus());
        assertTrue(written.get(0).getText().endsWith("."));
    }

    @Test
    void createBulletsRejectsUnknownStatus() {
        UUID storyId = UUID.randomUUID();
        when(stories.findById(storyId)).thenReturn(java.util.Optional.of(
                new Story(storyId, projectId, "Story", new String[0], new String[]{"general"})));
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        when(configService.get(userId)).thenReturn(new GenerationConfig());

        assertThrows(IllegalArgumentException.class, () -> service.createBullets(userId, storyId,
                new CreateBulletsRequest(List.of(new AgentBullet("A bullet.", List.of(), "general",
                        null, null, "MAYBE")))));
        verify(bullets, never()).saveAll(anyList());
    }

    @Test
    void checkBulletsRunsAnvilCvRules() {
        when(configService.get(userId)).thenReturn(new GenerationConfig());
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        Bullet existing = new Bullet(projectId, "Worked on the build pipeline with 12 commits", new String[0]);
        when(bullets.findByProjectIdOrderByCreatedAtAsc(projectId)).thenReturn(List.of(existing));

        BulletChecksResponse response = service.checkBullets(userId, new CheckBulletsRequest(
                List.of("Worked on the build pipeline with 12 commits", "Cut latency by 40% using a queue"),
                null, projectId, "no numbers here"));

        BulletCheck check = response.checks().get(0);
        assertTrue(check.forbiddenOpener());
        assertNotNull(check.vanityCount());
        assertTrue(check.nearDuplicate());
        assertNotNull(check.decision());
        assertFalse(response.checks().get(1).fabricatedNumbers().isEmpty());
        assertTrue(response.singleLow() > 0 && response.doubleHigh() > response.singleHigh());
    }

    @Test
    void checkCoverageSeparatesHardGatesFromGaps() {
        CoverageResponse response = service.checkCoverage(new CheckCoverageRequest(
                "Must have a degree in CS. Kubernetes experience is preferred.", "", List.of("Kubernetes")));

        assertEquals(1, response.hardGateMisses().size());
        assertTrue(response.coverageGaps().stream().anyMatch(g -> g.contains("preferred")));
        assertTrue(response.items().stream().anyMatch(i -> "KEYWORD".equals(i.kind())));
    }

    @Test
    void createApplicationRendersCompilesPersistsAndRecordsQuota() {
        UUID bulletId = UUID.randomUUID();
        Bullet b = new Bullet(projectId, "Built a queue that cut latency.", new String[0]);
        ReflectionTestUtils.setField(b, "id", bulletId);
        when(bullets.findByIdsAndProjectUserId(any(UUID[].class), eq(userId))).thenReturn(List.of(b));
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        when(renderer.render(eq(userId), anyList(), any(), anyList(), any(), any())).thenReturn("\\documentclass{article}");
        when(compiler.compile(anyString())).thenReturn(PdfCompiler.Result.success(new byte[]{1, 2}, "one page"));
        when(applications.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        Application saved = service.createApplication(userId, new CreateAgentApplicationRequest(
                "Must have Kubernetes.", "https://example.com/job", "backend", "Acme", "Engineer",
                List.of("Kubernetes"), List.of(bulletId), Map.of("languages", List.of("Java")),
                List.of("OSU"), "Dear Acme,"));

        assertNotNull(saved);
        assertArrayEquals(new byte[]{1, 2}, saved.getPdfBlob());
        assertTrue(new String(saved.getTexBlob()).startsWith("\\documentclass"));
        assertArrayEquals(new UUID[]{bulletId}, saved.getSelectedBulletIds());
        assertEquals(List.of("Kubernetes"), List.of(saved.getAtsMissing()));
        verify(quota).checkTailor(userId);
        verify(quota).recordTailor(eq(userId), any());
    }

    @Test
    void createApplicationFailsWithoutSavingWhenCompileFails() {
        UUID bulletId = UUID.randomUUID();
        Bullet b = new Bullet(projectId, "Built a queue.", new String[0]);
        ReflectionTestUtils.setField(b, "id", bulletId);
        when(bullets.findByIdsAndProjectUserId(any(UUID[].class), eq(userId))).thenReturn(List.of(b));
        when(projects.get(userId, projectId)).thenReturn(mock(Project.class));
        when(renderer.render(eq(userId), anyList(), any(), anyList(), any(), any())).thenReturn("\\documentclass{article}");
        when(compiler.compile(anyString())).thenReturn(PdfCompiler.Result.failure("tectonic exploded", "log"));

        assertThrows(ResponseStatusException.class, () -> service.createApplication(userId,
                new CreateAgentApplicationRequest("JD", null, null, null, null, List.of(),
                        List.of(bulletId), Map.of(), List.of(), null)));
        verify(applications, never()).save(any());
        verify(quota, never()).recordTailor(any(), any());
    }
}
