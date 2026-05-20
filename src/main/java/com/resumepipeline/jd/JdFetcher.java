package com.resumepipeline.jd;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JdFetcher {

    private static final int TIMEOUT_MS = 15_000;
    private static final String UA =
            "Mozilla/5.0 (resume-pipeline; +https://github.com)";

    public String fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            doc.select("script, style, nav, footer, header, noscript").remove();
            String text = doc.body() == null ? doc.text() : doc.body().text();
            return text == null ? "" : text;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch JD from " + url + ": " + e.getMessage(), e);
        }
    }
}
