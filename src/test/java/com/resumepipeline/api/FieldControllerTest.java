package com.resumepipeline.api;

import com.resumepipeline.field.Field;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FieldController.class)
@Import(ApiExceptionHandler.class)
class FieldControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProfileService profiles;

    @Test
    void getReturnsTheUsersFieldItsPresetAndWhatElseIsOffered() throws Exception {
        UUID userId = UUID.randomUUID();
        when(profiles.field(userId)).thenReturn(Field.MECHANICAL);

        mvc.perform(get("/api/field").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field").value("mechanical"))
                .andExpect(jsonPath("$.preset.skillLabels[0]").value("CAD & Modeling"))
                .andExpect(jsonPath("$.preset.lenses[0].slug").value("design"))
                .andExpect(jsonPath("$.available.length()").value(2));
    }

    @Test
    void putStoresTheChosenField() throws Exception {
        UUID userId = UUID.randomUUID();
        Profile saved = new Profile();
        saved.setField(Field.MECHANICAL);
        when(profiles.setField(userId, Field.MECHANICAL)).thenReturn(saved);

        mvc.perform(put("/api/field").with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"field\":\"mechanical\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field").value("mechanical"));

        verify(profiles).setField(userId, Field.MECHANICAL);
    }

    @Test
    void putRejectsATypoInsteadOfDefaulting() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(put("/api/field").with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"field\":\"mechancial\"}"))
                .andExpect(status().isBadRequest());

        verify(profiles, never()).setField(any(), any());
    }
}
