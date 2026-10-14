package com.resumepipeline.api;

import com.resumepipeline.onboarding.OnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
@Import(ApiExceptionHandler.class)
class OnboardingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OnboardingService onboarding;

    @Test
    void getReturnsTheUsersProgress() throws Exception {
        UUID userId = UUID.randomUUID();
        when(onboarding.status(userId)).thenReturn(new OnboardingService.Status(false, 2, 5, 1, false));

        mvc.perform(get("/api/onboarding").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarded").value(false))
                .andExpect(jsonPath("$.entries").value(2))
                .andExpect(jsonPath("$.approvedBullets").value(5))
                .andExpect(jsonPath("$.applications").value(1))
                .andExpect(jsonPath("$.downloaded").value(false));
    }

    @Test
    void completeAndDownloadedRecordTheEventForTheCaller() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/onboarding/complete").with(user(userId)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/onboarding/downloaded").with(user(userId)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(onboarding).complete(userId);
        verify(onboarding).downloaded(userId);
    }
}
