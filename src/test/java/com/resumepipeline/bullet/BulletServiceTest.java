package com.resumepipeline.bullet;

import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.llm.LlmUsageService;
import com.resumepipeline.llm.TokenAccumulator;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @InjectMocks BulletService service;

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
}
