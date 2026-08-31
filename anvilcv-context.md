# Project Name
→ AnvilCV field: **name**

AnvilCV (repo `resuforge`, Maven artifact `resume-pipeline`)

# Tech Stack
→ AnvilCV field: **techStack**

Java 21, Spring Boot 3.4.0 [repo] (web, data-jpa, security, validation). PostgreSQL on Neon with Flyway — 22 migrations, V1 through V22 [repo] — and Hibernate `ddl-auto: validate`, so the schema's source of truth is the migration set, not the entities. Virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) in six places [repo] for blocking LLM fan-out. HikariCP pinned to `maximum-pool-size: 5` with `max-lifetime: 240000` and a 30s connection timeout, tuned for Neon's scale-to-zero cold start [repo]. AES-256-GCM (`AES/GCM/NoPadding`, 12-byte random IV per value, never reused, base64 `iv || ciphertext+tag`) for provider API keys at rest [repo]. bucket4j token bucket, capacity 5 per IP, on registration [repo]. jsoup scraping schema.org `JobPosting` JSON-LD. LaTeX compiled by the `tectonic` binary. Apache PDFBox `PDFTextStripper` in tests. Frontend: React 18.3, TypeScript 5.6, Vite 5.4, react-router 6.28, framer-motion, d3-sankey [repo]. LLM: google-genai 1.18 plus OpenAI-compatible clients (OpenAI, OpenCode Zen) behind one `LlmClient` interface with a `RoutingLlmClient` in front [repo]. Multi-arch Docker: `maven:3.9-eclipse-temurin-21` build stage to `eclipse-temurin:21-jre-jammy` runtime, `TARGETARCH`-aware [repo]. Cloudflare Tunnel — compose publishes no host ports at all [repo].

# Architecture Overview
→ AnvilCV field: **description**

**The prompt is the product.** `content_extract.md` is a 622-line LLM prompt that ships as a classpath resource, is served for download by `ToolsController`, and whose output is parsed back in by `frontend/src/lib/parseExtract.ts` into the app's own project-intake form — the tool that fills AnvilCV's fields is itself a shipped, versioned, unit-tested artifact of AnvilCV [repo].

Paste a job description, get a one-page tailored resume PDF. The pipeline is: clean the JD with an LLM, cheap keyword prefilter over the bullet bank, expensive LLM ranking, selection under hard caps, LaTeX, tectonic, PDF — with the cover letter generated in parallel [repo]. 47 REST endpoints across the controllers (21 GET, 16 POST, 5 PUT, 3 DELETE, 2 PATCH) over 9 JPA entities [repo]. Bullet generation fans out across eight hand-authored "category lenses" on virtual threads, each lens a paragraph of prompt text naming the techniques and units that lens should quantify with [repo]. Selection is a constrained pack, not a top-N: `MAX_TOTAL = MAX_ENTRIES * 3`, `MAX_PER_PROJECT = 3`, `MAX_TOTAL_LINES = 29`, with floors of 2 experience and 3 project entries, and bullets sorted by marginal gain rather than absolute score because a resume is read as a set [repo]. Async throughout: submit returns a job UUID, the client polls; job progress is deliberately in-memory, which the README itself names as the limit on multi-instance deploys [repo].

# Your Role
→ AnvilCV field: **yourRole**

*Verification mode: indexing mode. The 2a roster returned one human name across all 104 commits, so per the 2-gate there was nothing to disambiguate and 2b/2c were skipped; 2d was run in full.*

Sole author. First-person: I built AnvilCV end-to-end — Spring Boot backend, React frontend, schema and all 22 migrations, LaTeX template, Docker and Cloudflare Tunnel deployment, CI, and the authored content (prompts, lenses, term list).

*Identity folding, done without the developer available.* The no-merge roster showed `UfozDelta <chizblitz@gmail.com>` (58) and `UfozDelta <>` (43) — folded on an identical name string, the prompt's stated hard evidence. Phase 2d then surfaced a third identity that the 2a roster had missed because it appears only on merge commits: `Ufoz <60415565+UfozDelta@users.noreply.github.com>`, 3 commits, all "Merge pull request" [repo] — folded on the same username across a work email and a `users.noreply.github.com` address. All three are one person on that evidence; if that is wrong, the ownership claims here are wrong.

*Stated limitations.* `gh` is not installed in this environment, so PR and review workflow is not verifiable beyond the three merge commits above (PRs #1, #2, #5) — no review culture is claimed. No developer was available for Phase 0 or Phase 4, so this document contains zero `[dev]` figures and every outcome beat that only a human could supply is absent rather than estimated.

# What You Owned End-to-End
→ AnvilCV field: **ownership**

- **Bullet selection engine** (`BulletSelector`) — built greenfield. Constrained pack under simultaneous caps: 15 bullets, 3 per project, 29 rendered lines, floors of at least 2 experience and 3 project entries. Ranks by *marginal* gain rather than absolute score [repo].
- **Rendered-length model** (`BulletTextRules`, `GenerationConfig`) — optimized existing. Words are converted to characters at `CHARS_PER_WORD = 5.4` and checked against `CHARS_PER_LINE = 105`, both measured off a compiled `resume.tex` PDF rather than guessed [repo].
- **Keyword prefilter** (`KeywordScorer`) — rebuilt. n-gram normalisation up to `MAX_NGRAM = 3` with an alias table, so "Kubernetes"/"K8s", "PostgreSQL"/"Postgres", "Node.js"/"NodeJS" match, and "CI/CD" matches at all — the javadoc records that punctuation "previously matched nothing" [repo].
- **Eight category lenses plus a 428-line `tech-terms.txt`** — authored, not coded. Hand-built domain modelling: per-lens prompt paragraphs naming techniques and units, and a curated canonical-casing allowlist matched longest-term-first, extendable with no code change [repo].
- **`content_extract.md` as a shipped feature** — built greenfield. Classpath resource, `ToolsController` download endpoint, `parseExtract.ts` parser, intake form, with `parseExtract.test.ts` covering the round trip [repo].
- **Multi-provider LLM layer** — integrated third-party. Three clients behind one interface plus `RoutingLlmClient`, per-call model selection, retry, and executor timeout; token and USD cost logged per application in `llm_usage_log` [repo].
- **Schema** — 22 Flyway migrations including a user-isolation migration (V7) and a backfill (V18); `ddl-auto: validate` keeps entities honest [repo].
- **Deployment** — multi-arch Dockerfile, `mem_limit: 3g`, and a compose file that publishes no host ports; cloudflared reaches the JVM over the compose network so the machine has no open inbound port [repo].
- **CI** — two-job GitHub Actions workflow, `mvn -B verify` and Vitest plus Vite build, on push to main and every PR [repo].

Not claimed: nothing was subtracted. Phase 2d enumerated all 14 top-level paths and blamed each; every one is 100% this author.

# Scale & Impact
→ AnvilCV field: **scaleImpact**

- 47 REST endpoints, 9 JPA entities, 22 Flyway migrations [repo]
- ~7,988 lines of Java across 76 files; ~4,710 lines of TypeScript across 46 files [repo]
- 28 backend test classes plus 4 frontend Vitest suites [repo]
- 8 category lenses; 428-line curated technology term list [repo]
- 3 LLM providers behind 1 interface [repo]
- 104 commits over 3.5 months, 2026-05-15 to 2026-08-30 [repo]
- Config-pinned limits: Hikari pool 5, LLM timeout 30s, register rate limit 5 per IP, JVM container cap 3 GB, selection caps 15 bullets / 3 per project / 29 lines [repo]
- SSE removal: 100 insertions against 414 deletions, net minus 314 lines, `SseUtils.java` (43 lines) deleted outright [diff]
- Refit/repair rework `0c49246`: 1,105 insertions against 1,395 deletions — a net deletion of 290 lines while adding a 182-line test [diff]

No production traffic, user-count, latency or cost figure appears anywhere in the repo. `llm_usage_log` and the `pipeline_duration` column exist and would hold real numbers in a running instance; with no developer available, none are stated here.

# Hardest Problem Solved
→ AnvilCV field: **hardestProblem**

CONSTRAINT: a resume PDF is read by an ATS, not a human, and `resume.tex` lays every heading out as a two-column `tabular*` — employer left, dates right-aligned to the margin. A PDF carries no table structure, only positioned glyphs, so whether the two halves come back out attached is a property of the *extractor*, not the document. A layout-analysis extractor can read the wide inter-column gap as a column boundary, emit the whole left column first, and land every date range beside the *next* employer — a wrong-facts parse: right job, wrong dates, silently. APPROACH: `ResumePdfParseTest` compiles the real template with the real tectonic binary and re-parses its own output with PDFBox `PDFTextStripper`, asserting that date-to-employer binding survives. The extractor choice is reasoned rather than defaulted — PDFBox is what Apache Tika wraps and what the large Java-shop ATS vendors run, so it is the one that decides the question. RESULT (partial, [repo], measured 2026-08-29 and recorded in the test's own javadoc): the template survives PDFBox, pypdf and mostly poppler, and fails only under pdfminer.six's default `LAParams`. The test documents that narrowing the gap does not help — pdfminer splits at every width down to `0.40\textwidth` — so the failure is a property of that library's defaults rather than something the template can be tuned away from. No before-to-after error rate exists in the repo; the missing RESULT beat is how often a real ATS mangled dates before the test existed, and it could not be asked.

# Notable Technical Decisions
→ fold into: **hardestProblem**

- **Deleted SSE rather than fixing it.** Streaming was built in `8b5fdda`, then fought across six consecutive commits — emitter timeout, forcing immediate delivery on all stream endpoints, removing React state batching, moving to a modal, merging dual `useSyncExternalStore` calls, switching to fetch-event-source for incremental rendering — before being replaced with async-submit plus a `JobProgressStore` poll and deleted in `526851b`, net minus 314 lines with `SseUtils` gone [diff]. Building a thing, establishing it lost, and removing it is the rarer signal here.
- **Rendered-length model over word count.** Word bands are converted to characters at 5.4 chars per word and checked against a 105-char line measured off a compiled PDF, so "one-line bullet" means one *rendered* line [repo]. V21 recalibrated the bands from 22-26/42-50 (dead zone 27-40) to 15-18/32-37 (dead zone 19-31) — the old defaults were sized for a narrower textwidth and rendered as 2-3 lines each [repo].
- **The V21 migration only touches untouched rows.** Its `WHERE` clause matches every one of the six old default values simultaneously, so a user who tuned their own bands keeps that tuning [repo]. A blanket `UPDATE` would have silently overwritten user configuration.
- **Marginal gain over absolute score** in selection, because a resume is read as a set, not as a ranked list [repo].
- **Keyword text matching over tag matching.** `KeywordScorer`'s javadoc records the reasoning: bullet tags are coarse categories, JD keywords are specific tech terms, "those vocabularies barely overlap, so tag-only matching was near-random" [repo].
- **No published ports.** cloudflared reaches the JVM over the compose network; the host has no open inbound port at all [repo].

# Security & Compliance Posture
→ fold into: **ownership**

Provider API keys are encrypted at rest with AES-256-GCM — 12-byte random IV per value, never reused, stored as base64 `iv || ciphertext+tag`; a wrong key fails the GCM tag check rather than returning garbage [repo]. The key must decode to exactly 32 bytes or startup rejects it; without `LLM_SECRET_KEY` the app runs on env keys but refuses to save new ones rather than storing plaintext [repo]. Keys are masked on read and live-tested against the provider before save. Admin is enforced server-side with `hasRole("ADMIN")`; the repo states plainly that the nav-link gate is cosmetic. Registration is rate-limited by bucket4j at capacity 5 per IP [repo]. Per-user isolation of profile, projects, bullets and applications was introduced as a dedicated migration (V7) rather than bolted on per route [repo]. Zero inbound host ports; TLS terminates at Cloudflare's edge, which is why `COOKIE_SECURE=true` despite cloudflared speaking plain HTTP to the app [repo]. `SecretCipherTest` and `SecurityRulesTest` cover both areas. `.env.example` flags its own weak spot: `COOKIE_SAME_SITE=Lax` is only safe while frontend and backend share a registrable domain, and on a `*.vercel.app` frontend it must become `None`, at which point `SecurityConfig`'s `.csrf(disable)` leaves the API open to cross-site requests [repo]. No external audit or compliance framework appears in the repo.

# Work Character
→ fold into: **yourRole**

`built greenfield` — first commit to last is one author over 3.5 months, initial migration V1 in-repo, no prior history [repo].
`optimized existing` — perf commits on existing code: parallelized category generation, targeted project queries, scheduler pool, tectonic pre-warm [repo].
`hardened / secured` — encryption at rest, rate limiting, per-user isolation migration, server-side role enforcement [repo].
`migrated / refactored` — SSE-to-polling removal, `sourcePath`-to-GitHub-fetch replacement, blank-line-to-bullet-triggered parser replacement, band recalibration [repo].
`integrated third-party` — three LLM providers, jsoup JD scraping, tectonic, Cloudflare Tunnel, Neon [repo].
Not `led / coordinated` — one contributor.

# Standout Signal
→ fold into: **description**

**The extractor prompt is a shipped, tested product feature, and the app eats its own output** — `content_extract.md` lives on the classpath, is served by `ToolsController`, and is parsed back in by `parseExtract.ts` under test. Most resume tools hide the prompt; this one versions it and ships it as the intake path.

**A test that compiles the real PDF and re-parses it to check an ATS still binds dates to employers** — `ResumePdfParseTest` treats text extraction as the adversary, names PDFBox as the deciding extractor because Tika wraps it, and records which of four extractors pass. The failure it guards against is silent and wrong-facts, not a crash.

# Failure Modes Avoided
→ fold into: **hardestProblem**

- **PDF re-parse test** preventing a template change that silently detaches every date range from its employer and reattaches it to the next one — a resume that is confidently wrong rather than obviously broken [repo].
- **Round-robin per-project quota before the global cut** preventing one bullet-heavy project from crowding every other project off the resume [repo].
- **Entry-kind floors (2 experience, 3 project)** preventing a resume with no work history on it at all [repo].
- **Guarded V21 `WHERE` clause** preventing a data migration from overwriting configuration a user deliberately tuned [repo].
- **Fresh random IV per encrypted value** preventing GCM nonce reuse, which would leak plaintext across stored keys [repo].
- **Refusal to save keys when `LLM_SECRET_KEY` is absent** preventing a silent fallback to plaintext secret storage [repo].
- **Executor timeout on the LLM call** preventing a stalled provider response from pinning a virtual thread forever — the comment says so explicitly [repo].
- **Dropping empty resume sections** (`2d850a5`) preventing a LaTeX compile failure on a sparse profile [repo].
- **Repairing off-band bullets instead of discarding them** (`20ce564`, `0c49246`) preventing a generation run from silently returning fewer bullets than asked for [repo].
- **`ddl-auto: validate`** preventing entity drift from silently reshaping a production schema [repo].

Flagged, unresolved doc drift: README lines 39-40 still describe the pre-V21 word bands — "22-26 words for a one-line bullet, 42-50 for two lines; 27-40 lands in a dead zone" — which V21 and `GenerationConfig` replaced with 15-18 / 32-37 / dead zone 19-31 [repo]. The stale numbers are the ones a reader would quote in an interview.

# Category

`backend`, `ai-ml`
