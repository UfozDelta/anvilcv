package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lens taxonomy is written down twice — {@link CategoryLenses#LENSES} here and CATEGORIES in
 * frontend/src/lib/api.ts — with no shared source. A desync is silent in both directions: the
 * frontend renders an unlabelled group for a slug it does not know, and the backend rejects an
 * unknown slug with a 400 from BulletService.generateBank. This test is the only thing that
 * notices.
 */
class CategoryLensesTest {

    private static final Path API_TS = Path.of("frontend", "src", "lib", "api.ts");

    @Test
    void frontendCategoriesMatchBackendLenses() throws IOException {
        String src = Files.readString(API_TS);
        int start = src.indexOf("export const CATEGORIES");
        assertTrue(start >= 0, "CATEGORIES array not found in " + API_TS);
        String block = src.substring(start, src.indexOf("];", start));

        Set<String> frontend = new LinkedHashSet<>();
        Matcher m = Pattern.compile("slug:\\s*'([^']+)'").matcher(block);
        while (m.find()) frontend.add(m.group(1));

        assertFalse(frontend.isEmpty(), "no slugs parsed out of CATEGORIES");
        assertEquals(CategoryLenses.LENSES.keySet(), frontend,
                "frontend CATEGORIES and backend LENSES have drifted apart");
    }

    @Test
    void lensesNameNoVendorTheSourceMustSupply() {
        // The lens says what to look FOR. Naming specific products here pushes the model to claim
        // them on projects that never used them, and BulletTextRules.fabricatedNumbers only
        // guards digits — an invented vendor passes every filter. Technique vocabulary
        // (idempotency, 2dsphere, AES-256-GCM) is deliberately still allowed.
        Pattern banned = Pattern.compile(
                "telnyx|vapi|trreb|betterauth|chromadb|groq|ollama|supabase|brevo|twilio|"
                        + "pytorch|huggingface|hcaptcha|beautifulsoup|vitest|playwright|flyway|"
                        + "prisma|hibernate|mongodb|postgresql|leaflet|tailwind",
                Pattern.CASE_INSENSITIVE);
        CategoryLenses.LENSES.forEach((slug, text) -> {
            Matcher m = banned.matcher(text);
            assertFalse(m.find(), "lens '" + slug + "' names a vendor the source may not have: "
                    + (m.hitEnd() ? "" : m.group()));
        });
    }
}
