package com.resumepipeline.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubClientTest {

    private static final String API = "https://api.test";
    private MockRestServiceServer server;
    private GithubClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GithubAppAuth auth = mock(GithubAppAuth.class);
        when(auth.appJwt()).thenReturn("app-jwt");
        when(auth.appId()).thenReturn("42");
        client = new GithubClient(builder, auth, API, "https://web.test");
    }

    private void expectToken() {
        server.expect(requestTo(API + "/app/installations/7/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer app-jwt"))
                // Narrowed to read-only regardless of what the app registration grants.
                .andExpect(jsonPath("$.permissions.contents").value("read"))
                .andExpect(jsonPath("$.permissions.metadata").value("read"))
                .andRespond(withSuccess("{\"token\":\"inst-tok\",\"expires_at\":\"" + Instant.now().plusSeconds(3600) + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    private static String repoJson(int n) {
        return "{\"full_name\":\"me/r" + n + "\",\"private\":true,\"default_branch\":\"main\",\"description\":null,\"pushed_at\":\"x\"}";
    }

    @Test
    void listsPrivateReposAcrossPagesWithOneCachedToken() {
        expectToken();
        StringBuilder page1 = new StringBuilder("{\"repositories\":[");
        for (int i = 0; i < 100; i++) page1.append(i == 0 ? "" : ",").append(repoJson(i));
        page1.append("]}");
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer inst-tok"))
                .andRespond(withSuccess(page1.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer inst-tok"))
                .andRespond(withSuccess("{\"repositories\":[" + repoJson(100) + "]}", MediaType.APPLICATION_JSON));

        List<GithubClient.Repo> repos = client.listRepos(7);

        assertEquals(101, repos.size());
        assertTrue(repos.get(0).isPrivate());
        assertEquals("", repos.get(0).description()); // null description normalized
        server.verify(); // token minted exactly once
    }

    @Test
    void uninstalledAppSurfacesAsNotConnected() {
        server.expect(requestTo(API + "/app/installations/7/access_tokens"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(GithubException.NotConnected.class, () -> client.listRepos(7));
    }

    @Test
    void rateLimitSurfacesWithRetryDelay() {
        expectToken();
        HttpHeaders h = new HttpHeaders();
        h.add("retry-after", "30");
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(h));
        GithubException.RateLimited e = assertThrows(GithubException.RateLimited.class, () -> client.listRepos(7));
        assertEquals(30, e.resetSeconds());
    }

    @Test
    void userInstallationsOnlyCountsThisApp() {
        server.expect(requestTo(API + "/user/installations?per_page=100"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-tok"))
                .andRespond(withSuccess("{\"installations\":["
                        + "{\"id\":1,\"app_id\":42,\"account\":{\"login\":\"me\"}},"
                        + "{\"id\":2,\"app_id\":99,\"account\":{\"login\":\"other-app\"}}]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(new GithubClient.Installation(1, "me")), client.userInstallations("user-tok"));
    }

    @Test
    void snapshotFollowsTarballRedirectWithoutLeakingToken() throws Exception {
        expectToken();
        HttpHeaders redirect = new HttpHeaders();
        redirect.setLocation(java.net.URI.create("https://codeload.test/me/app/tar.gz/abcdef1?token=signed"));
        server.expect(requestTo(API + "/repos/me/app/tarball/abcdef1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer inst-tok"))
                .andRespond(withStatus(HttpStatus.FOUND).headers(redirect));
        server.expect(requestTo("https://codeload.test/me/app/tar.gz/abcdef1?token=signed"))
                .andExpect(r -> assertNull(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andRespond(withSuccess(TarGz.of(java.util.Map.of("src/A.java", "class A {}")), MediaType.APPLICATION_OCTET_STREAM));

        RepoSnapshot s = client.snapshot(7, "me/app", "abcdef1");

        assertEquals("class A {}", s.read("src/A.java"));
        server.verify();
    }

    @Test
    void readFileRejectsPathTraversalSegmentsAndBadRepoNames() {
        expectToken();
        server.expect(requestTo(API + "/repos/me/r/contents/src/a%20b.java?ref=abcdef1"))
                .andRespond(withSuccess("class A {}", MediaType.TEXT_PLAIN));
        assertEquals("class A {}", client.readFile(7, "me/r", "../src/./a b.java", "abcdef1"));

        assertThrows(GithubException.class, () -> client.readFile(7, "me/r/../x", "a", "abcdef1"));
        assertThrows(GithubException.class, () -> client.readFile(7, "me/r", "a", "HEAD;rm"));
    }
}
