package com.resumepipeline.api;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletService;
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

@WebMvcTest(BulletController.class)
@Import(ApiExceptionHandler.class)
class BulletControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean BulletService bullets;

    @Test
    void listForProjectForwardsUserId() throws Exception {
        UUID userId = UUID.randomUUID(), proj = UUID.randomUUID();
        when(bullets.listForProject(userId, proj)).thenReturn(List.of());

        mvc.perform(get("/api/projects/{p}/bullets", proj).with(user(userId)))
                .andExpect(status().isOk());

        verify(bullets).listForProject(userId, proj);
    }

    @Test
    void createRejectsBlankTextWith400() throws Exception {
        UUID userId = UUID.randomUUID(), proj = UUID.randomUUID();

        mvc.perform(post("/api/projects/{p}/bullets", proj)
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bullets);
    }

    @Test
    void updateForwardsOwnershipAndReturns404WhenMissing() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();
        when(bullets.update(eq(userId), eq(id), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Bullet not found"));

        mvc.perform(put("/api/bullets/{id}", id)
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteForwardsUserId() throws Exception {
        UUID userId = UUID.randomUUID(), id = UUID.randomUUID();

        mvc.perform(delete("/api/bullets/{id}", id).with(user(userId)).with(csrf()))
                .andExpect(status().isOk());

        verify(bullets).delete(userId, id);
    }
}
