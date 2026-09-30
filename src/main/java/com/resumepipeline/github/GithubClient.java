package com.resumepipeline.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thin REST wrapper over the GitHub endpoints AnvilCV uses. Every repo call runs on an
 * installation token, so access is bounded to whatever repos the user granted the app —
 * there is no code path that can read a repo outside the user's own installation.
 *
 * <p>Installation tokens are requested with {@code contents:read} + {@code metadata:read}
 * only, even if the app registration grants more, and are cached in memory until 5 minutes
 * before they expire. They are never persisted or logged.
 */
@Component
public class GithubClient {

    private static final Pattern FULL_NAME = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final int MAX_REPO_PAGES = 10;

    public record Installation(long id, String accountLogin) {}
    public record Repo(String fullName, boolean isPrivate, String defaultBranch, String description, String pushedAt) {}
    public record TreeEntry(String path, long size) {}
    public record Tree(List<TreeEntry> entries, boolean truncated) {}
    public record SearchHit(String path, List<String> fragments) {}
    public record Commit(String sha, String author, String date, String message) {}

    private record CachedToken(String token, Instant expiresAt) {}

    private final RestClient rest;
    private final GithubAppAuth auth;
    private final String apiBase;
    private final String webBase;
    private final Map<Long, CachedToken> tokens = new ConcurrentHashMap<>();

    public GithubClient(RestClient.Builder builder, GithubAppAuth auth,
                        @Value("${github.api-base:https://api.github.com}") String apiBase,
                        @Value("${github.web-base:https://github.com}") String webBase) {
        this.rest = builder
                .defaultHeader(HttpHeaders.USER_AGENT, "anvilcv")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultStatusHandler(HttpStatusCode::isError, GithubClient::raise)
                .build();
        this.auth = auth;
        this.apiBase = apiBase;
        this.webBase = webBase;
    }

    // ---------------------------------------------------------------- user OAuth (connect only)

    /** Trades the OAuth callback code for a user token. The token is used once and dropped. */
    public String exchangeCode(String code) {
        JsonNode r = rest.post()
                .uri(URI.create(webBase + "/login/oauth/access_token"))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", auth.clientId(), "client_secret", auth.clientSecret(), "code", code))
                .retrieve().body(JsonNode.class);
        String token = r == null ? null : r.path("access_token").asText(null);
        if (token == null) throw new GithubException("GitHub rejected the authorization code");
        return token;
    }

    /** Installations of THIS app the user can access — proof the installation id is theirs. */
    public List<Installation> userInstallations(String userToken) {
        JsonNode r = rest.get()
                .uri(URI.create(apiBase + "/user/installations?per_page=100"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .accept(MediaType.valueOf("application/vnd.github+json"))
                .retrieve().body(JsonNode.class);
        List<Installation> out = new ArrayList<>();
        for (JsonNode i : r.path("installations")) {
            if (!auth.appId().equals(i.path("app_id").asText())) continue;
            out.add(new Installation(i.path("id").asLong(), i.path("account").path("login").asText()));
        }
        return out;
    }

    // ---------------------------------------------------------------- installation-scoped reads

    public List<Repo> listRepos(long installationId) {
        List<Repo> out = new ArrayList<>();
        for (int page = 1; page <= MAX_REPO_PAGES; page++) {
            JsonNode r = get(installationId, "/installation/repositories?per_page=100&page=" + page);
            JsonNode repos = r.path("repositories");
            for (JsonNode n : repos) {
                out.add(new Repo(n.path("full_name").asText(), n.path("private").asBoolean(),
                        n.path("default_branch").asText("main"), n.path("description").asText(""),
                        n.path("pushed_at").asText("")));
            }
            if (repos.size() < 100) break;
        }
        return out;
    }

    public String branchHead(long installationId, String fullName, String branch) {
        return get(installationId, "/repos/" + repo(fullName) + "/branches/" + UriUtils.encodePathSegment(branch, StandardCharsets.UTF_8))
                .path("commit").path("sha").asText();
    }

    public Tree tree(long installationId, String fullName, String sha) {
        JsonNode r = get(installationId, "/repos/" + repo(fullName) + "/git/trees/" + sha(sha) + "?recursive=1");
        List<TreeEntry> entries = new ArrayList<>();
        for (JsonNode n : r.path("tree")) {
            if ("blob".equals(n.path("type").asText())) {
                entries.add(new TreeEntry(n.path("path").asText(), n.path("size").asLong()));
            }
        }
        return new Tree(entries, r.path("truncated").asBoolean());
    }

    public String readFile(long installationId, String fullName, String path, String sha) {
        return rest.get()
                .uri(URI.create(apiBase + "/repos/" + repo(fullName) + "/contents/" + encodePath(path) + "?ref=" + sha(sha)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken(installationId))
                .accept(MediaType.valueOf("application/vnd.github.raw+json"))
                .retrieve().body(String.class);
    }

    /** GitHub code search — default branch only, and its own tighter rate limit. */
    public List<SearchHit> searchCode(long installationId, String fullName, String query) {
        String q = UriUtils.encodeQueryParam(query + " repo:" + repo(fullName), StandardCharsets.UTF_8);
        JsonNode r = rest.get()
                .uri(URI.create(apiBase + "/search/code?per_page=20&q=" + q))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken(installationId))
                .accept(MediaType.valueOf("application/vnd.github.text-match+json"))
                .retrieve().body(JsonNode.class);
        List<SearchHit> out = new ArrayList<>();
        for (JsonNode item : r.path("items")) {
            List<String> frags = new ArrayList<>();
            for (JsonNode m : item.path("text_matches")) frags.add(m.path("fragment").asText());
            out.add(new SearchHit(item.path("path").asText(), frags));
        }
        return out;
    }

    public List<Commit> commits(long installationId, String fullName, String sha, String path) {
        String uri = "/repos/" + repo(fullName) + "/commits?per_page=30&sha=" + sha(sha)
                + (path == null || path.isBlank() ? "" : "&path=" + UriUtils.encodeQueryParam(path, StandardCharsets.UTF_8));
        List<Commit> out = new ArrayList<>();
        for (JsonNode n : get(installationId, uri)) {
            JsonNode c = n.path("commit");
            String login = n.path("author").path("login").asText("");
            out.add(new Commit(n.path("sha").asText(),
                    login.isEmpty() ? c.path("author").path("name").asText() : login,
                    c.path("author").path("date").asText(), c.path("message").asText()));
        }
        return out;
    }

    // ---------------------------------------------------------------- plumbing

    private JsonNode get(long installationId, String pathAndQuery) {
        return rest.get()
                .uri(URI.create(apiBase + pathAndQuery))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken(installationId))
                .accept(MediaType.valueOf("application/vnd.github+json"))
                .retrieve().body(JsonNode.class);
    }

    String installationToken(long installationId) {
        CachedToken c = tokens.get(installationId);
        if (c != null && c.expiresAt().isAfter(Instant.now().plusSeconds(300))) return c.token();
        JsonNode r;
        try {
            r = rest.post()
                    .uri(URI.create(apiBase + "/app/installations/" + installationId + "/access_tokens"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.appJwt())
                    .accept(MediaType.valueOf("application/vnd.github+json"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("permissions", Map.of("contents", "read", "metadata", "read")))
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new GithubException.NotConnected(); // uninstalled on GitHub's side
        }
        CachedToken fresh = new CachedToken(r.path("token").asText(), Instant.parse(r.path("expires_at").asText()));
        tokens.put(installationId, fresh);
        return fresh.token();
    }

    void forget(long installationId) {
        tokens.remove(installationId);
    }

    private static void raise(org.springframework.http.HttpRequest req, ClientHttpResponse resp) throws java.io.IOException {
        int status = resp.getStatusCode().value();
        HttpHeaders h = resp.getHeaders();
        if (status == 429 || (status == 403 && ("0".equals(h.getFirst("x-ratelimit-remaining")) || h.getFirst("retry-after") != null))) {
            long wait = 60;
            if (h.getFirst("retry-after") != null) {
                wait = Long.parseLong(h.getFirst("retry-after"));
            } else if (h.getFirst("x-ratelimit-reset") != null) {
                wait = Math.max(1, Long.parseLong(h.getFirst("x-ratelimit-reset")) - Instant.now().getEpochSecond());
            }
            throw new GithubException.RateLimited(wait);
        }
        byte[] body = resp.getBody().readAllBytes();
        if (resp.getStatusCode().is4xxClientError()) {
            throw HttpClientErrorException.create(resp.getStatusCode(), resp.getStatusText(), h, body, StandardCharsets.UTF_8);
        }
        throw HttpServerErrorException.create(resp.getStatusCode(), resp.getStatusText(), h, body, StandardCharsets.UTF_8);
    }

    private static String repo(String fullName) {
        if (fullName == null || !FULL_NAME.matcher(fullName).matches()) {
            throw new GithubException("Invalid repository name: " + fullName);
        }
        return fullName;
    }

    private static String sha(String sha) {
        if (sha == null || !sha.matches("[0-9a-fA-F]{7,40}")) throw new GithubException("Invalid commit sha");
        return sha;
    }

    private static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isEmpty() || seg.equals("..") || seg.equals(".")) continue;
            if (!sb.isEmpty()) sb.append('/');
            sb.append(UriUtils.encodePathSegment(seg, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
