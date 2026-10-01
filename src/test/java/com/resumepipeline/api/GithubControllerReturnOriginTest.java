package com.resumepipeline.api;

import com.resumepipeline.github.GithubService;
import com.resumepipeline.github.RepoExplorer;
import com.resumepipeline.llm.LlmUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.resumepipeline.api.WebTestSecurity.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Split deploy: the callback runs on the API host but must land the browser on the frontend's domain. */
@WebMvcTest(GithubController.class)
@Import(ApiExceptionHandler.class)
@TestPropertySource(properties = "github.return-origin=https://app.example.com/")
class GithubControllerReturnOriginTest {

    @Autowired MockMvc mvc;
    @MockitoBean GithubService github;
    @MockitoBean RepoExplorer explorer;
    @MockitoBean JobProgressStore jobStore;
    @MockitoBean LlmUsageService usage;

    @Test
    void callbackReturnsToFrontendOrigin() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("github.oauth.state", "s1");

        mvc.perform(get("/api/github/callback").session(session).with(user(userId))
                        .param("code", "c").param("state", "s1"))
                .andExpect(header().string("Location", "https://app.example.com/settings?github=connected"));

        mvc.perform(get("/api/github/callback").session(new MockHttpSession()).with(user(userId))
                        .param("code", "c").param("state", "forged"))
                .andExpect(header().string("Location", "https://app.example.com/settings?github=error"));
    }
}
