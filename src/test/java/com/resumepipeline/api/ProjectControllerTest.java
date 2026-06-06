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
        when(projects.create(eq(userId), any(), eq("New"), eq("d"), any(), any(), any(), any(), any()))
                .thenReturn(project(userId));
        String body = "{\"name\":\"New\",\"description\":\"d\"}";

        mvc.perform(post("/api/projects")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(projects).create(eq(userId), any(), eq("New"), eq("d"), any(), any(), any(), any(), any());
    }

    @Test
    void deleteForwardsUserId() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();

        mvc.perform(delete("/api/projects/{id}", id).with(user(userId)).with(csrf()))
                .andExpect(status().isOk());

        verify(projects).delete(userId, id);
    }
}
