package com.resumepipeline.jd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class JdFetcher {

    private static final Logger log = LoggerFactory.getLogger(JdFetcher.class);

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String UA =
            "Mozilla/5.0 (resume-pipeline; +https://github.com)";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param structured true when the text came from a schema.org JobPosting, i.e. it is
     *                   already just the posting and needs no LLM cleanup.
     */
    public record Fetched(String text, boolean structured) {}

    public Fetched fetch(String url) {
        long start = System.currentTimeMillis();
        try {
            Document doc = get(url);

            // ATS platforms (Greenhouse, Lever, Workday, LinkedIn) embed the job posting as
            // schema.org JobPosting JSON-LD even when the visible DOM is JS-rendered/empty.
            // That structured description is cleaner and more reliable than scraping body text.
            String jsonLd = extractJobPostingDescription(doc);
            String result;
            boolean structured = jsonLd != null && !jsonLd.isBlank();
            if (structured) {
                result = jsonLd;
            } else {
                doc.select("script, style, nav, footer, header, noscript").remove();
                String text = doc.body() == null ? doc.text() : doc.body().text();
                result = text == null ? "" : text;
            }
            log.info("JD_FETCH host={} chars={} ms={}", safeHost(url), result.length(),
                    System.currentTimeMillis() - start);
            return new Fetched(result, structured);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch JD from " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Redirects are followed by hand so every hop is checked, not just the first URL.
     * Resolving here and again inside Jsoup leaves a DNS-rebinding gap; closing it needs a
     * pinned-IP connection, which Jsoup doesn't offer.
     */
    private Document get(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            requirePublicHttp(current);
            Connection.Response res = Jsoup.connect(current)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(false)
                    .execute();
            if (res.statusCode() >= 300 && res.statusCode() < 400) {
                String location = res.header("Location");
                if (location == null) throw new IOException("Redirect without Location");
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            return res.parse();
        }
        throw new IOException("Too many redirects");
    }

    /** Rejects non-http(s) URLs and any host that resolves to a loopback, private or link-local address. */
    static void requirePublicHttp(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid job URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Job URL must be http or https");
        }
        String host = uri.getHost();
        if (host == null) throw new IllegalArgumentException("Invalid job URL");
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Job URL host not found");
        }
        for (InetAddress a : addrs) {
            byte[] b = a.getAddress();
            boolean uniqueLocalV6 = b.length == 16 && (b[0] & 0xFE) == 0xFC;
            boolean cgnat = b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress() || uniqueLocalV6 || cgnat) {
                throw new IllegalArgumentException("Job URL must point to a public host");
            }
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
