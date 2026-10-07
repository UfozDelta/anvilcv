package com.resumepipeline.api;

import com.resumepipeline.auth.AppUserDetailsService;
import com.resumepipeline.auth.SecurityConfig;
import com.resumepipeline.eval.EvalService;
import com.resumepipeline.eval.EvalSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.admin;
import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEvalController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@TestPropertySource(properties = "cors.allowed-origin=http://localhost:5173")
class AdminEvalControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AppUserDetailsService userDetailsService;
    @MockitoBean EvalService eval;

    @Test
    void nonAdminGets403OnEveryEvalRoute() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/admin/eval/sets").with(user(u))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/eval/snapshot").with(user(u))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/eval/generate").with(user(u))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/eval/compare").param("a", u.toString()).param("b", u.toString()).with(user(u)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/eval/sets/{id}", u).with(user(u))).andExpect(status().isForbidden());
        verifyNoInteractions(eval);
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/admin/eval/sets")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListSets() throws Exception {
        when(eval.list()).thenReturn(List.of());
        mvc.perform(get("/api/admin/eval/sets").with(admin(UUID.randomUUID()))).andExpect(status().isOk());
        verify(eval).list();
    }

    @Test
    void snapshotUsesTheCallingAdminsOwnBank() throws Exception {
        UUID admin = UUID.randomUUID();
        when(eval.snapshotBank(admin)).thenReturn(new EvalSet("bank-x", EvalSet.BASELINE, null, EvalSet.DONE));
        mvc.perform(post("/api/admin/eval/snapshot").with(admin(admin))).andExpect(status().isOk());
        verify(eval).snapshotBank(admin);
    }
}
