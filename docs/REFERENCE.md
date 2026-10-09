# AnvilCV — full reference

Short version: [README](../README.md).

Paste a job description, get a tailored one-page resume PDF. AnvilCV keeps a bank of resume
bullets per project, ranks them against a JD with an LLM, compiles the winners through a LaTeX
template with Tectonic, and tracks what happened to every application you send.

Repo is `resuforge`; the Maven artifact and Java package are `resume-pipeline`. Same thing.

```
JD text or URL  ->  clean JD + keywords + 1-2 lenses (LLM)  ->  keyword pre-filter
                ->  rank bullets (LLM)  ->  select bullets, skills, courses (code)
                ->  resume.tex  ->  tectonic  ->  PDF  ->  recruiter pass (LLM, background)
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
- Story-based generation. One `findStories` call picks the strongest pieces of work and tags each
  with up to 3 of the four lenses (`ai-ml`, `backend`, `data`, `general`); evidence quotes not found
  in the source are dropped. Each new story is then one **slot** on its best lens, run by the
  [story bank pipeline](#story-bank-pipeline). Each kept story is saved as a `story` row (title,
  evidence quotes, lenses), and its wordings share its `story_id`. Retired lens slugs (`frontend`,
  `security`, `devops`, `systems`, `comms`) map to `general`: V42 remapped the stored rows, and new
  writes normalize the same way.
- Reruns build on the bank. `findStories` is shown the live stories, storyless bullets as
  covered work, and dismissed stories (every wording rejected), and asks for up to
  `min(8, 12 - live)` new ones. A found story is dropped as a repeat if at least half of its
  quotes (ignoring quotes under 40 chars) sit inside a saved one's, or its title is a
  near-duplicate. A project holds at most 12 live stories (`STORY_CAP`); a full bank makes no
  LLM call. One build per project at a time: a second gets 409 (the async submit fails the job).
  "More wordings" (`POST .../stories/{storyId}/wordings/submit`) runs one slot per chosen lens onto an
  existing story; it checks 400 / 404 / 409 before the job starts. Trashed (REJECTED) wordings are hidden
  and not counted; a story whose wordings are all trashed is deleted and is never re-found.
- Distinct progress messages when nothing is generated: no new stories, all duplicated, or bank
  full.
- Project and experience pages (`/projects/:id`, `/experiences/:id`): a header with the story count
  (a warning under 3, "Full" at 12), a description block, and Bullets · Generate · Repo tabs.
  - Bullets: wordings grouped under their story, sub-grouped by lens. Each wording can show its
    angle (outcome, decision, scale, failure) and the judge's note, and its Edit control is always
    there, whatever its status or lens. Trash is a soft delete (status `REJECTED`, hidden); Undo
    restores the previous status once. Failed writes show an error line and leave the list as it was.
  - Generate: three steps, Story -> Lens -> Generate. Pick *New stories* or one story, pick lenses
    (repeats allowed, a lens the story does not carry is a weak fit), then run it as a background job
    shown in the progress panel.
  - Repo: the file tree and viewer, the repo map and the explorer. Experiences have it too, behind a
    warning and an acknowledgement (see [What is stored and sent](#what-is-stored-and-sent)).
  - Experiences edit title, company, location and dates inline (one *I currently work here* box
    sets the end date to Present).
- XYZ format, with the Y (a measured result) only when the source states it.
- Deterministic filters: activity counts (commits, lines, tests...), filler sentences, numbers
  absent from the source (unit-aware: `40%` needs a percentage in the source; spelled-out units such
  as "3 million" or "5 seconds" count), banned openers, and lengths outside the one-line / two-line
  bands (a dead zone between is rejected; bands are per-user config, checked in rendered characters).
  The single-lens path (`POST /bullets/generate`) keeps one repair pass for what it cut on form; the
  story pipeline has no repair pass.
- Additive: generation never changes a bullet already in the bank, approved or not. If a story run
  gets an unreadable reply, the run fails; slots already saved stay saved, and the job error says how
  many wordings were saved. There is no per-lens fallback.
- Triage: the stored statuses are `PENDING`, `APPROVED` and `REJECTED`. The UI shows `PENDING` as
  Bullet and `APPROVED` as Approved; `REJECTED` is the trash and is hidden everywhere.
- Manual create/edit/tag, plus a rule-based importer for pasting in an existing resume.

**Tailoring pipeline**
- Accepts raw JD text or a JD URL. The scraper reads schema.org `JobPosting` JSON-LD, so
  Greenhouse / Lever / Workday / LinkedIn postings parse cleanly.
- JD cleanup also infers 1-2 role lenses (overridable on the new-application form). The lens
  only breaks ties; keywords decide.
- Keyword pre-filter (plural- and alias-aware, e.g. `c++`, `c#`): top 4 per project, one
  wording per story, 25 total, then the LLM ranks them.
- Selection is code: up to 6 entries at 3 bullets each under a one-page line budget, minimum 2
  experience and 3 project entries, no near-duplicates, one wording per story, no repeated
  opening verb within an entry, and unreviewed bullets with vanity counts skipped. An entry left
  with under 2 bullets is swapped for the next-best entry of the same kind (never a locked
  entry, the refit target, or one the kind floor needs).
- Across the whole page, a verb opens at most 2 bullets as a soft preference (`VERB_PAGE_CAP`). The
  selector looks at the next 3 ranks (`VERB_TIE_WINDOW`) and never passes over the top remaining
  bullet for a weaker one outside that window. Only bullets it keeps count toward the page's verbs.
- Two bullets of one story are not printed together by the automatic passes. The exception is
  bullets you lock yourself: locked bullets are seeded without that check, so two locked wordings
  of one story can both print.
- LLM also picks the relevant skill categories and coursework per JD. Skills come only from your
  profile; skills rows and each project's tech line list JD keywords first. The ATS report counts
  keywords in bullets, every printed skills row (AI & Integrations included; an empty saved row
  falls back to the profile row, as the renderer does), and heading tech lines.
- A recruiter-style review scores the page in the background after the PDF is ready.
- PDF downloads as `Last_Company_resume.pdf`.
- Application page: add any bank bullet, reorder, see duplicate and awkward-wrap warnings, and a
  persistent "PDF out of date" flag after edits until the next rerender. A rerender clears it only
  if `pdf_stale_seq` is unchanged, so an edit landing mid-compile keeps the flag.
- Async: submit returns a job UUID immediately, frontend polls progress every 1.5s.

**Tracking**
- Per-application outcome history, rendered as a d3-sankey flow diagram at `/flow`.
- LLM token usage and USD cost recorded per application.

**GitHub repos**

![GitHub repo to verified, source-traced bullet bank](repo-explorer-flow.svg)

Full writeup: [github-repo-explorer.html](github-repo-explorer.html).

- Connect a GitHub App with read-only access to the repos you pick, private included. See
  [GitHub App](#github-app).
- Import a repo as a project, pinned to a commit. The Repo tab shows its tree, the file viewer, the
  repo map and the explorer. The bullet bank is on the Bullets tab.
- A server-side explorer (an LLM tool loop over `list_tree` / `read_file` / `search_code` /
  `git_log`) fills project context from the code. It runs under step, file, token, and time budgets. Output is re-verified:
  evidence citing unread files is dropped, and sentences with numbers absent from the repo are
  cut.
- Steer it with pinned/excluded paths, lenses, and notes. Each bullet links to the files or
  commits it traces to.
- **Repo map**, built once per pinned commit from a single tarball download:
  - *Deterministic (no LLM):* per-language symbol/route skeletons, module links from
    cross-module references, PageRank so the code everything depends on ranks first, and facts
    counted from the code (tests, endpoints, migrations, dependencies, lines of code). These
    ground the explorer and summaries; the bullet filters cut activity counts used as results.
  - *LLM, bottom-up:* the top 15 modules get a what/how/why summary. Then the project gets an
    overview, its audience, 3-7 subsystems tagged with the lenses they feed, and 2-3 end-to-end
    flows.
  - Generation gets the overview plus the modules of the requested lenses' subsystems (or the
    subsystems you tick) as source material for the story pass.
- <a id="what-is-stored-and-sent"></a>**What is stored and sent.** Stored on the project: the repo map
  (module summaries, purposes, facts, flows) and `repo_evidence`, which holds the source lines the
  explorer cites (up to 15 lines per claim, verbatim). Story evidence quotes and wording text are
  stored too. The full repository is downloaded into memory for a run and is not written to disk.
  Sent to the AI provider you configured: each top module's first source file (its head), the README,
  the manifests, the explorer's file reads (up to 12,000 characters per read), and the source material
  for bullet generation. The Repo tab for an experience shows this as a warning, and the explore and
  map buttons stay disabled until you acknowledge it.

**Jobs**
- `/jobs` feed with remote, added-within, tech-stack chip, and "My skills" (stack overlaps your
  profile) filters. Filters are capped: 20 stack tags of 50 chars, `q` and `location` 100 chars.
- Postings arrive through a signed webhook (`POST /api/public/jobs/webhook`, HMAC-SHA256 in
  `X-Jobs-Signature`; off while `JOBS_WEBHOOK_SECRET` is blank). Stack tags are normalised at
  ingest (trimmed, comma-free, case-insensitive repeats dropped).

**Admin**
- Bullet Eval (admin only): snapshot the live bank, dry-run the current generator on chosen
  projects without saving, and compare two sets side by side with deterministic metrics. Items
  keep their `storyId`, and coverage replays the real selectable bank (no rejected, no unreviewed
  vanity, one wording per story).
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
| `JOBS_WEBHOOK_SECRET` | — | secret; blank disables the jobs webhook |

Hikari is tuned for Neon's scale-to-zero (pool 5, min idle 0, 4-minute max lifetime).
`tectonic.timeout-seconds` is 30.

---

### GitHub App

| Var | Notes |
|---|---|
| `GITHUB_APP_ID` | numeric App ID |
| `GITHUB_APP_SLUG` | the `<slug>` in `github.com/apps/<slug>` |
| `GITHUB_APP_CLIENT_ID` / `GITHUB_APP_CLIENT_SECRET` | secret; the app's OAuth pair |
| `GITHUB_APP_PRIVATE_KEY` | secret; the downloaded `.pem`, multi-line or one line with `\n` escapes |

All blank disables the feature: the GitHub endpoints answer 503 and nothing else changes.

---

## GitHub App

One app per AnvilCV deploy, created once by whoever runs it:

1. GitHub > Settings > Developer settings > GitHub Apps > **New GitHub App**.
2. **Callback URL**: `https://<API host>/api/github/callback` (`http://localhost:8080/...`
   locally). On a split deploy this is the **backend's** domain. After connecting, the browser
   returns to `FRONTEND_ORIGIN` (override with `GITHUB_RETURN_ORIGIN`). Tick **Request user authorization (OAuth) during installation**.
3. **Webhook**: untick *Active*. Nothing here uses webhooks.
4. **Repository permissions**: *Contents* read-only and *Metadata* read-only. Nothing else.
5. **Where can this app be installed**: *Any account* if other people use your instance.
6. Create it, then copy the App ID and Client ID, generate a client secret and a private key,
   and set the env vars above.

How the connect flow works: `/api/github/connect` stores a one-time `state` in the session and
sends the user through the app's OAuth page. The callback checks `state`, exchanges `code` for a
user token, and links an installation only if that token lists it (the `installation_id` query
param alone is forgeable). If the user has no installation yet, they go to the install page.
GitHub returns from there with `code` + `installation_id` but possibly no `state`, so the
callback restarts OAuth, which bounces straight back with a `state` it can check. Then it drops
the user token. Only the
installation id is stored. Installation tokens are minted per use from the private key, narrowed
to `contents:read` + `metadata:read`, and cached in memory until near expiry.

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
| `src/main/resources/db/migration/` | Flyway migrations, `V1`-`V43` |
| `src/main/resources/template/resume.tex` | LaTeX resume template with `{{TOKEN}}` placeholders |
| `src/main/resources/static/` | Vite build output (git-ignored) — Spring serves the SPA from here |
| `src/main/resources/repo-explorer-instructions.md` | System prompt for the server-side repo explorer |
| `src/test/java/` | about 585 backend tests |
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
| `bullet` | Bullet CRUD, the story bank (`BulletService`: the per-slot pipeline, judge pairing and saving) |
| `application` | `ApplicationService` (the pipeline), `BulletSelector`, `ApplicationRenderer` |
| `llm` | `LlmClient` interface, `RoutingLlmClient`, provider clients, `KeywordScorer`, `BulletTextRules`, `CategoryLenses`, GitHub context fetch, token accounting |
| `llm.settings` | `LlmSettings` entity + `SecretCipher` (AES-256-GCM) |
| `render` | `LatexEscaper`, `LatexRenderer`, `PdfCompiler` |
| `jd` | `JdFetcher` — URL scraping and JSON-LD extraction |
| `github` | GitHub App auth (JWT, installation tokens), `GithubClient`, `RepoSnapshot` (tarball), `RepoMapBuilder` / `RepoMapService` / `RepoMapRenderer` (repo map), `RepoExplorer` tool loop, `SourceTracer` |
| `progress` | `ProgressLog`, `PipelineTimer` |
| `config` | Per-user `GenerationConfig`, SPA deep-link fallback |

### API surface

```
public   POST /api/login  /api/register  /api/logout   GET /api/me  /api/ping
         GET  /api/public/stats

profile  GET|PUT /api/profile
config   GET|PUT /api/config/generation
projects GET|POST /api/projects          GET|PUT|DELETE /api/projects/{id}
         POST /api/projects/{id}/duplicate
         POST /api/projects/{id}/bullets/generate  /bullets/refit
         GET  /api/projects/{id}/stories
         POST /api/projects/{id}/stories/submit                   (new stories, job)
         POST /api/projects/{id}/stories/{storyId}/wordings/submit (more wordings, job; 400/404/409)
         GET  /api/projects/jobs/{jobId}/progress
bullets  GET|POST /api/projects/{projectId}/bullets
         PUT|DELETE /api/bullets/{id}    PATCH /api/bullets/{id}/status
         POST /api/bullets/preview
apps     GET /api/applications           GET /api/applications/outcome-history
         POST /api/applications/submit   GET /api/applications/jobs/{jobId}/progress
         GET|POST|PATCH|DELETE /api/applications/{id}
         POST /api/applications/{id}/rerender[/submit]  /refit-selection[/submit]
         POST /api/applications/{id}/rescore/submit     PATCH /api/applications/{id}/locks
         POST /api/applications/selection-check         (read-only repeat warnings)
         GET  /api/applications/{id}/pdf            (application/pdf)
         GET  /api/applications/{id}/tex            (application/x-tex)
         GET  /api/applications/{id}/cover-letter   (text/plain)
jobs     GET /api/public/jobs  /api/public/jobs/tags     PUT|DELETE /api/jobs/{id}/save
         POST /api/public/jobs/webhook   (public, HMAC-signed)
import   POST /api/resume/parse          POST /api/resume/import
admin    GET /api/admin/stats  /logs  /bullet-measure-diagnostics
         GET|PUT /api/admin/llm          POST /api/admin/llm/test
         GET /api/admin/eval/sets  /compare   POST /api/admin/eval/snapshot  /generate
         DELETE /api/admin/eval/sets/{id}
github   GET /api/github/status  /connect  /callback  /repos      DELETE /api/github
         POST /api/github/link           GET /api/github/projects/{id}/tree|file|map|bullet-sources   (bullet-sources is still served; the UI no longer calls it)
         POST /api/github/projects/{id}/explore/submit[?rebuildMap=true]   (polls /api/projects/jobs/{jobId}/progress)
```

Session-cookie auth (`JSESSIONID`, httpOnly), BCrypt passwords, `/api/admin/**` gated on
`ROLE_ADMIN`, everything else under `/api/**` authenticated, 401 entry point.

### Frontend routes

`/` landing, `/login`, `/register`, `/docs`, `/pricing`, and `/jobs` are public, as are the
`/lab/*` UI prototypes (placeholder data, no API calls). `/projects`, `/experiences`,
`/projects/:id`, `/experiences/:id`, `/new`, `/applications`, `/applications/:id`, `/flow`,
`/profile`, `/settings`, `/admin`, `/upload` sit behind `RequireAuth`. The production pages never
import from `frontend/src/lab/`; the lab prototypes are kept for reference and can be removed without
touching production.

### Story bank pipeline

Entry point: `BulletService.generateBank` (used by `POST /stories/submit`, and by the eval dry run).
`generateStories` runs `findStories` once, drops repeats, then runs one slot per new story on its
best lens. Each slot is saved as soon as it finishes.

Per slot (`runSlot`):

1. **Write.** `writeSlotCandidates` asks for 15 wordings (`CANDIDATES_PER_SLOT`), each labelled with
   one of four angles: `outcome`, `decision`, `scale`, `failure` (other labels become null). The prompt
   asks for no verb to open more than two wordings; the code does not count that.
2. **Code filter** (same call). Drops banned openers, activity counts (`vanityCount`), padded
   sentences, numbers not in the source (unit-aware, including spelled-out units), and lengths outside
   the bands. Tags are kept only if the bullet mentions them.
3. **Dedup.** Against the bank, live and trashed, and against the wordings kept so far in the run
   (`BulletTextRules.isNearDuplicate`: word overlap at 0.6, or the same numbers in different prose).
4. **Code score**, 0-100 (`codeScore`): length 30 (constant, since every kept wording is in band),
   outcome 30 when a number appears, 15 for a result verb without one, tech 20 when a tag is in the
   project's tech stack, 10 for tags that are not, opener 20 (fresh), 10 (repeated in this batch), 0
   (already in the bank). The top 5 (`JUDGE_TOP_N`) go to the judge; a slot with one candidate skips the
   judge.
5. **Judge** (`judgeTop`, `scoreCandidates`). Two runs over the top 5: one in a shuffle seeded from
   the story and lens (so reruns match), one in reverse, so position bias cancels. Each run gives a
   1-5 score and a short good/bad note per candidate, against a checklist (supported by the evidence,
   concrete outcome or failure prevented, specific tech, one plain sentence, no inflated ownership).
   Scores are averaged over the runs that were readable.
6. **Pick #1** is the best average (ties go to the better code score). **Pick #2** must be within
   `PAIR_SCORE_MARGIN` (1.5) of pick #1's average; have a different angle when both angles are known;
   and have similarity below `ANGLE_SIMILARITY_MAX` (0.35). Among those, the one with the highest score
   after the variety penalty (average minus `VARIETY_PENALTY`, 2.0, times similarity) wins. If none
   qualifies, the slot keeps one wording and logs `BULLET_PAIR`.
7. **Fallback.** If neither judge run is readable, the slot keeps the top two by code score, with no
   notes. A slot with one candidate keeps it with no note.

Constants and where they live: `BulletService` (`STORY_CAP` 12, `MAX_NEW_STORIES` 8,
`MIN_OVERLAP_QUOTE` 40, `CANDIDATES_PER_SLOT` 15, `KEEP_PER_SLOT` 2, `JUDGE_TOP_N` 5,
`VARIETY_PENALTY` 2.0, `ANGLE_SIMILARITY_MAX` 0.35, `PAIR_SCORE_MARGIN` 1.5, `SCORE_*`), `BaseLlmClient`
(`JUDGE_NOTE_MAX` 300), `BulletSelector` (`VERB_PAGE_CAP` 2, `VERB_TIE_WINDOW` 3).

Failure: a slot that throws stops the run. Earlier slots are already saved, and the job error reads
`N wording(s) saved before the failure: <cause>` (`BulletService.PartialRunException`, whose cause is
the original exception). A parse failure is not retried per lens; it fails the run the same way.

Reading the logs (`BULLET_JUDGE`, `BULLET_PAIR`, `BULLET_SLOT` in the application log):
- `BULLET_JUDGE story=… pick_code_rank=R runs=N agreed=B`: one line per judged slot. `R` is the code
  rank of pick #1 among the top 5. A high share of `R=1` means the judge mostly agrees with the code
  score (it may be rubber-stamping the top candidate). `agreed=false` means the two runs disagreed on
  the best candidate; a high share means the judge is noisy. `runs=1` means one run was unreadable.
- `BULLET_PAIR story=… single: no second wording qualifies (…)`: logged only when no second wording
  qualified. A slot that kept two wordings logs no `BULLET_PAIR` line, so the single-wording rate is
  the count of these lines over the count of slots that reached the judge.
- `BULLET_SLOT story=… lens=… written=W kept=K`: the write and filter stage for one slot.
- `BULLET_JUDGE_FAILED` and `BULLET_JUDGE_MISMATCH`: a judge call failed, or its reply did not line up
  with the candidates.

### Async model

No scheduler and no queue. Long work runs on `newVirtualThreadPerTaskExecutor()`; submit endpoints
return a job UUID and the client polls. Progress is **in-memory only** — a restart drops running
jobs, and it does not survive horizontal scaling.

---

## Data model

| Table | Notes |
|---|---|
| `app_user` | UUID pk, unique username/email, bcrypt hash, `is_admin` |
| `profile` | one per user; education JSONB, five skill-category columns (one renders as "AI & Integrations") |
| `project` | `kind` = PROJECT \| EXPERIENCE; GitHub URL + repo context; enrichment fields (tech stack, role, ownership, scale/impact, hardest problem) |
| `bullet` | text, tags, category, `status` PENDING/APPROVED/REJECTED (REJECTED is the trash), `story_id` (groups wordings of one story; FK to `story`, deferred, on delete set null), `angle` (outcome/decision/scale/failure, nullable, V43), `judge_note` (the judge's good/bad note, nullable, V43; edits and refits keep it) — cascades from project |
| `story` | one per kept story: title, `evidence[]` quotes, `lenses[]`; app-assigned id, cascades from project. V41 backfilled rows for existing story ids |
| `application` | JD text/URL, ranking JSONB, selected bullet IDs, cover letter, ATS matched/missing, `tex_blob` + `pdf_blob`, `pdf_stale` + `pdf_stale_seq`, tectonic log, token counts and cost, pipeline duration |
| `outcome_history` | one row per outcome change — feeds the sankey; cascades from application |
| `generation_config` | per-user word-filter bounds, temperature, bold density, tone, verb style |
| `llm_usage_log` | per-call tokens and cost, nullable app/project FKs |
| `llm_settings` | singleton row: provider, encrypted keys, base URLs, per-call models |
| `github_installation` | one per user: GitHub App installation id + account login. No tokens |
| `eval_set` | admin bullet eval: frozen bank snapshots and dry-run generations, items as JSONB |
| `job_posting` / `saved_job` | jobs feed postings (from the webhook) and per-user saves |
| `bullet_measure_diagnostic` | admin diagnostics: estimated vs compiled bullet line counts |

`project` also carries `repo_branch`, `repo_commit_sha` (the pinned snapshot),
`repo_evidence` (JSON of the explorer's verified spans, used for bullet tracing), and
`repo_map` (JSON repo map for that commit, reused until it changes).

Flyway owns the schema (`out-of-order: true`); JPA only validates it (`ddl-auto: validate`). Migrations
V37-V43 are the story bank: V37 `bullet.story_id`, V38 eval sets, V39-V40 PDF staleness, V41 `story`,
V42 remaps lenses to the four current ones, V43 `bullet.angle` and `bullet.judge_note`.

---

## Tests and CI

```bash
mvn test                    # or `mvn -B verify`, as CI runs it
cd frontend && npm test     # vitest run
```

Backend tests are all fast units — Mockito service tests plus `@WebMvcTest` slices. No
Testcontainers, no `@SpringBootTest`, **no database needed**. Heaviest coverage sits on
`BulletTextRules`, `KeywordScorer`, `LatexEscaper`, `BulletSelector`, and `ApplicationService`.
Frontend: 8 files, 108 tests (`vitest run`). Backend: about 585 test cases.

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
15. **GitHub callback lands on `/settings` of the API origin.** On a split-origin dev setup
    (`npm run dev` on 5173), you end up on 8080's built SPA after connecting.
16. **Repo exploration costs LLM tokens on your key.** The first run on a commit builds the map
    (about 16 calls: 15 modules on the JD-cleanup model plus 1 project call), then up to 40
    explorer steps. Later runs reuse the map. All of it is logged to `llm_usage_log` as
    `repo_explore`.
17. **Symbol extraction is regex, not a parser.** It covers Java/Kotlin, TS/JS, Python, and Go
    well, and other languages loosely. A missed symbol only weakens ranking. Nothing it extracts
    is stated as fact except the counts, and each count names what it counted.
18. **Big repos:** the tarball is capped at 100 MB compressed, and the snapshot keeps 25 MB of
    text. Past that, files are dropped and the map says `(repo truncated)`.
19. **V43 must be applied before this code runs.** It adds `bullet.angle` and `bullet.judge_note`.
    Hibernate runs with `ddl-auto: validate`, so the app will not start against a database that lacks
    these columns. V43 has not been applied to any database yet.

## Known limitations

- **Repo map numbers.** The map's unsourced-number check runs over a corpus of bare numbers, so a map
  sentence with a unit ("600 seconds", "90%") is cut unless the unit appears in that corpus. Fixing it
  properly means building the corpus from source text.
- **Judge cost.** A slot with two or more candidates makes two judge calls, so judge cost is roughly
  doubled. The two runs are sequential; they are not parallelised.
- **Locked bullets.** Two locked wordings from one story can print together (see the selector notes).
- **Context fields have no editing UI.** Tech stack, role, ownership, scale and impact come from the
  GitHub explorer. The Info & Context tab and its paste route were removed, so without GitHub there is
  no way to enter them in the UI.
- **Progress panel on phones.** The shared progress panel is inline-styled and may not wrap long log
  lines on a phone.
- **Off-band bullets.** Bullets outside the length bands (the dead zone, too long, or too short) are not
  fixed automatically. Press REFIT on the project page to rewrite them.
- **Visual check.** The new pages have not been compared by eye with the lab prototypes, and the phone
  layout has not been checked on a device.
