package com.resumepipeline.api;

import com.resumepipeline.github.GithubService;
import com.resumepipeline.github.RepoExplorer;
import com.resumepipeline.llm.LlmUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GithubController.class)
@Import(ApiExceptionHandler.class)
class GithubControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GithubService github;
    @MockitoBean RepoExplorer explorer;
    @MockitoBean JobProgressStore jobStore;
    @MockitoBean LlmUsageService usage;

    @Test
    void callbackWithoutMatchingSessionStateNeverConnects() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("github.oauth.state", "expected");

        mvc.perform(get("/api/github/callback").session(session).with(user(UUID.randomUUID()))
                        .param("code", "c").param("state", "forged").param("installation_id", "1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/settings?github=error"));

        verify(github, never()).connect(any(), any(), any());
    }

    @Test
    void connectBindsStateThatCallbackThenAccepts() throws Exception {
        UUID userId = UUID.randomUUID();
        when(github.authorizeUrl(anyString())).thenAnswer(i -> "https://github.com/login/oauth/authorize?client_id=x&state=" + i.getArgument(0));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/api/github/connect").session(session).with(user(userId)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://github.com/login/oauth/authorize")));
        String state = (String) session.getAttribute("github.oauth.state");

        mvc.perform(get("/api/github/callback").session(session).with(user(userId))
                        .param("code", "c").param("state", state).param("installation_id", "1"))
                .andExpect(header().string("Location", "/settings?github=connected"));

        verify(github).connect(userId, "c", 1L);
        // One-time: replaying the same state fails.
        mvc.perform(get("/api/github/callback").session(session).with(user(userId))
                        .param("code", "c").param("state", state))
                .andExpect(header().string("Location", "/settings?github=error"));
    }

    @Test
    void freshInstallWithoutStateRestartsOauthInsteadOfConnecting() throws Exception {
        mvc.perform(get("/api/github/callback").session(new MockHttpSession()).with(user(UUID.randomUUID()))
                        .param("code", "c").param("installation_id", "1").param("setup_action", "install"))
                .andExpect(header().string("Location", "/api/github/connect"));

        verify(github, never()).connect(any(), any(), any());
    }

    @Test
    void authorizedButNotInstalledGoesToInstallPageWithFreshState() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("github.oauth.state", "s1");
        when(github.connect(userId, "c", null)).thenThrow(new com.resumepipeline.github.GithubException.NoInstallation());
        when(github.installUrl(anyString())).thenAnswer(i -> "https://github.com/apps/x/installations/new?state=" + i.getArgument(0));

        mvc.perform(get("/api/github/callback").session(session).with(user(userId))
                        .param("code", "c").param("state", "s1"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://github.com/apps/x/installations/new?state=")));

        Object fresh = session.getAttribute("github.oauth.state");
        org.junit.jupiter.api.Assertions.assertNotNull(fresh);
        org.junit.jupiter.api.Assertions.assertNotEquals("s1", fresh);
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/github/status")).andExpect(status().isUnauthorized());
    }
}
