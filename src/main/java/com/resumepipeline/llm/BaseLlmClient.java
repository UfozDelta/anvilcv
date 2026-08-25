package com.resumepipeline.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Provider-agnostic half of an {@link LlmClient} implementation. Owns the four
 * pipeline prompts, the length filter / recovery logic, and the JSON envelope
 * parsing. Concrete subclasses only implement {@link #callJson} — turning a
 * prompt + {@link SchemaSpec} into a raw JSON string (plus token accounting)
 * for their specific provider transport (Gemini SDK, OpenAI-compatible REST,
 * etc.).
 */
public abstract class BaseLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(BaseLlmClient.class);

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final GenerationConfigService configService;

    protected BaseLlmClient(GenerationConfigService configService) {
        this.configService = configService;
    }

    /**
     * Send a prompt to the provider and return the raw JSON text of the reply.
     * Subclasses decide how the provider schema is enforced (Gemini responseSchema,
     * json_object + inline shape, etc.) and how token usage is recorded. {@code label}
     * names the pipeline step (e.g. "Ranking") for streamed progress events.
     */
    protected abstract String callJson(String model, String prompt, SchemaSpec schema,
                                       double temperature, ProgressLog progress,
                                       TokenAccumulator tokens, boolean stream, String label);

    // Transient-failure safety net around callJson: one retry after a short pause. Deliberately
    // NOT stacked with the recovery pass in generateBullets below (that's a separate,
    // content-quality layer) and does not retry timeouts, which are already expensive.
    private String callJsonWithRetry(String model, String prompt, SchemaSpec schema, double temperature,
                                     ProgressLog progress, TokenAccumulator tokens, boolean stream, String label) {
        try {
            return callJson(model, prompt, schema, temperature, progress, tokens, stream, label);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw e;
            }
            log.warn("{}: LLM call failed ({}), retrying once...", label, e.getMessage());
            progress.emit(label + ": call failed, retrying...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return callJson(model, prompt, schema, temperature, progress, tokens, stream, label);
        }
    }

    /**
     * Temperature for the extraction-shaped calls — JD cleanup, ranking, cover letter.
     * These want determinism, so a lower value looks like an obvious win. It is not:
     * the admin LLM settings let an operator point the base URL at any OpenAI-compatible
     * endpoint, and OpenAI's reasoning models reject {@code temperature != 1} with a 400.
     * 1.0 is the one value that works on every provider we can be pointed at.
     *
     * <p>Lowering it would also blunt {@link #callJsonWithRetry}: the OpenAI-compatible
     * path has no schema enforcement (only {@code response_format: json_object} plus a
     * prose shape hint), so its realistic failure is malformed JSON — and a near-zero
     * temperature retry resamples the same failure instead of a fresh draft.
     *
     * <p>ponytail: a constant, not a config column. If determinism here is ever worth
     * paying for, add a separate match temperature with a provider capability check —
     * do NOT reuse {@code GenerationConfig.getTemperature()}, which is the user's
     * bullet-writing style knob and means something different.
     */
    private static final double EXTRACTION_TEMPERATURE = 1.0;

    // -------- provider-agnostic schema model --------

    public enum SpecType { OBJECT, ARRAY, STRING, INTEGER }

    public record SchemaSpec(SpecType type, Map<String, SchemaSpec> properties,
                             SchemaSpec items, List<String> required) {
        public static SchemaSpec string() {
            return new SchemaSpec(SpecType.STRING, null, null, null);
        }
        public static SchemaSpec integer() {
            return new SchemaSpec(SpecType.INTEGER, null, null, null);
        }
        public static SchemaSpec array(SchemaSpec items) {
            return new SchemaSpec(SpecType.ARRAY, null, items, null);
        }
        public static SchemaSpec object(Map<String, SchemaSpec> properties, List<String> required) {
            return new SchemaSpec(SpecType.OBJECT, properties, null, required);
        }
    }

    // -------- generateBullets --------

    @Override
    public BulletGenerationResult generateBullets(GenerateBulletsRequest req, ProgressLog progress, TokenAccumulator tokens) {
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
                : buildProjectContextBlock(req);

        String repoBlock = req.repoContext() == null || req.repoContext().isBlank()
                ? ""
                : "\nRepo context (README + file listing):\n" + req.repoContext();

        // Without this the model happily rewrites bullets the bank already holds; the dedup
        // pass then deletes them, so we pay full output tokens for discarded work.
        String existingBlock = req.existingBullets() == null || req.existingBullets().isEmpty()
                ? ""
                : """

                ─────────────────────────────────────────────────────────────
                ## ALREADY COVERED — do not repeat these

                The bank already holds the bullets below. Write about DIFFERENT work, or the same
                work from a genuinely different angle. Do not restate, lightly reword, or merely
                re-bold any of them — near-duplicates are discarded.

                %s
                """.formatted(req.existingBullets().stream()
                        .map(t -> "  - " + t)
                        .reduce("", (a, b) -> a + b + "\n"));

        String countTarget = experience ? "8 to 12" : "4 to 6";
        String sourceWord  = experience ? "ROLE" : "PROJECT";

        GenerationConfig cfg = configService.get(req.userId());

        // Appended to the END of the prompt, not the front. generateBank fires one call per
        // category in parallel, and the lens is the ONLY part that differs between them —
        // everything else (rules, examples, source context, repo context, existing bank) is
        // byte-identical. Leading with the lens gave those calls a zero-length common prefix
        // and defeated provider prompt caching on the largest part of the prompt; trailing it
        // makes ~90% of the prompt a shared cacheable prefix. Recency also helps the model
        // weight the lens, which is why the header no longer has to shout "read this FIRST".
        String lens = CategoryLenses.lensFor(req.category());
        String lensBlock = lens == null ? "" : "\n─────────────────────────────────────────────────────────────\n"
                + "## CATEGORY LENS — the angle for THIS batch\n\nApply this lens to the source material above.\n\n" + lens + "\n";

        // Same tail-of-prompt reason as lensBlock above: this varies per category, so it must
        // stay behind the shared cacheable prefix. Unlike existingBlock these bullets do not
        // exist yet — the siblings are mid-flight — so all we can do is fence off their angles.
        String siblingBlock = req.siblingCategories() == null || req.siblingCategories().isEmpty()
                ? ""
                : "\nOther lenses are generating from this same source material right now: "
                + String.join(", ", req.siblingCategories()) + ".\n"
                + "Stay strictly inside YOUR lens — those angles are being covered by those calls, and\n"
                + "overlapping bullets are discarded. If a piece of work fits another lens better, leave\n"
                + "it to that lens.\n";

        String toneInstruction = switch (cfg.getTone()) {
            case CONSERVATIVE -> "Write in a precise, understated tone. Avoid hyperbole. Let the metrics speak.";
            case AGGRESSIVE   -> "Write with a confident, high-impact tone. Emphasise scale, speed, and results aggressively.";
            default           -> "";
        };
        String boldInstruction = switch (cfg.getBoldDensity()) {
            case NONE  -> "Do NOT use any **bold** markup in bullets.";
            case HEAVY -> "Use up to 4 **bold** spans per bullet instead of 2 — prefer the quantified claims.";
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

        String prompt = tuningBlock + """
                You are writing resume bullet points for a %s.
                Produce %s bullets in JSON. EVERY rule below is mandatory.

                ─────────────────────────────────────────────────────────────
                ## 1. LENGTH — line-filling discipline (CRITICAL)

                Each bullet must compile to EITHER exactly 1 full line OR exactly 2 full lines on the
                rendered resume. NEVER produce a bullet that overflows by a few words into a sparse
                second line — that looks broken.

                Length is measured in CHARACTERS including spaces, ignoring the ** bold markers
                (they compile to \\textbf{} and take no width). Word counts are approximate guides;
                the character range is what actually decides whether a line fills.

                Targets:
                  • 1-line bullet: %d to %d characters (roughly %d to %d words).
                  • 2-line bullet: %d to %d characters (roughly %d to %d words).
                  • NEVER produce a bullet of %d-%d characters — that range half-fills line 2.

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

                Use AT MOST 2 bolds per bullet — bold is emphasis, and a bullet that bolds
                everything emphasizes nothing. Reserve it for the one or two things a recruiter's
                eye should land on first: usually the biggest quantified claim. Pick from these
                categories, in priority order:

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
                  • Tag each bullet with 2 to 5 specific technologies/tools/frameworks/protocols
                    explicitly named in the bullet text itself (e.g. "react", "kubernetes",
                    "postgresql", "grpc"). Lowercase, no fixed list — pull from what you wrote.
                    Do not invent tags for things not mentioned in the bullet.

                ─────────────────────────────────────────────────────────────
                ## EXAMPLES (study these — match this length, bold density, and ending punctuation)

                  ✓ Built a RAG pipeline over **64K** MLS listings with hybrid full-text + vector search, RRF-k fusion, and a semantic cache, cutting query latency under **300ms** and LLM calls by 40%%.

                  ✓ Engineered a real-time geospatial pipeline over **64K live listings** using React-Leaflet, Turf.js, and MongoDB 2dsphere queries with viewport-aware fetching and marker diffing, cutting map re-render from **180ms to 70ms**.

                  ✓ Designed a sub-cent-precision credit ledger powering metered billing across AI, voice, and SMS usage, processing **120K transactions/month** through an append-only audit trail and idempotent **Stripe** webhook integration.

                  ✓ Encrypted all third-party OAuth and telephony tokens at rest with **AES-256-GCM**, eliminating plaintext credentials from the database across the multi-tenant platform.

                  ✗ Worked on backend stuff using various tools and got things faster.
                    (passive opener, no bolds, no metrics, no period-style impact)

                ─────────────────────────────────────────────────────────────
                ## %s CONTEXT

                %s%s%s
                """.formatted(sourceWord, countTarget,
                        BulletTextRules.singleLowChars(cfg), BulletTextRules.singleHighChars(cfg),
                        cfg.getSingleLineLow(), cfg.getSingleLineHigh(),
                        BulletTextRules.doubleLowChars(cfg), BulletTextRules.doubleHighChars(cfg),
                        cfg.getDoubleLineLow(), cfg.getDoubleLineHigh(),
                        BulletTextRules.deadZoneLowChars(cfg), BulletTextRules.deadZoneHighChars(cfg),
                        sourceWord, contextBlock, repoBlock, existingBlock)
                + lensBlock + siblingBlock;

        SchemaSpec schema = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "bullets", SchemaSpec.array(SchemaSpec.object(new LinkedHashMap<>(Map.of(
                        "text", SchemaSpec.string(),
                        "tags", SchemaSpec.array(SchemaSpec.string())
                )), List.of("text", "tags")))
        )), List.of("bullets"));

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
        String sourceContext = contextBlock + repoBlock;
        FilterResult first = callAndFilter(prompt, schema, target, cfg, progress, tokens, sourceContext);
        List<GeneratedBullet> kept = new ArrayList<>(first.kept());

        // One recovery pass when the filter left us short: repair what it rejected — wrong
        // length, or a weak opening verb — and top up whatever shortfall remains. The first
        // pass's survivors are carried through rather than thrown away.
        //
        // The word-filter check is not the only trigger: opener rejects happen with the length
        // filter switched off, and those are repairable too.
        //
        // Capped at one attempt on purpose — this sits above callJsonWithRetry, so stacking
        // recovery rounds would multiply worst-case latency and spend.
        Cuts cuts = first.cuts();
        int returned = first.returned();
        int repaired = 0;
        boolean recovered = false;

        if (kept.size() < target && (cfg.isWordFilterEnabled() || !first.repairable().isEmpty())) {
            recovered = true;
            int deficit = target - kept.size();
            int newNeeded = Math.max(0, deficit - first.repairable().size());
            log.info("Filter kept {}/{} bullets, running recovery ({} to repair, {} new).",
                    kept.size(), target, first.repairable().size(), newNeeded);
            progress.emit("Recovery: " + kept.size() + "/" + target + " passed - repairing "
                    + first.repairable().size() + " rejected, requesting " + newNeeded + " new...");

            String recoveryPrompt = prompt + recoveryNote(first.repairable(), kept, newNeeded, cfg);
            FilterResult second = callAndFilter(recoveryPrompt, schema, deficit, cfg, progress, tokens, sourceContext);

            List<String> keptTexts = new ArrayList<>(kept.stream().map(GeneratedBullet::text).toList());
            int added = 0;
            for (GeneratedBullet g : second.kept()) {
                if (BulletTextRules.isNearDuplicate(g.text(), keptTexts)) continue;
                keptTexts.add(g.text());
                kept.add(g);
                added++;
            }
            progress.emit("Recovery result: +" + added + " bullet(s), now " + kept.size() + "/" + target);
            cuts = cuts.plus(second.cuts());
            returned += second.returned();
            repaired = added;
        }

        // One structured line per category generation. The per-bullet decisions are already in
        // the progress stream, but that is ephemeral and per-user — this is the only place the
        // accept rate and its reasons survive the request, so prompt changes can be judged on
        // numbers instead of on reading a few outputs.
        //
        // ponytail: a log line, not a table. Grep-able beats a schema until someone actually
        // wants a dashboard; cost_usd here is TokenAccumulator's, which prices every provider
        // at Gemini Flash rates (see TokenAccumulator) — fine for relative comparisons between
        // runs, wrong as an absolute for OpenAI/OpenRouter.
        log.info("BULLET_GEN category={} kind={} target={} returned={} kept={} repaired={} recovery={}"
                        + " cut_opener={} cut_fabricated={} cut_deadzone={} cut_toolong={} cut_tooshort={}"
                        + " in_tok={} out_tok={} cost_usd={}",
                req.category(), req.kind(), target, returned, kept.size(), repaired, recovered,
                cuts.opener(), cuts.fabricated(), cuts.deadZone(), cuts.tooLong(), cuts.tooShort(),
                tokens.getPromptTokens(), tokens.getCandidatesTokens(), tokens.getCostUsd());

        progress.emit("Saved " + kept.size() + " bullets for category: " + req.category());
        return new BulletGenerationResult(kept);
    }

    /**
     * Appended to the generation prompt for the recovery pass, so the model keeps every rule
     * and the source context from the first call and only gains the repair instructions.
     */
    private static String recoveryNote(List<Reject> repairable, List<GeneratedBullet> kept,
                                       int newNeeded, GenerationConfig cfg) {
        StringBuilder sb = new StringBuilder(
                "\n─────────────────────────────────────────────────────────────\n## RECOVERY PASS\n\n");
        sb.append(("Bullets must be EITHER %d-%d characters (fills 1 line) OR %d-%d characters (fills\n"
                 + "2 lines). The %d-%d character range is forbidden — it half-fills line 2. NEVER exceed\n"
                 + "%d characters — that spills onto a third line.\n\n")
                .formatted(BulletTextRules.singleLowChars(cfg), BulletTextRules.singleHighChars(cfg),
                        BulletTextRules.doubleLowChars(cfg), BulletTextRules.doubleHighChars(cfg),
                        BulletTextRules.deadZoneLowChars(cfg), BulletTextRules.deadZoneHighChars(cfg),
                        BulletTextRules.doubleHighChars(cfg)));

        // Grouped by reason: a bullet cut for length and a bullet cut for its opening verb need
        // opposite instructions, and a single blended one ("rewrite this") gets both done badly.
        List<String> tooLong = repairable.stream()
                .filter(r -> r.reason() == RejectReason.LENGTH).map(Reject::text).toList();
        List<String> weakOpener = repairable.stream()
                .filter(r -> r.reason() == RejectReason.OPENER).map(Reject::text).toList();

        if (!tooLong.isEmpty()) {
            sb.append("Rewrite each bullet below so it lands in a valid band. Keep the same facts, metrics\n")
              .append("and technologies — change only the phrasing and the level of detail. Never invent a\n")
              .append("number to reach a length.\n\n");
            for (String t : tooLong) sb.append("  - ").append(t).append("\n");
            sb.append("\n");
        }
        if (!weakOpener.isEmpty()) {
            sb.append("Each bullet below opens with a weak or passive phrase. Replace ONLY the opening so it\n")
              .append("starts with a strong action verb (Built, Designed, Shipped, Engineered, Owned, Led,\n")
              .append("Authored, Implemented, Architected, Migrated, Hardened, Integrated). Keep the facts,\n")
              .append("metrics, technologies and length as they are — this is a verb fix, not a rewrite.\n\n");
            for (String t : weakOpener) sb.append("  - ").append(t).append("\n");
            sb.append("\n");
        }
        if (newNeeded > 0) {
            sb.append("Also write ").append(newNeeded)
              .append(" additional NEW bullet(s) covering work not described above.\n\n");
        }
        if (!kept.isEmpty()) {
            sb.append("Already accepted — do not repeat or rewrite these:\n");
            for (GeneratedBullet g : kept) sb.append("  - ").append(g.text()).append("\n");
            sb.append("\n");
        }
        sb.append("Return the rewritten bullets and any new bullets together in the bullets array.\n");
        return sb.toString();
    }

    /**
     * What one filtered LLM call produced: the bullets that passed, plus the ones rejected
     * only on length — the dead zone and the two-line ceiling. Those are good content at the
     * wrong length, so the recovery pass sends them back to be rewritten rather than
     * discarding them. Rejects on content (fabricated metric) or style (weak opener) are not
     * repairable here and are dropped outright.
     */
    private record FilterResult(List<GeneratedBullet> kept, List<Reject> repairable,
                                Cuts cuts, int returned) {}

    /**
     * Per-reason reject tally for one filtered call. Exists so the generation telemetry line
     * can say WHY bullets were lost — an accept rate alone cannot distinguish a prompt that
     * writes weak openers from one that fabricates metrics, and those need opposite fixes.
     */
    private record Cuts(int opener, int fabricated, int deadZone, int tooLong, int tooShort) {
        static final Cuts ZERO = new Cuts(0, 0, 0, 0, 0);
        Cuts plus(Cuts o) {
            return new Cuts(opener + o.opener, fabricated + o.fabricated, deadZone + o.deadZone,
                    tooLong + o.tooLong, tooShort + o.tooShort);
        }
    }

    /** Why a repairable bullet was rejected — decides which instruction the recovery pass gets. */
    private enum RejectReason { LENGTH, OPENER }

    /** A rejected bullet the recovery pass can plausibly fix, with the reason it was cut. */
    private record Reject(String text, RejectReason reason) {}

    // progress param lets us emit per-bullet filter decisions without exposing bullet text.
    private FilterResult callAndFilter(String prompt, SchemaSpec schema,
                                       int target, GenerationConfig cfg, ProgressLog progress, TokenAccumulator tokens,
                                       String sourceContext) {
        String json = callJsonWithRetry(generateModel(), prompt, schema, cfg.getTemperature(), progress, tokens, false, "Bullets");
        BulletsEnvelope env;
        try {
            env = mapper.readValue(json, BulletsEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM bullet response: " + json, e);
        }
        if (env.bullets == null) {
            log.warn("LLM bullet response had no 'bullets' array: {}", abbreviate(json));
            progress.emit("LLM returned no bullets array.");
            return new FilterResult(List.of(), List.of(), Cuts.ZERO, 0);
        }
        int total = env.bullets.size();
        if (cfg.isWordFilterEnabled()) {
            progress.emit("LLM returned " + total + " bullets, filtering by word count (target: " + target + ")...");
        } else {
            progress.emit("LLM returned " + total + " bullets (word filter disabled, keeping all)...");
        }

        List<GeneratedBullet> kept = new ArrayList<>();
        List<Reject> repairable = new ArrayList<>();
        int dropped = 0;
        int cutOpener = 0, cutFabricated = 0, cutDeadZone = 0, cutTooLong = 0, cutTooShort = 0;
        for (BulletJson b : env.bullets) {
            String text = BulletTextRules.ensureTerminalPeriod(b.text);

            if (BulletTextRules.hasForbiddenOpener(text)) {
                log.info("Dropped bullet (forbidden opener): {}", abbreviate(text));
                progress.emit("Cut: weak/passive opener - queued for rewrite");
                // The content is fine; only the first two words are wrong. Repairing that is a
                // verb swap, so this goes to the recovery pass rather than being thrown away.
                repairable.add(new Reject(text, RejectReason.OPENER));
                cutOpener++;
                dropped++;
                continue;
            }
            List<String> fabricated = BulletTextRules.fabricatedNumbers(text, sourceContext);
            if (!fabricated.isEmpty()) {
                log.info("Dropped bullet (fabricated metric {}): {}", fabricated, abbreviate(text));
                progress.emit("Cut: fabricated metric not in source (" + String.join(", ", fabricated) + ")");
                cutFabricated++;
                dropped++;
                continue;
            }

            int cc = BulletTextRules.charCount(text);
            BulletTextRules.Decision decision = BulletTextRules.decide(cc, cfg);
            switch (decision) {
                case DEAD_ZONE -> {
                    log.info("Dropped bullet (char count {} in dead zone {}-{}): {}", cc,
                            BulletTextRules.deadZoneLowChars(cfg), BulletTextRules.deadZoneHighChars(cfg), abbreviate(text));
                    progress.emit("Cut: " + cc + "c - dead zone ("
                            + BulletTextRules.deadZoneLowChars(cfg) + "-" + BulletTextRules.deadZoneHighChars(cfg)
                            + "), needs " + BulletTextRules.singleLowChars(cfg) + "-" + BulletTextRules.singleHighChars(cfg)
                            + " or " + BulletTextRules.doubleLowChars(cfg) + "-" + BulletTextRules.doubleHighChars(cfg));
                    repairable.add(new Reject(text, RejectReason.LENGTH));
                    cutDeadZone++;
                    dropped++;
                }
                case TOO_LONG -> {
                    log.info("Dropped bullet (char count {} over two-line ceiling {}): {}", cc,
                            BulletTextRules.doubleHighChars(cfg), abbreviate(text));
                    progress.emit("Cut: " + cc + "c - over two-line ceiling (max "
                            + BulletTextRules.doubleHighChars(cfg) + ")");
                    // Same shape as a dead-zone reject — right content, wrong length — so it goes
                    // to the recovery pass, whose instruction is already "rewrite into a valid band".
                    repairable.add(new Reject(text, RejectReason.LENGTH));
                    cutTooLong++;
                    dropped++;
                }
                case TOO_SHORT -> {
                    log.info("Dropped bullet (char count {} too short, floor {}): {}", cc,
                            BulletTextRules.minFloorChars(cfg), abbreviate(text));
                    progress.emit("Cut: " + cc + "c - too short (min " + BulletTextRules.minFloorChars(cfg) + ")");
                    cutTooShort++;
                    dropped++;
                }
                case KEPT -> {
                    // The prompt says to tag only what the bullet actually names, but the model
                    // invents tags anyway — and tags feed JD keyword scoring, so junk here
                    // quietly degrades matching. Drop the bad tag, not the bullet.
                    List<String> rawTags = b.tags == null ? List.of() : b.tags;
                    List<String> tags = rawTags.stream()
                            .filter(t -> KeywordScorer.mentions(text, t))
                            .toList();
                    int droppedTags = rawTags.size() - tags.size();
                    String tagNote = droppedTags == 0 ? "" : " (" + droppedTags + " unmentioned tag(s) dropped)";
                    progress.emit("Kept: " + cc + "c [" + String.join(", ", tags) + "]" + tagNote);
                    kept.add(new GeneratedBullet(text, tags));
                }
            }
        }
        log.info("Generation kept {} bullets, dropped {}.", kept.size(), dropped);
        return new FilterResult(kept, repairable,
                new Cuts(cutOpener, cutFabricated, cutDeadZone, cutTooLong, cutTooShort), total);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    }

    // -------- cleanJd --------

    /**
     * Ceiling on JD text sent to the model. Matches {@code GithubContextFetcher.MAX_CHARS}
     * (30_000) so the two large free-text inputs stay comparable.
     *
     * <p>Why it exists: {@link #cleanJd} is the first LLM call in the application pipeline
     * and {@link #callJson} has a hard 120s timeout, so a pasted or fetched careers page
     * can burn the whole timeout before anything useful happens. repoContext was already
     * capped; the JD was the remaining unbounded input. Truncating (never rejecting) keeps
     * the whole-careers-page paste working instead of turning it into a 400.
     */
    static final int MAX_JD_CHARS = 30_000;

    /** Truncates at the last whitespace before {@link #MAX_JD_CHARS}. Null/blank passes through. */
    static String capJd(String rawJd) {
        if (rawJd == null || rawJd.length() <= MAX_JD_CHARS) return rawJd;
        String head = rawJd.substring(0, MAX_JD_CHARS);
        int cut = head.lastIndexOf(' ');
        return cut > 0 ? head.substring(0, cut) : head;
    }

    @Override
    public JdCleanResult cleanJd(String rawJd, ProgressLog progress, TokenAccumulator tokens) {
        progress.emit("Calling LLM to clean JD and extract keywords...");
        String cappedJd = capJd(rawJd);
        if (rawJd != null && cappedJd.length() < rawJd.length()) {
            log.info("JD truncated from {} to {} chars (cap {}).", rawJd.length(), cappedJd.length(), MAX_JD_CHARS);
            progress.emit("JD was " + rawJd.length() + " chars, truncated to " + cappedJd.length()
                    + " (cap " + MAX_JD_CHARS + ") to keep the first LLM call inside its timeout.");
        }
        rawJd = cappedJd;
        String prompt = """
                Clean this job description and extract structured fields.
                  - cleanJd: the JD text with navigation, marketing fluff, and "about us" boilerplate stripped. Keep responsibilities, requirements, and tech stack.
                  - company: the hiring company name.
                  - role: the job title.
                  - keywords: 8-20 specific technical keywords ATS systems would look for (technologies, frameworks, methodologies). No soft skills.

                Raw JD:
                %s
                """.formatted(rawJd);

        SchemaSpec schema = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "cleanJd",  SchemaSpec.string(),
                "company",  SchemaSpec.string(),
                "role",     SchemaSpec.string(),
                "keywords", SchemaSpec.array(SchemaSpec.string())
        )), List.of("cleanJd", "company", "role", "keywords"));

        String json = callJsonWithRetry(cleanJdModel(), prompt, schema, EXTRACTION_TEMPERATURE, progress, tokens, false, "JD clean");
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
    public RankResult rankBullets(RankRequest req, ProgressLog progress, TokenAccumulator tokens) {
        progress.emit("Calling LLM to rank " + req.bullets().size() + " bullets against JD...");
        StringBuilder bulletsBlock = new StringBuilder();
        for (BulletForMatch b : req.bullets()) {
            bulletsBlock.append("  - id=").append(b.bulletId())
                    .append(" project=").append(b.projectName())
                    .append(" tags=").append(b.tags())
                    .append("\n    text: ").append(b.text()).append("\n");
        }

        String coursesBlock = req.courses() == null || req.courses().isEmpty()
                ? ""
                : "\nCoursework (select up to 6 most relevant for this role):\n"
                  + req.courses().stream().map(c -> "  - " + c).reduce("", (a, b) -> a + b + "\n");

        StringBuilder skillsBlock = new StringBuilder();
        if (req.skillCategories() != null && !req.skillCategories().isEmpty()) {
            skillsBlock.append("\nSkills (filter each category to only the most JD-relevant items; keep ordering; return empty array if none relevant):\n");
            for (LlmClient.SkillCategory sc : req.skillCategories()) {
                skillsBlock.append("  ").append(sc.name()).append(": ")
                        .append(String.join(", ", sc.items())).append("\n");
            }
        }

        String prompt = """
                You are an expert resume writer. Rank EVERY bullet below against the job description.

                Rank ALL %d bullets from rank 1 (best fit) to %d (worst). Use integers, no ties.
                For each bullet give a one-sentence "why" tying it to specific JD requirements.

                Produce atsMatched (keywords from the JD that appear in the top 8 bullets)
                and atsMissing (JD keywords NOT covered).

                If coursework is provided, select the best matching courses (up to 6) for this role
                and return them in selectedCourses. Return an empty array if no coursework is provided.

                If skills are provided, return selectedSkills with each category filtered to only the
                JD-relevant items. Preserve the original item text exactly. Return empty arrays for
                categories with no relevant items.

                Role emphasis: %s
                Company: %s

                Cleaned JD:
                %s

                Keywords from JD:
                %s

                Bullets:
                %s%s
                """.formatted(
                        req.bullets().size(), req.bullets().size(),
                        req.roleEmphasis(),
                        req.company(),
                        req.cleanJd(),
                        req.keywords(),
                        bulletsBlock,
                        coursesBlock + skillsBlock);

        SchemaSpec rankedItem = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "bulletId", SchemaSpec.string(),
                "rank",     SchemaSpec.integer(),
                "why",      SchemaSpec.string()
        )), List.of("bulletId", "rank", "why"));

        SchemaSpec stringArray = SchemaSpec.array(SchemaSpec.string());

        SchemaSpec selectedSkillsSchema = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "languages",   stringArray,
                "frameworks",  stringArray,
                "databases",   stringArray,
                "devops",      stringArray
        )), List.of("languages", "frameworks", "databases", "devops"));

        SchemaSpec schema = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "rankedBullets",   SchemaSpec.array(rankedItem),
                "atsMatched",      stringArray,
                "atsMissing",      stringArray,
                "selectedCourses", stringArray,
                "selectedSkills",  selectedSkillsSchema
        )), List.of("rankedBullets", "atsMatched", "atsMissing", "selectedCourses", "selectedSkills"));

        String json = callJsonWithRetry(matchModel(), prompt, schema, EXTRACTION_TEMPERATURE, progress, tokens, true, "Ranking");
        RankEnvelope env;
        try {
            env = mapper.readValue(json, RankEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM rank response: " + json, e);
        }
        // Without a ranking, selection picks nothing and we would render an empty resume
        // and store it as a successful application. Fail the pipeline instead.
        if (env.rankedBullets == null || env.rankedBullets.isEmpty()) {
            throw new RuntimeException("LLM rank response contained no rankedBullets: " + json);
        }
        try {
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
            List<String> selectedCourses = env.selectedCourses == null ? List.of() : env.selectedCourses;
            Map<String, List<String>> selectedSkills = env.selectedSkills == null ? Map.of() : env.selectedSkills;
            progress.emit("ATS matched (" + atsMatched.size() + "): " + String.join(", ", atsMatched));
            if (!atsMissing.isEmpty()) {
                progress.emit("ATS missing (" + atsMissing.size() + "): " + String.join(", ", atsMissing));
            }
            if (!selectedCourses.isEmpty()) {
                progress.emit("Selected courses (" + selectedCourses.size() + "): " + String.join(", ", selectedCourses));
            }
            selectedSkills.forEach((cat, items) -> {
                if (!items.isEmpty()) progress.emit("Skills/" + cat + ": " + String.join(", ", items));
            });
            return new RankResult(ranked, atsMatched, atsMissing, selectedCourses, selectedSkills);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM rank response: " + json, e);
        }
    }

    // -------- coverLetter --------

    @Override
    public String coverLetter(CoverLetterRequest req, ProgressLog progress, TokenAccumulator tokens) {
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

        SchemaSpec schema = SchemaSpec.object(new LinkedHashMap<>(Map.of(
                "coverLetter", SchemaSpec.string()
        )), List.of("coverLetter"));

        String json = callJsonWithRetry(matchModel(), prompt, schema, EXTRACTION_TEMPERATURE, progress, tokens, true, "Cover letter");
        try {
            CoverLetterEnvelope env = mapper.readValue(json, CoverLetterEnvelope.class);
            progress.emit("Cover letter generated.");
            return env.coverLetter;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM cover letter response: " + json, e);
        }
    }

    // -------- model selectors (subclass config) --------

    protected abstract String generateModel();
    protected abstract String matchModel();
    protected abstract String cleanJdModel();

    // -------- shared helpers --------

    private static String nz(String s) { return s == null ? "" : s; }

    private static String buildProjectContextBlock(GenerateBulletsRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project name: ").append(nz(req.projectName())).append("\n\n");
        sb.append("Project description:\n").append(nz(req.description())).append("\n");
        if (has(req.techStack()))      sb.append("\nTech stack: ").append(req.techStack()).append("\n");
        if (has(req.yourRole()))       sb.append("Your role: ").append(req.yourRole()).append("\n");
        if (has(req.ownership()))      sb.append("\nWhat you owned:\n").append(req.ownership()).append("\n");
        if (has(req.scaleImpact()))    sb.append("\nScale & impact: ").append(req.scaleImpact()).append("\n");
        if (has(req.hardestProblem())) sb.append("\nHardest problem solved:\n").append(req.hardestProblem()).append("\n");
        return sb.toString();
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    // -------- JSON envelopes (shared across providers) --------

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class BulletsEnvelope { public List<BulletJson> bullets; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class BulletJson { public String text; public List<String> tags; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class JdCleanEnvelope {
        public String cleanJd; public String company; public String role; public List<String> keywords;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class RankEnvelope {
        public List<RankedItemJson> rankedBullets;
        public List<String> atsMatched;
        public List<String> atsMissing;
        public List<String> selectedCourses;
        public Map<String, List<String>> selectedSkills;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class RankedItemJson { public String bulletId; public int rank; public String why; }
    @JsonIgnoreProperties(ignoreUnknown = true)
    protected static class CoverLetterEnvelope { public String coverLetter; }
}
