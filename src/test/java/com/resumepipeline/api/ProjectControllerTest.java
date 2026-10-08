package com.resumepipeline.api;

import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
@Import(ApiExceptionHandler.class)
class ProjectControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ProjectService projects;
    @MockitoBean BulletService bullets;
    @MockitoBean JobProgressStore jobStore;

    private static Project project(UUID userId) {
        return new Project(userId, Project.Kind.PROJECT, "P", "desc", null, null, null, null, null);
    }

    @Test
    void listPassesAuthenticatedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(projects.list(userId)).thenReturn(List.of(project(userId)));

        mvc.perform(get("/api/projects").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("P"));

        verify(projects).list(userId); // controller forwarded the principal's id
    }

    @Test
    void listReturnsBulletCountAndUpdatedAt() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        Project p = project(userId);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "updatedAt", java.time.Instant.parse("2026-01-02T03:04:05Z"));
        when(projects.list(userId)).thenReturn(List.of(p));
        when(projects.bulletCounts(any())).thenReturn(java.util.Map.of(id, 5L));

        mvc.perform(get("/api/projects").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bulletCount").value(5))
                .andExpect(jsonPath("$[0].updatedAt").exists());
    }

    @Test
    void getNotFoundMapsTo404() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        when(projects.get(userId, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        mvc.perform(get("/api/projects/{id}", id).with(user(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsBlankNameWith400() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = "{\"name\":\"\",\"description\":\"d\"}"; // @NotBlank name violated

        mvc.perform(post("/api/projects")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projects);
    }

    @Test
    void createForwardsUserIdAndReturnsProject() throws Exception {
        UUID userId = UUID.randomUUID();
        when(projects.create(eq(userId), any(), eq("New"), eq("d"), any(), any(), any(), any(), any(), any()))
                .thenReturn(project(userId));
        String body = "{\"name\":\"New\",\"description\":\"d\"}";

        mvc.perform(post("/api/projects")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(projects).create(eq(userId), any(), eq("New"), eq("d"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createForwardsTheCurrentlyWorkHereFlag() throws Exception {
        UUID userId = UUID.randomUUID();
        when(projects.create(eq(userId), any(), eq("Role"), eq("d"), any(), any(), any(), any(), any(), eq(true)))
                .thenReturn(project(userId));

        mvc.perform(post("/api/projects")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXPERIENCE\",\"name\":\"Role\",\"description\":\"d\",\"current\":true}"))
                .andExpect(status().isOk());

        verify(projects).create(eq(userId), any(), eq("Role"), eq("d"), any(), any(), any(), any(), any(), eq(true));
    }

    @Test
    void deleteForwardsUserId() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();

        mvc.perform(delete("/api/projects/{id}", id).with(user(userId)).with(csrf()))
                .andExpect(status().isOk());

        verify(projects).delete(userId, id);
    }

    @Test
    void storiesListsLiveStoriesWithTheCap() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        com.resumepipeline.bullet.Story s = new com.resumepipeline.bullet.Story(
                UUID.randomUUID(), id, "Ledger service", new String[]{"quote"}, new String[]{"backend"});
        when(bullets.listStories(userId, id)).thenReturn(List.of(s));

        mvc.perform(get("/api/projects/" + id + "/stories").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cap").value(BulletService.STORY_CAP))
                .andExpect(jsonPath("$.stories[0].title").value("Ledger service"))
                .andExpect(jsonPath("$.stories[0].lenses[0]").value("backend"));
    }

    @Test
    void wordingsSubmitAnswersBadInputBeforeStartingAJob() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID(), storyId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Unknown lens: frontend")).when(bullets)
                .checkWordings(userId, id, storyId, List.of("frontend"));

        mvc.perform(post("/api/projects/" + id + "/stories/" + storyId + "/wordings/submit")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lenses\":[\"frontend\"]}"))
                .andExpect(status().isBadRequest());

        // Scoped to this test's ids: the async job from the sibling test shares this mock.
        verify(bullets, never()).generateWordings(eq(userId), eq(id), eq(storyId), any(), any(), any());
        verifyNoInteractions(jobStore);
    }

    @Test
    void wordingsSubmitStartsAJobAndReturnsItsId() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID(), storyId = UUID.randomUUID();

        mvc.perform(post("/api/projects/" + id + "/stories/" + storyId + "/wordings/submit")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lenses\":[\"data\",\"data\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists());

        verify(bullets).checkWordings(userId, id, storyId, List.of("data", "data"));
        verify(jobStore).start(any(), eq(userId));
    }
}
