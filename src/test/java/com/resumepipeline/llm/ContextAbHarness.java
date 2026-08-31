package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Manual A/B harness: same project, two extractor-output documents, one real generation
 * call each. Answers whether the richer extract actually produces better bullets, which
 * three rounds of validating the <em>document</em> could not.
 *
 * <p>Off by default -- it makes real Gemini calls. Run with:
 * <pre>AB_TEST=1 GEMINI_API_KEY=... AB_OLD=path AB_NEW=path mvn test -Dtest=ContextAbHarness</pre>
 */
class ContextAbHarness {

    /**
     * Pre-V23 heading map. Critically it does NOT own "notable technical decisions" or
     * "security & compliance posture" -- in the old shape those carry fold pointers into
     * hardestProblem/ownership, and parsing them with the new map would silently DELETE
     * that material from the old arm and flatter the new one.
     */
    private static final Map<String, String> HEADINGS_OLD = Map.ofEntries(
            Map.entry("tech stack", "techStack"),
            Map.entry("your role", "yourRole"),
            Map.entry("what you owned end-to-end", "ownership"),
            Map.entry("scale & impact", "scaleImpact"),
            Map.entry("hardest problem solved", "hardestProblem"),
            Map.entry("architecture overview", "description"));

    /** Section heading (lower-cased) -> the field it feeds, mirroring parseExtract.ts. */
    private static final Map<String, String> HEADINGS = Map.ofEntries(
            Map.entry("tech stack", "techStack"),
            Map.entry("your role", "yourRole"),
            Map.entry("what you owned end-to-end", "ownership"),
            Map.entry("scale & impact", "scaleImpact"),
            Map.entry("hardest problem solved", "hardestProblem"),
            Map.entry("notable technical decisions", "technicalDecisions"),
            Map.entry("users & business context", "userImpact"),
            Map.entry("security & compliance posture", "securityPosture"),
            Map.entry("architecture overview", "description"));

    /**
     * Parse an extractor document into fields. Handles both shapes: sections that own a
     * field via "AnvilCV field:", and sections routed by "fold into:" -- appended, exactly
     * as the real importer does, so an old-shape document lands the way it really would.
     */
    static Map<String, String> parse(String md, Map<String, String> headings) {
        Map<String, String> out = new LinkedHashMap<>();
        String field = null;
        boolean appending = false, awaitingFold = false;
        StringBuilder buf = new StringBuilder();

        for (String line : md.split("\r?\n")) {
            if (line.matches("\\s*#{1,6}\\s+\\S.*")) {
                flush(out, field, buf, appending);
                String key = line.replaceFirst("\\s*#{1,6}\\s+", "").trim().toLowerCase();
                field = headings.get(key);
                appending = false;
                awaitingFold = field == null;
                continue;
            }
            if (awaitingFold && !line.isBlank()) {
                var m = java.util.regex.Pattern
                        .compile("^\\s*→\\s*fold into:\\s*\\*\\*(\\w+)\\*\\*").matcher(line);
                awaitingFold = false;
                if (m.find() && headings.containsValue(m.group(1))) {
                    field = m.group(1);
                    appending = true;
                    continue;
                }
            }
            if (line.matches("\\s*→.*")) continue;   // pointer lines are not body text
            if (field != null) buf.append(line).append('\n');
        }
        flush(out, field, buf, appending);
        return out;
    }

    private static void flush(Map<String, String> out, String field, StringBuilder buf, boolean appending) {
        if (field != null) {
            String body = buf.toString().trim();
            if (!body.isEmpty()) {
                String prev = out.get(field);
                out.put(field, appending && prev != null ? prev + "\n\n" + body : body);
            }
        }
        buf.setLength(0);
    }

    private static LlmClient.GenerateBulletsRequest request(UUID user, Map<String, String> f, boolean full) {
        return new LlmClient.GenerateBulletsRequest(
                user, LlmClient.SourceKind.PROJECT, "backend",
                "AnvilCV", f.get("description"), null,
                f.get("techStack"), f.get("yourRole"), f.get("ownership"),
                f.get("scaleImpact"), f.get("hardestProblem"),
                // The three V23 columns exist only in the new shape.
                full ? f.get("technicalDecisions") : null,
                full ? f.get("userImpact") : null,
                full ? f.get("securityPosture") : null,
                null, null, null, null, List.of(), List.of());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AB_TEST", matches = "1")
    void compareOldAndNewContext() throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        Map<String, String> old = parse(Files.readString(Path.of(System.getenv("AB_OLD"))), HEADINGS_OLD);
        Map<String, String> neu = parse(Files.readString(Path.of(System.getenv("AB_NEW"))), HEADINGS);

        GenerationConfigService cfg = mock(GenerationConfigService.class);
        when(cfg.get(any())).thenReturn(new GenerationConfig());
        var llm = new GoogleLlmClient(key, "gemini-2.5-flash", "gemini-2.5-flash",
                "gemini-2.5-flash-lite", cfg);

        UUID user = UUID.randomUUID();
        for (var arm : new Object[][] {{"OLD (6 fields)", old, false}, {"NEW (9 fields)", neu, true}}) {
            @SuppressWarnings("unchecked") Map<String, String> f = (Map<String, String>) arm[1];
            boolean full = (boolean) arm[2];
            System.out.println("\n================ " + arm[0] + " ================");
            System.out.println("fields populated: " + new HashMap<>(f).keySet());
            System.out.println("context chars: " + f.values().stream().mapToInt(String::length).sum());
            var res = llm.generateBullets(request(user, f, full), ProgressLog.noOp(), new TokenAccumulator());
            res.bullets().forEach(b -> System.out.println("  • " + b.text()));
            System.out.println("  [" + res.bullets().size() + " bullets kept]");
        }
    }
}
