package com.resumepipeline.api;

import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectService;
import com.resumepipeline.project.ResumeParserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
@Import(ApiExceptionHandler.class)
class ResumeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ResumeParserService parser;
    @MockitoBean ProjectService projects;
    @MockitoBean BulletService bullets;

    @Test
    void importSavesTheConfirmedLinesOfEachEntryAsBullets() throws Exception {
        UUID userId = UUID.randomUUID();
        Project created = new Project(userId, Project.Kind.EXPERIENCE, "acme", "desc", null, "Eng", "Acme", "NYC", "2024");
        when(projects.create(eq(userId), eq(Project.Kind.EXPERIENCE), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        mvc.perform(post("/api/resume/import").with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"kind":"EXPERIENCE","name":"acme","description":"desc","title":"Eng","company":"Acme",
                                 "bullets":["Shipped billing.","Cut latency 40%."]}]}"""))
                .andExpect(status().isOk());

        verify(bullets).importApproved(created.getId(), List.of("Shipped billing.", "Cut latency 40%."));
    }

    @Test
    void importWithoutBulletsStillCreatesTheEntry() throws Exception {
        UUID userId = UUID.randomUUID();
        Project created = new Project(userId, Project.Kind.PROJECT, "p", "desc", null, null, null, null, null);
        when(projects.create(eq(userId), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(created);

        mvc.perform(post("/api/resume/import").with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"name\":\"p\",\"description\":\"desc\"}]}"))
                .andExpect(status().isOk());

        verify(bullets).importApproved(created.getId(), null);
    }

    @Test
    void tooManyOrBlankBulletsAreRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(post("/api/resume/import").with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"name\":\"p\",\"description\":\"d\",\"bullets\":[\"\"]}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bullets);
    }
}
