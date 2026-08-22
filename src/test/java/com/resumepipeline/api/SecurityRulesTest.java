package com.resumepipeline.api;

import com.resumepipeline.auth.AppUserDetailsService;
import com.resumepipeline.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.admin;
import static com.resumepipeline.api.WebTestSecurity.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link SecurityConfig} filter chain: protected routes require
 * auth, the entry point returns 401 (not a redirect), authenticated requests pass.
 */
@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origin=http://localhost:5173")
class SecurityRulesTest {

    @Autowired MockMvc mvc;

    // SecurityConfig wires the DAO auth provider; the user-details service is not exercised
    // by these tests but must exist as a bean.
    @MockitoBean AppUserDetailsService userDetailsService;

    @Test
    void protectedRouteReturns401WhenUnauthenticated() throws Exception {
        mvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteSucceedsWhenAuthenticated() throws Exception {
        mvc.perform(get("/api/ping").with(user(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    // /api/admin/** used to be .authenticated(), so anyone who registered could read
    // every user's email and spend. These three pin the rule that closed that.

    @Test
    void adminRouteRejectsNonAdmin() throws Exception {
        mvc.perform(get("/api/admin/llm").with(user(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRouteRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/admin/llm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoutePassesAuthorizationForAdmin() throws Exception {
        // 404, not 403: authorization allowed the request through and only then did
        // handler mapping fail, because this slice registers PingController alone.
        mvc.perform(get("/api/admin/llm").with(admin(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
