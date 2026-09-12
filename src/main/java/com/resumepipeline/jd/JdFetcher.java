package com.resumepipeline.jd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class JdFetcher {

    private static final Logger log = LoggerFactory.getLogger(JdFetcher.class);

    private static final int TIMEOUT_MS = 15_000;
    private static final String UA =
            "Mozilla/5.0 (resume-pipeline; +https://github.com)";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String fetch(String url) {
        long start = System.currentTimeMillis();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            // ATS platforms (Greenhouse, Lever, Workday, LinkedIn) embed the job posting as
            // schema.org JobPosting JSON-LD even when the visible DOM is JS-rendered/empty.
            // That structured description is cleaner and more reliable than scraping body text.
            String jsonLd = extractJobPostingDescription(doc);
            String result;
            if (jsonLd != null && !jsonLd.isBlank()) {
                result = jsonLd;
            } else {
                doc.select("script, style, nav, footer, header, noscript").remove();
                String text = doc.body() == null ? doc.text() : doc.body().text();
                result = text == null ? "" : text;
            }
            log.info("JD_FETCH host={} chars={} ms={}", safeHost(url), result.length(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch JD from " + url + ": " + e.getMessage(), e);
        }
    }

    /** url is caller-supplied; a raw log of it would be the same injection vector X-Request-Id
     * is. URI parsing rejects the characters that matter (\n, control chars), so the host it
     * extracts is safe to log even when the rest of the URL isn't. */
    private static String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "unknown" : host;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Looks for a schema.org JobPosting in any {@code <script type="application/ld+json">} block. */
    private String extractJobPostingDescription(Document doc) {
        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            try {
                JsonNode node = MAPPER.readTree(script.data());
                String desc = findJobPostingDescription(node);
                if (desc != null && !desc.isBlank()) {
                    return Jsoup.parse(desc).text();
                }
            } catch (Exception ignored) {
                // Malformed/unrelated JSON-LD block — fall through to the next one.
            }
        }
        return null;
    }

    private String findJobPostingDescription(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode item : node) {
                String d = findJobPostingDescription(item);
                if (d != null) return d;
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                String d = findJobPostingDescription(graph);
                if (d != null) return d;
            }
            JsonNode type = node.get("@type");
            boolean isJobPosting = type != null
                    && (type.isTextual() ? "JobPosting".equalsIgnoreCase(type.asText())
                                          : anyEquals(type, "JobPosting"));
            if (isJobPosting) {
                JsonNode desc = node.get("description");
                if (desc != null && desc.isTextual()) return desc.asText();
            }
        }
        return null;
    }

    private boolean anyEquals(JsonNode arrayNode, String value) {
        if (!arrayNode.isArray()) return false;
        for (JsonNode n : arrayNode) {
            if (n.isTextual() && value.equalsIgnoreCase(n.asText())) return true;
        }
        return false;
    }
}
