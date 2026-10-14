package com.resumepipeline.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GithubContextFetcher {

    private static final Logger log = LoggerFactory.getLogger(GithubContextFetcher.class);
    private static final int MAX_CHARS = 30_000;
    private static final Pattern REPO_PATTERN = Pattern.compile(
            "(?:https?://)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Returns README + file listing, or null if fetch fails or URL is blank. */
    public String fetch(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) return null;

        Matcher m = REPO_PATTERN.matcher(githubUrl.trim());
        if (!m.find()) {
            log.warn("Unrecognised GitHub URL: {}", githubUrl);
            return null;
        }
        String owner = m.group(1);
        String repo  = m.group(2);

        StringBuilder sb = new StringBuilder();

        String readme = fetchReadme(owner, repo);
        if (readme != null) {
            sb.append("README:\n").append(readme);
        }

        String tree = fetchFileTree(owner, repo);
        if (tree != null) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append("File listing:\n").append(tree);
        }

        if (sb.isEmpty()) return null;

        String result = sb.toString();
        return result.length() > MAX_CHARS ? result.substring(0, MAX_CHARS) : result;
    }

    private String fetchReadme(String owner, String repo) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/readme"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "resume-pipeline/1.0")
                    .timeout(java.time.Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonNode node = mapper.readTree(resp.body());
            String encoded = node.path("content").asText(null);
            if (encoded == null) return null;
            return new String(Base64.getMimeDecoder().decode(encoded));
        } catch (Exception e) {
            log.warn("Failed to fetch README for {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }

    private String fetchFileTree(String owner, String repo) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/HEAD"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "resume-pipeline/1.0")
                    .timeout(java.time.Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            JsonNode root = mapper.readTree(resp.body());
            JsonNode tree = root.path("tree");
            if (!tree.isArray()) return null;
            StringBuilder sb = new StringBuilder();
            for (JsonNode entry : tree) {
                sb.append(entry.path("path").asText()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to fetch file tree for {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }
}
