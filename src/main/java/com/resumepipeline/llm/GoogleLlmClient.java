package com.resumepipeline.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.PipelineTimer;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class GoogleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleLlmClient.class);

    private final Client client;
    private final String generateModel;
    private final String matchModel;
    private final String cleanJdModel;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GenerationConfigService configService;

    public GoogleLlmClient(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model.generate}") String generateModel,
            @Value("${llm.model.match}") String matchModel,
            @Value("${llm.model.clean-jd}") String cleanJdModel,
            GenerationConfigService configService) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.generateModel = generateModel;
        this.matchModel = matchModel;
        this.cleanJdModel = cleanJdModel;
        this.configService = configService;
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

        GenerationConfig cfg = configService.get(req.userId());

        String lens = CategoryLenses.lensFor(req.category());
        String lensBlock = lens == null ? "" : "\n─────────────────────────────────────────────────────────────\n## 0. CATEGORY LENS (read this FIRST)\n\n" + lens + "\n";

        String toneInstruction = switch (cfg.getTone()) {
            case CONSERVATIVE -> "Write in a precise, understated tone. Avoid hyperbole. Let the metrics speak.";
            case AGGRESSIVE   -> "Write with a confident, high-impact tone. Emphasise scale, speed, and results aggressively.";
            default           -> "";
        };
        String boldInstruction = switch (cfg.getBoldDensity()) {
            case NONE  -> "Do NOT use any **bold** markup in bullets.";
            case HEAVY -> "Bold aggressively — every metric, every technology, every named system or technique.";
            default    -> "";
        };
        String verbInstruction = switch (cfg.getActionVerbStyle()) {
            case LEADERSHIP -> "Prefer leadership verbs: Led, Owned, Directed, Coordinated, Mentored, Drove, Championed.";
            case IMPACT     -> "Prefer impact verbs: Accelerated, Reduced, Eliminated, Boosted, Saved, Cut, Scaled.";
            default         -> "";
        };
        String tuningBlock = (toneInstruction + boldInstruction + verbInstruction).isBlank() ? "" :
                "\n─────────────────────────────────────────────────────────────\n## STYLE OVERRIDES\n\n"
                + (toneInstruction.isBlank() ? "" : toneInstruction + "\n")
                + (boldInstruction.isBlank() ? "" : boldInstruction + "\n")
                + (verbInstruction.isBlank() ? "" : verbInstruction + "\n");

        String prompt = lensBlock + tuningBlock + """
                You are writing resume bullet points for a %s.
                Produce %s bullets in JSON. EVERY rule below is mandatory.

                ─────────────────────────────────────────────────────────────
                ## 1. LENGTH — line-filling discipline (CRITICAL)

                Each bullet must compile to EITHER exactly 1 full line OR exactly 2 full lines on the
                rendered resume. NEVER produce a bullet that overflows by a few words into a sparse
                second line — that looks broken.

                Targets (after \\textbf{} expansion):
                  • 1-line bullet: roughly %d to %d words (≈ 130 chars including spaces).
                  • 2-line bullet: roughly %d to %d words (≈ 250 chars including spaces).
                  • NEVER produce a bullet of %d-%d words — that range half-fills line 2.

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
                """.formatted(sourceWord, countTarget,
                        cfg.getSingleLineLow(), cfg.getSingleLineHigh(),
                        cfg.getDoubleLineLow(), cfg.getDoubleLineHigh(),
                        cfg.getDeadZoneLow(), cfg.getDeadZoneHigh(),
                        sourceWord, contextBlock, repoBlock);

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

        // Show first meaningful line of the lens so user knows what angle the LLM is targeting.
        if (lens != null) {
            String lensFirstLine = lens.lines()
                    .map(String::strip)
                    .filter(l -> !l.isBlank() && !l.startsWith("LENS:"))
                    .findFirst().orElse("");
            if (!lensFirstLine.isBlank()) progress.emit("Lens: " + lensFirstLine);
        }

        progress.emit("Calling LLM for category: " + req.category() + "...");
        int target = experience ? 8 : 4;
        List<GeneratedBullet> kept = callAndFilter(prompt, schema, target, cfg, progress);

        // If we lost too many bullets to the word-count filter, retry once with sharper instructions.
        if (cfg.isWordFilterEnabled() && kept.size() < target) {
            int firstPassCount = kept.size();
            String retryPrompt = prompt + ("\n─────────────────────────────────────────────────────────────\n"
                    + "## RETRY NOTE\n\n"
                    + "The previous attempt produced too many bullets in the FORBIDDEN %d-%d word range.\n"
                    + "Every bullet must be EITHER %d-%d words (fits 1 line) OR %d-%d words (fills 2 lines).\n"
                    + "Count words before emitting. Re-do the entire batch with this constraint enforced.\n"
            ).formatted(cfg.getDeadZoneLow(), cfg.getDeadZoneHigh(),
                    cfg.getSingleLineLow(), cfg.getSingleLineHigh(),
                    cfg.getDoubleLineLow(), cfg.getDoubleLineHigh());
            log.info("Word-count filter kept only {} bullets, retrying once.", kept.size());
            progress.emit("Retry: only " + firstPassCount + "/" + target + " passed filter, calling LLM again with stricter length rules...");
            List<GeneratedBullet> retry = callAndFilter(retryPrompt, schema, target, cfg, progress);
            if (retry.size() > kept.size()) {
                progress.emit("Retry result: " + retry.size() + "/" + target + " passed (was " + firstPassCount + "/" + target + ")");
                kept = retry;
            } else {
                progress.emit("Retry result: no improvement (" + retry.size() + "/" + target + "), keeping first-pass output");
            }
        }

        progress.emit("Saved " + kept.size() + " bullets for category: " + req.category());
        return new BulletGenerationResult(kept);
    }

    // progress param lets us emit per-bullet filter decisions without exposing bullet text.
    private List<GeneratedBullet> callAndFilter(String prompt, Schema schema,
                                                int target, GenerationConfig cfg, ProgressLog progress) {
        String json = call(generateModel, prompt, schema, cfg.getTemperature());
        BulletsEnvelope env;
        try {
            env = mapper.readValue(json, BulletsEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM bullet response: " + json, e);
        }
        int total = env.bullets == null ? 0 : env.bullets.size();
        if (cfg.isWordFilterEnabled()) {
            progress.emit("LLM returned " + total + " bullets, filtering by word count (target: " + target + ")...");
        } else {
            progress.emit("LLM returned " + total + " bullets (word filter disabled, keeping all)...");
        }

        List<GeneratedBullet> kept = new java.util.ArrayList<>();
        int dropped = 0;
        for (BulletJson b : env.bullets) {
            String text = ensureTerminalPeriod(b.text);
            int wc = wordCount(text);
            if (cfg.isWordFilterEnabled()) {
                if (wc >= cfg.getDeadZoneLow() && wc <= cfg.getDeadZoneHigh()) {
                    log.info("Dropped bullet (word count {} in dead zone {}-{}): {}", wc,
                            cfg.getDeadZoneLow(), cfg.getDeadZoneHigh(), abbreviate(text));
                    progress.emit("Cut: " + wc + "w - dead zone (" + cfg.getDeadZoneLow() + "-" + cfg.getDeadZoneHigh()
                            + "), needs " + cfg.getSingleLineLow() + "-" + cfg.getSingleLineHigh()
                            + " or " + cfg.getDoubleLineLow() + "-" + cfg.getDoubleLineHigh());
                    dropped++;
                    continue;
                }
                if (wc < cfg.getMinWordFloor()) {
                    log.info("Dropped bullet (word count {} too short, floor {}): {}", wc, cfg.getMinWordFloor(), abbreviate(text));
                    progress.emit("Cut: " + wc + "w - too short (min " + cfg.getMinWordFloor() + ")");
                    dropped++;
                    continue;
                }
            }
            List<String> tags = b.tags == null ? List.of() : b.tags;
            progress.emit("Kept: " + wc + "w [" + String.join(", ", tags) + "]");
            kept.add(new GeneratedBullet(text, tags));
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

    // -------- rankBullets --------

    @Override
    public RankResult rankBullets(RankRequest req, ProgressLog progress) {
        progress.emit("Calling LLM to rank " + req.bullets().size() + " bullets against JD...");
        StringBuilder bulletsBlock = new StringBuilder();
        for (BulletForMatch b : req.bullets()) {
            bulletsBlock.append("  - id=").append(b.bulletId())
                    .append(" project=").append(b.projectName())
                    .append(" tags=").append(b.tags())
                    .append("\n    text: ").append(b.text()).append("\n");
        }

        String prompt = """
                You are an expert resume writer. Rank EVERY bullet below against the job description.

                Rank ALL %d bullets from rank 1 (best fit) to %d (worst). Use integers, no ties.
                For each bullet give a one-sentence "why" tying it to specific JD requirements.

                Produce atsMatched (keywords from the JD that appear in the top 8 bullets)
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
                        "atsMatched",    stringArray,
                        "atsMissing",    stringArray
                ))
                .required(List.of("rankedBullets", "atsMatched", "atsMissing"))
                .build();

        String json = callStreaming(matchModel, prompt, schema, 1.0, "Ranking", progress);
        try {
            RankEnvelope env = mapper.readValue(json, RankEnvelope.class);
            List<RankedBullet> ranked = env.rankedBullets.stream()
                    .map(r -> new RankedBullet(r.bulletId, r.rank, r.why))
                    .toList();
            progress.emit("Top 4 ranked bullets:");
            ranked.stream()
                    .sorted(java.util.Comparator.comparingInt(RankedBullet::rank))
                    .limit(4)
                    .forEach(r -> {
                        String tags = req.bullets().stream()
                                .filter(b -> b.bulletId().equals(r.bulletId()))
                                .map(b -> String.join(", ", b.tags()))
                                .findFirst().orElse("");
                        String tagsStr = tags.isBlank() ? "" : " [" + tags + "]";
                        progress.emit("Rank #" + r.rank() + tagsStr + " - " + r.why());
                    });
            List<String> atsMatched = env.atsMatched == null ? List.of() : env.atsMatched;
            List<String> atsMissing = env.atsMissing == null ? List.of() : env.atsMissing;
            progress.emit("ATS matched (" + atsMatched.size() + "): " + String.join(", ", atsMatched));
            if (!atsMissing.isEmpty()) {
                progress.emit("ATS missing (" + atsMissing.size() + "): " + String.join(", ", atsMissing));
            }
            return new RankResult(ranked, atsMatched, atsMissing);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM rank response: " + json, e);
        }
    }

    // -------- coverLetter --------

    @Override
    public String coverLetter(CoverLetterRequest req, ProgressLog progress) {
        progress.emit("Generating cover letter...");
        String bulletsBlock = req.topBulletTexts().stream()
                .map(t -> "  - " + t)
                .reduce("", (a, b) -> a + b + "\n");

        String prompt = """
                Write a cover letter for this job application.

                Guidelines:
                  - 3-4 short paragraphs.
                  - Open by naming the company and role.
                  - Reference 2-3 of the provided bullets in plain prose (do not list them verbatim).
                  - Close with a brief, confident call to action.
                  - No "Dear Hiring Manager" — start "Hi %s team," or similar.
                  - Plain text only, no markdown.

                Role emphasis: %s
                Company: %s

                Cleaned JD:
                %s

                Top selected bullets:
                %s
                """.formatted(
                        req.company() == null ? "the" : req.company(),
                        req.roleEmphasis(),
                        req.company(),
                        req.cleanJd(),
                        bulletsBlock);

        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of("coverLetter", Schema.builder().type(Type.Known.STRING).build()))
                .required(List.of("coverLetter"))
                .build();

        String json = callStreaming(matchModel, prompt, schema, 1.0, "Cover letter", progress);
        try {
            CoverLetterEnvelope env = mapper.readValue(json, CoverLetterEnvelope.class);
            progress.emit("Cover letter generated.");
            return env.coverLetter;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM cover letter response: " + json, e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // -------- shared --------

    private String call(String model, String prompt, Schema schema) {
        return call(model, prompt, schema, 1.0);
    }

    private String call(String model, String prompt, Schema schema, double temperature) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature((float) temperature)
                .build();
        // Run on a separate thread so we can enforce a hard 2-minute timeout.
        // Without this, a stalled LLM response blocks the virtual thread forever.
        try {
            PipelineTimer tLlm = PipelineTimer.start("LLM " + model + " (promptLen=" + prompt.length() + ")");
            String json = CompletableFuture
                    .supplyAsync(() -> {
                        GenerateContentResponse resp = client.models.generateContent(model, prompt, config);
                        return resp.text();
                    })
                    .get(120, TimeUnit.SECONDS);
            tLlm.stop("responseLen=" + (json == null ? 0 : json.length()));
            log.debug("LLM {} raw: {}", model, json);
            return json;
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM call timed out after 2 minutes — Gemini may be overloaded, try again.", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        }
    }

    private String callStreaming(String model, String prompt, Schema schema,
                                 double temperature, String label, ProgressLog progress) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature((float) temperature)
                .build();
        try {
            PipelineTimer tLlm = PipelineTimer.start("LLM stream " + model + " (promptLen=" + prompt.length() + ")");
            String json = CompletableFuture
                    .supplyAsync(() -> {
                        ResponseStream<GenerateContentResponse> stream =
                                client.models.generateContentStream(model, prompt, config);
                        StringBuilder sb = new StringBuilder();
                        int chunks = 0;
                        for (GenerateContentResponse chunk : stream) {
                            String t = chunk.text();
                            if (t != null) sb.append(t);
                            chunks++;
                            if (chunks % 10 == 0) {
                                // Rough word count — strip ** so bold tokens don't inflate count.
                                int words = sb.toString().replace("**", "").trim().split("\\s+").length;
                                progress.emit(label + "... (" + words + "w received)");
                            }
                        }
                        return sb.toString();
                    })
                    .get(120, TimeUnit.SECONDS);
            tLlm.stop("responseLen=" + (json == null ? 0 : json.length()));
            log.debug("LLM stream {} raw: {}", model, json);
            return json;
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM call timed out after 2 minutes — Gemini may be overloaded, try again.", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("LLM call failed: " + cause.getMessage(), cause);
        }
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
    private static class RankEnvelope {
        public List<RankedItemJson> rankedBullets;
        public List<String> atsMatched;
        public List<String> atsMissing;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RankedItemJson { public String bulletId; public int rank; public String why; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CoverLetterEnvelope { public String coverLetter; }
}
