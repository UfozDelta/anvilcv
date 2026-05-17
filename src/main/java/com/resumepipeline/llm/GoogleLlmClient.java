package com.resumepipeline.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GoogleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleLlmClient.class);

    private final Client client;
    private final String generateModel;
    private final String matchModel;
    private final String cleanJdModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleLlmClient(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model.generate}") String generateModel,
            @Value("${llm.model.match}") String matchModel,
            @Value("${llm.model.clean-jd}") String cleanJdModel) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.generateModel = generateModel;
        this.matchModel = matchModel;
        this.cleanJdModel = cleanJdModel;
    }

    // -------- generateBullets --------

    @Override
    public BulletGenerationResult generateBullets(GenerateBulletsRequest req, ProgressLog progress) {
        boolean experience = req.kind() == SourceKind.EXPERIENCE;

        String contextBlock = experience
                ? """
                Role:     %s
                Company:  %s
                Location: %s
                Dates:    %s

                Description of work (what was built, with what tech, at what scale):
                %s
                """.formatted(nz(req.title()), nz(req.company()), nz(req.location()), nz(req.dates()), nz(req.description()))
                : """
                Project name: %s

                Project description:
                %s
                """.formatted(nz(req.projectName()), nz(req.description()));

        String repoBlock = req.repoContext() == null || req.repoContext().isBlank()
                ? ""
                : "\nRepo context (README + file listing):\n" + req.repoContext();

        String countTarget = experience ? "8 to 12" : "4 to 6";
        String sourceWord  = experience ? "ROLE" : "PROJECT";

        String lens = CategoryLenses.lensFor(req.category());
        String lensBlock = lens == null ? "" : "\n─────────────────────────────────────────────────────────────\n## 0. CATEGORY LENS (read this FIRST)\n\n" + lens + "\n";

        String prompt = lensBlock + """
                You are writing resume bullet points for a %s.
                Produce %s bullets in JSON. EVERY rule below is mandatory.

                ─────────────────────────────────────────────────────────────
                ## 1. LENGTH — line-filling discipline (CRITICAL)

                Each bullet must compile to EITHER exactly 1 full line OR exactly 2 full lines on the
                rendered resume. NEVER produce a bullet that overflows by a few words into a sparse
                second line — that looks broken.

                Targets (after \\textbf{} expansion):
                  • 1-line bullet: roughly 22 to 26 words (≈ 130 chars including spaces).
                  • 2-line bullet: roughly 42 to 50 words (≈ 250 chars including spaces).
                  • NEVER produce a bullet of 27-40 words — that range half-fills line 2.

                Default to 2-line bullets where the substance warrants it; reserve 1-liners for crisp
                accomplishments. Aim for a mix.

                ## 2. FORMAT — Google XYZ pattern

                Every bullet reads as:
                  [STRONG ACTION VERB] + [WHAT was built] + [at WHAT SCALE] + [with WHAT OUTCOME].

                Strong verbs only — open each bullet with one of:
                  Built · Designed · Shipped · Engineered · Owned · Led · Authored ·
                  Implemented · Architected · Stood up · Migrated · Hardened · Integrated.

                Forbidden openers: "Worked on", "Helped with", "Was responsible for", "Assisted",
                "Contributed to", "Collaborated on" — these are passive and weak.

                EVERY bullet ends with a period.

                ## 3. BOLD — **double asterisks** (compiles to \\textbf{})

                Aim for **3 to 6 bolds per bullet**. Bold everything in these categories:

                  (a) Every quantity / scale / metric:
                      **64K**, **500+**, **300ms**, **sub-200ms**, **95%%+**, **$200K**,
                      **11.5MB**, **2-3%%**, **120K transactions/month**, **15+ features**

                  (b) Every marquee technology / framework / protocol / vendor:
                      **RAG**, **RRF-k fusion**, **React-Leaflet**, **MongoDB 2dsphere**,
                      **AES-256-GCM**, **Clerk JWT**, **PyTorch**, **Stripe**, **BetterAuth**,
                      **WebRTC**, **Next.js 16**, **D3.js**

                  (c) Signature systems / techniques you designed (the noun phrase that names the thing):
                      **sub-cent-precision credit ledger**, **3-tier fuzzy matching**,
                      **AST-based parser**, **5-role RBAC**, **hybrid three-store architecture**

                Do NOT bold: weak verbs, plain English nouns, generic adjectives, the action verb itself.

                ## 4. CONTENT RULES

                  • Quote anchor numbers from the description VERBATIM. NEVER fabricate metrics.
                    If the description doesn't have a number, omit it — don't invent one.
                  • NO internal identifiers (table names, function names, file paths, env-var names).
                    Those belong in interview answers, not on a resume.
                  • Each bullet stands alone — a recruiter must understand it in 5 seconds without
                    reading neighbors.
                  • Tag each bullet with 1 to 3 tags from this allowlist only:
                      backend, frontend, ai-ml, devops, data, systems, communication, mobile, security

                ─────────────────────────────────────────────────────────────
                ## EXAMPLES (study these — match this length, bold density, and ending punctuation)

                  ✓ Built a **RAG** pipeline over **64K** MLS listings with hybrid full-text + vector search, **RRF-k fusion**, and a semantic cache, cutting query latency under **300ms** and LLM calls by **40%%**.

                  ✓ Engineered a real-time geospatial pipeline over **64K live listings** using **React-Leaflet**, **Turf.js**, and **MongoDB 2dsphere** queries with viewport-aware fetching and marker diffing, cutting map re-render from **180ms to 70ms**.

                  ✓ Designed a **sub-cent-precision credit ledger** powering metered billing across AI, voice, and SMS usage, processing **120K transactions/month** through an append-only audit trail and idempotent **Stripe** webhook integration.

                  ✓ Encrypted all third-party OAuth and telephony tokens at rest with **AES-256-GCM**, eliminating plaintext credentials from the database across the multi-tenant platform.

                  ✗ Worked on backend stuff using various tools and got things faster.
                    (passive opener, no bolds, no metrics, no period-style impact)

                ─────────────────────────────────────────────────────────────
                ## %s CONTEXT

                %s%s
                """.formatted(sourceWord, countTarget, sourceWord, contextBlock, repoBlock);

        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "bullets", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder()
                                        .type(Type.Known.OBJECT)
                                        .properties(Map.of(
                                                "text", Schema.builder().type(Type.Known.STRING).build(),
                                                "tags", Schema.builder()
                                                        .type(Type.Known.ARRAY)
                                                        .items(Schema.builder().type(Type.Known.STRING).build())
                                                        .build()
                                        ))
                                        .required(List.of("text", "tags"))
                                        .build())
                                .build()
                ))
                .required(List.of("bullets"))
                .build();

        // Emit before the blocking LLM call so the user sees something immediately.
        progress.emit("Calling LLM for category: " + req.category() + "...");
        List<GeneratedBullet> kept = callAndFilter(prompt, schema, null, progress);

        // If we lost a lot of bullets to the word-count filter, retry once with sharper instructions.
        int target = experience ? 8 : 4;
        if (kept.size() < target) {
            String retryPrompt = prompt + """

                    ─────────────────────────────────────────────────────────────
                    ## RETRY NOTE

                    The previous attempt produced too many bullets in the FORBIDDEN 27-40 word range.
                    Every bullet must be EITHER 22-26 words (fits 1 line) OR 42-50 words (fills 2 lines).
                    Count words before emitting. Re-do the entire batch with this constraint enforced.
                    """;
            log.info("Word-count filter kept only {} bullets, retrying once.", kept.size());
            // Tell the user why we're calling LLM again.
            progress.emit("Only " + kept.size() + "/" + target + " bullets passed word-count filter — retrying with stricter prompt...");
            List<GeneratedBullet> retry = callAndFilter(retryPrompt, schema, kept, progress);
            if (retry.size() > kept.size()) kept = retry;
        }

        progress.emit("Saved " + kept.size() + " bullets for category: " + req.category());
        return new BulletGenerationResult(kept);
    }

    // progress param lets us emit per-bullet decisions as they happen.
    private List<GeneratedBullet> callAndFilter(String prompt, Schema schema,
                                                List<GeneratedBullet> previous, ProgressLog progress) {
        String json = call(generateModel, prompt, schema);
        BulletsEnvelope env;
        try {
            env = mapper.readValue(json, BulletsEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM bullet response: " + json, e);
        }
        List<GeneratedBullet> kept = new java.util.ArrayList<>();
        int dropped = 0;
        for (BulletJson b : env.bullets) {
            String text = ensureTerminalPeriod(b.text);
            int wc = wordCount(text);
            if (wc >= 27 && wc <= 40) {
                log.info("Dropped bullet (word count {} in dead zone 27-40): {}", wc, abbreviate(text));
                // Emit so the user sees exactly which bullet was cut and why.
                progress.emit("Cut (" + wc + "w, dead zone 27-40): \"" + abbreviate(text) + "\"");
                dropped++;
                continue;
            }
            if (wc < 12) {
                log.info("Dropped bullet (word count {} too short): {}", wc, abbreviate(text));
                progress.emit("Cut (" + wc + "w, too short): \"" + abbreviate(text) + "\"");
                dropped++;
                continue;
            }
            // Kept — show a short preview so the user can see what's passing the filter.
            progress.emit("Kept (" + wc + "w): \"" + abbreviate(text) + "\"");
            kept.add(new GeneratedBullet(text, b.tags == null ? List.of() : b.tags));
        }
        log.info("Generation kept {} bullets, dropped {}.", kept.size(), dropped);
        return kept;
    }

    private static int wordCount(String s) {
        if (s == null || s.isBlank()) return 0;
        // Strip markdown bolds before counting so **64K** counts as one word, not three tokens.
        String stripped = s.replace("**", "");
        return stripped.trim().split("\\s+").length;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    }

    /** Trim and ensure terminal period. Bullets without a period look unfinished on a resume. */
    private static String ensureTerminalPeriod(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return t;
        char last = t.charAt(t.length() - 1);
        if (last == '.' || last == '!' || last == '?') return t;
        return t + ".";
    }

    // -------- cleanJd --------

    @Override
    public JdCleanResult cleanJd(String rawJd, ProgressLog progress) {
        progress.emit("Calling LLM to clean JD and extract keywords...");
        String prompt = """
                Clean this job description and extract structured fields.
                  - cleanJd: the JD text with navigation, marketing fluff, and "about us" boilerplate stripped. Keep responsibilities, requirements, and tech stack.
                  - company: the hiring company name.
                  - role: the job title.
                  - keywords: 8-20 specific technical keywords ATS systems would look for (technologies, frameworks, methodologies). No soft skills.

                Raw JD:
                %s
                """.formatted(rawJd);

        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "cleanJd",  Schema.builder().type(Type.Known.STRING).build(),
                        "company",  Schema.builder().type(Type.Known.STRING).build(),
                        "role",     Schema.builder().type(Type.Known.STRING).build(),
                        "keywords", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build()
                ))
                .required(List.of("cleanJd", "company", "role", "keywords"))
                .build();

        String json = call(cleanJdModel, prompt, schema);
        try {
            JdCleanEnvelope env = mapper.readValue(json, JdCleanEnvelope.class);
            List<String> kws = env.keywords == null ? List.of() : env.keywords;
            // Emit what we extracted so the user can see the parsed role/company immediately.
            progress.emit("Extracted: role=" + env.role + ", company=" + env.company
                    + ", " + kws.size() + " keywords: " + String.join(", ", kws));
            return new JdCleanResult(env.cleanJd, env.company, env.role, kws);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM cleanJd response: " + json, e);
        }
    }

    // -------- match --------

    @Override
    public MatchResult match(MatchRequest req, ProgressLog progress) {
        progress.emit("Calling LLM to rank " + req.bullets().size() + " bullets against JD...");
        StringBuilder bulletsBlock = new StringBuilder();
        for (BulletForMatch b : req.bullets()) {
            bulletsBlock.append("  - id=").append(b.bulletId())
                    .append(" project=").append(b.projectName())
                    .append(" tags=").append(b.tags())
                    .append("\n    text: ").append(b.text()).append("\n");
        }

        String prompt = """
                You are an expert resume writer. Rank EVERY bullet below against the job description
                and write a cover letter.

                Rank ALL %d bullets from rank 1 (best fit) to %d (worst). Use integers, no ties.
                For each bullet give a one-sentence "why" tying it to specific JD requirements.

                Then write a cover letter (3-4 short paragraphs):
                  - Open by naming the company and role.
                  - Reference 2-3 of the top-ranked bullets in plain prose (do not list them).
                  - Close with a brief, confident call to action.
                  - No "Dear Hiring Manager" — start "Hi %s team," or similar.

                Finally produce atsMatched (keywords from the JD that appear in the top 8 bullets)
                and atsMissing (JD keywords NOT covered).

                Role emphasis: %s
                Company: %s

                Cleaned JD:
                %s

                Keywords from JD:
                %s

                Bullets:
                %s
                """.formatted(
                        req.bullets().size(), req.bullets().size(),
                        req.company() == null ? "the" : req.company(),
                        req.roleEmphasis(),
                        req.company(),
                        req.cleanJd(),
                        req.keywords(),
                        bulletsBlock);

        Schema rankedItem = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "bulletId", Schema.builder().type(Type.Known.STRING).build(),
                        "rank",     Schema.builder().type(Type.Known.INTEGER).build(),
                        "why",      Schema.builder().type(Type.Known.STRING).build()
                ))
                .required(List.of("bulletId", "rank", "why"))
                .build();

        Schema stringArray = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).build())
                .build();

        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "rankedBullets", Schema.builder().type(Type.Known.ARRAY).items(rankedItem).build(),
                        "coverLetter",   Schema.builder().type(Type.Known.STRING).build(),
                        "atsMatched",    stringArray,
                        "atsMissing",    stringArray
                ))
                .required(List.of("rankedBullets", "coverLetter", "atsMatched", "atsMissing"))
                .build();

        String json = call(matchModel, prompt, schema);
        try {
            MatchEnvelope env = mapper.readValue(json, MatchEnvelope.class);
            List<RankedBullet> ranked = env.rankedBullets.stream()
                    .map(r -> new RankedBullet(r.bulletId, r.rank, r.why))
                    .toList();
            // Emit top-10 rankings so the user can see which bullets scored best while the rest of the pipeline runs.
            ranked.stream()
                    .sorted(java.util.Comparator.comparingInt(RankedBullet::rank))
                    .limit(10)
                    .forEach(r -> {
                        // Find matching bullet text for a readable preview.
                        String preview = req.bullets().stream()
                                .filter(b -> b.bulletId().equals(r.bulletId()))
                                .map(b -> abbreviate(b.text()))
                                .findFirst().orElse(r.bulletId());
                        progress.emit("Rank #" + r.rank() + ": \"" + preview + "\" — " + r.why());
                    });
            List<String> atsMatched = env.atsMatched == null ? List.of() : env.atsMatched;
            List<String> atsMissing = env.atsMissing == null ? List.of() : env.atsMissing;
            progress.emit("ATS matched: " + String.join(", ", atsMatched));
            if (!atsMissing.isEmpty()) {
                progress.emit("ATS missing: " + String.join(", ", atsMissing));
            }
            return new MatchResult(ranked, env.coverLetter, atsMatched, atsMissing);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM match response: " + json, e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // -------- shared --------

    private String call(String model, String prompt, Schema schema) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();
        GenerateContentResponse resp = client.models.generateContent(model, prompt, config);
        String json = resp.text();
        log.debug("LLM {} raw: {}", model, json);
        return json;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BulletsEnvelope { public List<BulletJson> bullets; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BulletJson { public String text; public List<String> tags; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JdCleanEnvelope {
        public String cleanJd; public String company; public String role; public List<String> keywords;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MatchEnvelope {
        public List<RankedItemJson> rankedBullets;
        public String coverLetter;
        public List<String> atsMatched;
        public List<String> atsMissing;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RankedItemJson { public String bulletId; public int rank; public String why; }
}
