# AnvilCV

Paste a job description, get a tailored one-page resume PDF. AnvilCV keeps a bank of resume
bullets per project, ranks them against a JD with an LLM, compiles the winners through a LaTeX
template with Tectonic, and tracks what happened to every application you send.

Repo is `resuforge`; the Maven artifact and Java package are `resume-pipeline`. Same thing.

```
JD text or URL  ->  clean JD (LLM)  ->  keyword prefilter  ->  rank bullets (LLM)
                ->  select <=15 bullets, skills, courses  ->  resume.tex  ->  tectonic  ->  PDF
```

Typical run: 1-3 minutes, dominated by LLM latency. Cover letter is generated in parallel.

---

## Contents

- [Features](#features)
- [Stack](#stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [LLM providers and keys](#llm-providers-and-keys)
- [Repo layout](#repo-layout)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Tests and CI](#tests-and-ci)
- [Deployment](#deployment)
- [Gotchas](#gotchas)

---

## Features

**Bullet bank**
- Per-project bullet generation through eight "category lenses" (`ai-ml`, `backend`, `frontend`,
  `data`, `security`, `devops`, `systems`, `comms`), fanned out on virtual threads.
- Word-count rules enforced in code: 22-26 words for a one-line bullet, 42-50 for two lines;
  27-40 lands in a dead zone and is rejected. Failing bullets are repaired, not discarded.
- Triage workflow — every bullet is `PENDING`, `APPROVED`, or `REJECTED`.
- Manual create/edit/tag, plus a rule-based importer for pasting in an existing resume.

**Tailoring pipeline**
- Accepts raw JD text or a JD URL. The scraper reads schema.org `JobPosting` JSON-LD, so
  Greenhouse / Lever / Workday / LinkedIn postings parse cleanly.
- Cheap keyword scorer prefilters the bank before the expensive ranking call.
- Selection caps: 15 bullets total, 3 per project, minimum 2 experience and 3 project entries.
- LLM also picks the relevant skill categories and coursework per JD.
- Async: submit returns a job UUID immediately, frontend polls progress every 1.5s.

**Tracking**
- Per-application outcome history, rendered as a d3-sankey flow diagram at `/flow`.
- LLM token usage and USD cost recorded per application.

**Admin**
- `/admin` — LLM provider, API keys, base URLs, and per-call models. Keys encrypted at rest
  (AES-256-GCM), masked on read, live-tested against the provider before save.
- Enforced server-side with `hasRole("ADMIN")`; the nav-link gate is cosmetic.

**Multi-user** — profile, projects, bullets, and applications are scoped per account, never shared.

---

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.4.0 (web, data-jpa, security, validation) |
| DB | PostgreSQL (Neon in prod), Flyway migrations, Hibernate `ddl-auto: validate` |
| Frontend | React 18.3, TypeScript 5.6, Vite 5.4, react-router 6.28, framer-motion, d3-sankey |
| LLM | google-genai 1.18 (Gemini) + OpenAI-compatible clients (OpenAI, OpenCode/Zen) |
| PDF | LaTeX template compiled by the `tectonic` binary |
| Other | jsoup (JD scraping), bucket4j (register rate limit) |
| Tests | JUnit 5 + Mockito + `@WebMvcTest`; Vitest on the frontend |
| Build | Maven (no wrapper — needs `mvn` on PATH) + npm |

---

## Quick start

**Prereqs:** Java 21, Maven, Node 18+, a PostgreSQL database, and `tectonic` on PATH if you want
PDFs. An LLM API key if you want ranking and JD analysis.

### 1. Create `src/main/resources/application-local.yml`

Git-ignored and excluded from the Docker build context.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/resume_pipeline
    username: postgres
    password: postgres

auth:
  seed:
    username: yourname
    email: you@example.com
    password: yourpassword     # blank creates an admin with an EMPTY password

llm:
  gemini:
    api-key: your_gemini_api_key

tectonic:
  binary: tectonic
```

The database needs the `pgcrypto` extension — Flyway's `V1__init.sql` calls `gen_random_uuid()`.

### 2. Run

Windows:

```powershell
./start.ps1            # -Frontend forces a UI rebuild, -NoBrowser skips opening the browser
```

Preflights the config file, tectonic, and port 8080; rebuilds the frontend if `frontend/src` is
newer than the built output; then starts the backend.

macOS / Linux (no script — two steps):

```bash
(cd frontend && npm install && npm run build)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Everything is served from **http://localhost:8080** — UI and API on one origin. The seed user is
created or updated on startup, so log in with the `auth.seed.*` credentials.

### 3. Frontend hot reload (optional)

```bash
cd frontend && npm run dev      # http://localhost:5173
```

The only mode that crosses origins, so the only one that needs `FRONTEND_ORIGIN` and CORS.

---

## Configuration

Everything resolves through `src/main/resources/application.yml`. See `.env.example`.

### Required

| Var | Notes |
|---|---|
| `DB_URL` | JDBC URL. On Neon use the **direct** endpoint, not `-pooler` |
| `DB_USER` | |
| `DB_PASSWORD` | secret |

### LLM

| Var | Default | Notes |
|---|---|---|
| `LLM_PROVIDER` | `gemini` | `gemini` \| `openai` \| `opencode` |
| `LLM_SECRET_KEY` | — | secret; base64, 32 bytes. Needed to *save* keys from `/admin` |
| `GEMINI_API_KEY` | — | secret |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` | — / `https://api.openai.com/v1` | base URL also covers OpenRouter, Ollama, LM Studio |
| `OPENCODE_API_KEY` / `OPENCODE_BASE_URL` | — / `https://opencode.ai/zen/v1` | |
| `OPENCODE_MODEL_GENERATE` / `_MATCH` / `_CLEAN_JD` | `x-preview-f-free`, `x-preview-f-free`, `deepseek-v4-flash-free` | |

Gemini defaults: `gemini-2.5-flash` for generate and match, `gemini-2.5-flash-lite` for JD cleanup.
OpenAI defaults to `gpt-4o-mini` for all three.

### Everything else

| Var | Default | Notes |
|---|---|---|
| `TECTONIC_BIN` | `tectonic` | PDF only |
| `SEED_USERNAME` / `SEED_EMAIL` / `SEED_PASSWORD` | `f3l` / `admin@localhost` / — | seed admin; blank password only warns |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | split-origin deploys only |
| `COOKIE_SAME_SITE` / `COOKIE_SECURE` | `Lax` / `false` | set `None` / `true` behind a tunnel |
| `TUNNEL_TOKEN` | — | secret; `compose.yml` cloudflared only |

Hikari is tuned for Neon's scale-to-zero (pool 5, min idle 0, 4-minute max lifetime).
`tectonic.timeout-seconds` is 30.

---

## LLM providers and keys

Provider, keys, base URLs, and per-call models live in the `llm_settings` table (a hard singleton
row) and are edited at `/admin`. `RoutingLlmClient` resolves the provider **per call**, cached
against `updated_at`, so a save takes effect without a redeploy.

The `llm:` block in YAML is the **fallback**: a NULL column in the DB falls through to env, so an
existing deploy keeps running and env stays a backstop if a bad save locks you out.

Keys in the DB are encrypted with AES-256-GCM. The cipher key cannot live in the database it
protects, so it stays in the environment:

```bash
LLM_SECRET_KEY=$(openssl rand -base64 32)
```

N provider keys collapse to one `LLM_SECRET_KEY`. Unset, the app still runs on env keys but
**refuses to save new ones** rather than writing plaintext. Losing or rotating it makes every
stored key undecryptable — they have to be re-entered.

---

## Repo layout

| Path | Contents |
|---|---|
| `src/main/java/com/resumepipeline/` | Spring Boot backend |
| `src/main/resources/db/migration/` | Flyway migrations, `V1`-`V20` |
| `src/main/resources/template/resume.tex` | LaTeX resume template with `{{TOKEN}}` placeholders |
| `src/main/resources/static/` | Vite build output (git-ignored) — Spring serves the SPA from here |
| `src/main/resources/content_extract.md` | Prompt doc served by `GET /api/tools/content-extract` |
| `src/test/java/` | ~192 backend tests |
| `frontend/` | React + Vite SPA |
| `.github/workflows/ci.yml` | Maven job + Vite job |
| `start.ps1`, `Dockerfile`, `compose.yml`, `pom.xml` | Build and run |

---

## Architecture

### Backend packages

| Package | Role |
|---|---|
| `api` | REST controllers, DTOs, `JobProgressStore` (in-memory async job progress, owner-checked) |
| `auth` | `SecurityConfig`, login/register, seed user runner, bucket4j register rate limit |
| `profile` | One profile row per user — contact, education (JSONB), skill categories |
| `project` | Projects and EXPERIENCE entries, plus the regex resume importer |
| `bullet` | Bullet CRUD and parallel per-category generation |
| `application` | `ApplicationService` (the pipeline), `BulletSelector`, `ApplicationRenderer` |
| `llm` | `LlmClient` interface, `RoutingLlmClient`, provider clients, `KeywordScorer`, `BulletTextRules`, `CategoryLenses`, GitHub context fetch, token accounting |
| `llm.settings` | `LlmSettings` entity + `SecretCipher` (AES-256-GCM) |
| `render` | `LatexEscaper`, `LatexRenderer`, `PdfCompiler` |
| `jd` | `JdFetcher` — URL scraping and JSON-LD extraction |
| `progress` | `ProgressLog`, `PipelineTimer` |
| `config` | Per-user `GenerationConfig`, SPA deep-link fallback |

### API surface

```
public   POST /api/login  /api/register  /api/logout   GET /api/me  /api/ping
         GET  /api/public/stats          GET /api/tools/content-extract

profile  GET|PUT /api/profile
config   GET|PUT /api/config/generation
projects GET|POST /api/projects          GET|PUT|DELETE /api/projects/{id}
         POST /api/projects/{id}/bullets/generate
         POST /api/projects/{id}/bullets/generate-bank[/submit]
         GET  /api/projects/jobs/{jobId}/progress
bullets  GET|POST /api/projects/{projectId}/bullets
         PUT|DELETE /api/bullets/{id}    PATCH /api/bullets/{id}/status
apps     GET /api/applications           GET /api/applications/outcome-history
         POST /api/applications/submit   GET /api/applications/jobs/{jobId}/progress
         GET|POST|PATCH|DELETE /api/applications/{id}
         POST /api/applications/{id}/rerender[/submit]
         GET  /api/applications/{id}/pdf            (application/pdf)
         GET  /api/applications/{id}/cover-letter   (text/plain)
import   POST /api/resume/parse          POST /api/resume/import
admin    GET /api/admin/stats            GET|PUT /api/admin/llm   POST /api/admin/llm/test
```

Session-cookie auth (`JSESSIONID`, httpOnly), BCrypt passwords, `/api/admin/**` gated on
`ROLE_ADMIN`, everything else under `/api/**` authenticated, 401 entry point.

### Frontend routes

`/` landing, `/login`, `/register`, and `/docs` are public. `/projects`, `/experiences`,
`/projects/:id`, `/experiences/:id`, `/new`, `/applications`, `/applications/:id`, `/flow`,
`/profile`, `/settings`, `/admin`, `/upload` sit behind `RequireAuth`.

### Async model

No scheduler and no queue. Long work runs on `newVirtualThreadPerTaskExecutor()`; submit endpoints
return a job UUID and the client polls. Progress is **in-memory only** — a restart drops running
jobs, and it does not survive horizontal scaling.

---

## Data model

| Table | Notes |
|---|---|
| `app_user` | UUID pk, unique username/email, bcrypt hash, `is_admin` |
| `profile` | one per user; education JSONB, five skill-category columns |
| `project` | `kind` = PROJECT \| EXPERIENCE; GitHub URL + repo context; enrichment fields (tech stack, role, ownership, scale/impact, hardest problem) |
| `bullet` | text, tags, category, `status` PENDING/APPROVED/REJECTED — cascades from project |
| `application` | JD text/URL, ranking JSONB, selected bullet IDs, cover letter, ATS matched/missing, `tex_blob` + `pdf_blob`, tectonic log, token counts and cost, pipeline duration |
| `outcome_history` | one row per outcome change — feeds the sankey; cascades from application |
| `generation_config` | per-user word-filter bounds, temperature, bold density, tone, verb style |
| `llm_usage_log` | per-call tokens and cost, nullable app/project FKs |
| `llm_settings` | singleton row: provider, encrypted keys, base URLs, per-call models |

Flyway owns the schema (`out-of-order: true`); JPA only validates it.

---

## Tests and CI

```bash
mvn test                    # or `mvn -B verify`, as CI runs it
cd frontend && npm test     # vitest run
```

Backend tests are all fast units — Mockito service tests plus `@WebMvcTest` slices. No
Testcontainers, no `@SpringBootTest`, **no database needed**. Heaviest coverage sits on
`BulletTextRules`, `KeywordScorer`, `LatexEscaper`, `BulletSelector`, and `ApplicationService`.
Frontend coverage is thin: two files.

CI runs on push to `main` and on every PR — Temurin 21 + `mvn -B verify`, and Node 20 +
`npm ci && npm test && npm run build`.

---

## Deployment

### Docker

```bash
docker build -t resume-pipeline .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/resume_pipeline \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e SEED_USERNAME=yourname -e SEED_EMAIL=you@example.com -e SEED_PASSWORD=... \
  -e GEMINI_API_KEY=... -e LLM_SECRET_KEY=... \
  resume-pipeline
```

Multi-stage build (`maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-jammy`). It pins
tectonic 0.15.0 from GitHub releases, switching on `TARGETARCH` for amd64/arm64, and pre-warms it
with a dummy `.tex` so the first real compile does not stall downloading LaTeX packages.

### Compose + Cloudflare Tunnel

`compose.yml` runs `app` (from `.env`, 3 GB memory limit, **no published ports**) alongside
`cloudflared`. Nothing inbound is open on the host — cloudflared reaches the JVM over the compose
network. Behind a tunnel set `COOKIE_SECURE=true`.

### Build artifacts

```bash
mvn package                        # target/resume-pipeline-0.1.0.jar
cd frontend && npm run build       # dist/ and a copy into src/main/resources/static/
```

---

## Gotchas

1. **`application-local.yml` must be created by hand** — `start.ps1` hard-fails without it.
2. **Never commit real credentials to it.** It is git-ignored and `.dockerignore`d, but treat any
   database password or API key that has ever been written there as needing rotation before the
   repo or an image is shared.
3. **No Maven wrapper.** `mvn` must be on PATH; there is no `mvnw`.
4. **`start.ps1` is PowerShell-only.** On macOS/Linux run the two manual steps.
5. **The Vite build wipes `src/main/resources/static/`** and copies `dist/` into it. Skip
   `npm run build` and Spring silently serves the previous UI.
6. **A blank `SEED_PASSWORD` creates an admin with an empty password** — it only logs a warning.
7. **Registrations are not admins** and there is no promotion UI. Log in as the seed user, or
   `UPDATE app_user SET is_admin = true WHERE username = '...'`.
8. **CSRF is disabled** while auth is cookie-based. That is only safe while the frontend and API
   share a registrable domain. A separately hosted frontend forces `COOKIE_SAME_SITE=None`, which
   leaves the API reachable cross-site — fix CSRF before deploying that way.
9. **On Neon, use the direct endpoint, not `-pooler`.** PgBouncer transaction pooling breaks pgjdbc
   prepared statements and Flyway's session advisory lock.
10. **Tectonic degrades silently.** Without it, every PDF compile fails and `start.ps1` only warns.
    A cold first compile downloads LaTeX packages and may exceed the 30s timeout.
11. **`LLM_SECRET_KEY` is unrecoverable** — see [LLM providers and keys](#llm-providers-and-keys).
12. **Cost accounting only prices Gemini Flash and Flash-Lite.** OpenCode free-tier models are
    zeroed; OpenAI calls are not rated by the table in `TokenAccumulator`.
13. **Job progress is in-memory**, so a restart loses it and multi-instance deploys break polling.
14. No linter, formatter, `CONTRIBUTING.md`, or `LICENSE` in the repo yet.
