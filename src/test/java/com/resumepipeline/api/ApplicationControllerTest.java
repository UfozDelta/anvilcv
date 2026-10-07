package com.resumepipeline.api;

import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApplicationController.class)
@Import(ApiExceptionHandler.class)
class ApplicationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ApplicationService service;
    @MockitoBean JobProgressStore jobStore;
    @MockitoBean ProfileRepository profiles;

    @Test
    void listForwardsOutcomeFilter() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.list(userId, "offer")).thenReturn(List.of());

        mvc.perform(get("/api/applications").param("outcome", "offer").with(user(userId)))
                .andExpect(status().isOk());

        verify(service).list(userId, "offer");
    }

    @Test
    void getNotFoundMapsTo400() throws Exception {
        // ApplicationService.get throws IllegalArgumentException (not ResponseStatusException),
        // which the handler maps to 400 — asymmetric vs ProjectController's 404. Lock the behavior.
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        when(service.get(userId, id)).thenThrow(new IllegalArgumentException("Application not found"));

        mvc.perform(get("/api/applications/{id}", id).with(user(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selectionCheckReturnsTheServiceWarningsForTheCaller() throws Exception {
        UUID userId = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        when(service.selectionWarnings(userId, List.of(a, b)))
                .thenReturn(List.of(new ApplicationService.SelectionWarning(b, a, "near-duplicate")));

        mvc.perform(post("/api/applications/selection-check")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedBulletIds\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bulletId").value(b.toString()))
                .andExpect(jsonPath("$[0].conflictId").value(a.toString()))
                .andExpect(jsonPath("$[0].reason").value("near-duplicate"));
    }

    @Test
    void submitWithoutRoleEmphasisReturns202() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/applications/submit")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jdText\":\"x\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void submitReturns202AndStartsJob() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/applications/submit")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jdText\":\"a real jd\",\"roleEmphasis\":\"backend\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists());

        verify(jobStore).start(any(), eq(userId));
    }

    @Test
    void pdfReturns404WhenNoBlob() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        Application a = new Application(); // pdfBlob null
        when(service.get(userId, id)).thenReturn(a);

        mvc.perform(get("/api/applications/{id}/pdf", id).with(user(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pdfFilenameIsLastNameRoleResume() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        Application a = new Application();
        a.setRole("Senior Backend Engineer");
        a.setPdfBlob(new byte[]{1});
        when(service.get(userId, id)).thenReturn(a);
        Profile p = new Profile();
        p.setName("Jordan Reyes");
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(p));

        mvc.perform(get("/api/applications/{id}/pdf", id).with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"Reyes_Senior_Backend_Engineer_resume.pdf\""));
    }

    @Test
    void resumeBaseHandlesSuffixesUnicodeAndFallbacks() {
        assertThat(ApplicationController.resumeBase("José Muñoz Jr.", "C++ / Go Dev", "Acme"))
                .isEqualTo("Munoz_C_Go_Dev_resume");
        assertThat(ApplicationController.resumeBase("Jordan Reyes", " ", "Acme, Inc."))
                .isEqualTo("Reyes_Acme_Inc_resume");
        assertThat(ApplicationController.resumeBase("", null, null)).isEqualTo("resume");
        assertThat(ApplicationController.resumeBase(null, "SWE \"Intern\"", null)).isEqualTo("SWE_Intern_resume");
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();

        mvc.perform(delete("/api/applications/{id}", id).with(user(userId)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete(userId, id);
    }
}
