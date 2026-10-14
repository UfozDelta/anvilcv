# Code Review Plan

Source: 6 Haiku reviewers (api/auth, llm/jobs, github/bullet/project, migrations/tests/infra, frontend app, frontend lab). ~90 raw findings, deduped below.

**Caveat:** findings are model-generated, read-only, nothing run. Verify each against code before fixing (marked `verify` where a claim is shaky).

## Verification pass (manual, against source)

**Confirmed real:**
- SSRF — `JdFetcher.java:35`, no host/scheme check, redirects on.
- Blank `SEED_PASSWORD` → admin with `""` password; `LoginRequest` has no `@Valid`, so `""` isn't even rejected. (Already noted in `docs/REFERENCE.md:593` as known.)
- `PdfCompiler.java:73` — `readAllBytes()` blocks before `waitFor`, timeout is dead code.
- `BaseLlmClient.java:72` — cause is `RestClientException`, never `TimeoutException`; timeouts are retried despite comment.
- `@Valid` missing on login/register/`GenerationConfigController` (present on application/project/bullet/resume controllers).
- `JobProgressStore.cleanup()` has zero callers → jobs leak forever.
- `ApiExceptionHandler` catch-all `RuntimeException` swallows `HttpMessageNotReadableException` etc. → 500 with raw message.
- V7 drops singleton constraints, only non-unique `user_id` index.
- **Partial PUT wipes data (new top bug):** `ProjectService.update` sets every field except name/description unconditionally (`:130-144`). `DescBlock` PUTs `{description}`, `MetaBlock` PUTs 5 fields → saving a description nulls `githubUrl`, `techStack`, `contextDescription`, role/ownership fields etc.
- `EventStream` `onDone` only fires via button, hidden when `doneLabel=""`; `RepoTab`/`StoryGenerate` pass `""` → no refresh after run.
- `ProjectService.create/update` call `@Async fetchAndCacheRepoContext` via `this` → runs sync.
- `Jobs.tsx:237` renders `j.url` unchecked (feed trust not verified).

**Downgraded:**
- V6 seed hash: `SeedUserRunner` overwrites hash every boot, so exposure is only before first start → low.
- V3 `DROP COLUMN experience`: historic migration, likely already run → moot; just document.
- Cookie default: `.env.example` already sets `COOKIE_SECURE=true`, docs say set behind tunnel → verify prod env only.

**Not yet verified:** everything else (P1 #3–6, #9, #11–23; P2 #3–13; P3–P5). Treat as leads.

## P0 — Security / data loss (do first)

| # | Issue | Where | Fix |
|---|-------|-------|-----|
| 1 | **SSRF**: caller `jdUrl` fetched by Jsoup, no host/IP check, body returned to user | `jd/JdFetcher.java:35`, `ApplicationService.java:138` | https only; resolve host, reject loopback/link-local/private; re-check redirects; `maxBodySize`; catch `IllegalArgumentException` |
| 2 | **Blank seed password = admin login**: `SEED_PASSWORD` defaults empty, `encode()` runs before null check, admin reset every boot | `auth/SeedUserRunner.java:45-62`, `application.yml:133-135` | Fail startup if blank; null-check before encode; drop hardcoded username default; reject blank passwords at login |
| 3 | **Committed seed credential**: V6 inserts known bcrypt hash, V19 sets admin | `V6__app_user.sql:11-17`, `V19__admin_flag.sql:5` | New migration neutralizes hash (`'!'`); stop setting admin in SQL (can't edit applied migrations — Flyway checksum) |
| 4 | **Cookie defaults insecure**: `COOKIE_SECURE=false`, CSRF off | `application.yml:24-25` | Default `true`; confirm prod env |
| 5 | **No login throttle**; catch-all maps DB outage to 401 | `AuthController.java:55-77` | Limiter on login; catch `AuthenticationException` only |
| 6 | **`@Valid` missing** on login/register/config/bulk DTOs → constraints never enforced; no size caps on `jdText`/`text`/id lists (LLM cost + DoS) | `AuthController:47-57`, `ApplicationDtos`, `ResumeDtos`, `BulletDtos`, `ProjectDtos`, `GenerationConfigController:25` | Add `@Valid`, `@Size`, `@NotNull`; cap lists (~50) |
| 7 | **TeX injection**: LLM lens string + `portfolioUrl` interpolated unescaped into LaTeX | `BulletLineMeasurer.java:142`, `Bullet.java:50`, `ApplicationRenderer.java:125` | Normalize lens; validate id `[A-Za-z0-9_-]+`; validate http(s) URL, reject `{}\%#` |
| 8 | **Prompt injection**: JD/README text unfenced in most prompts | `BaseLlmClient.java:1061-1726` | Reuse `scoreFit` untrusted-data fence everywhere |
| 9 | **V3 drops `profile.experience`** without copying (`verify` whether already migrated in prod) | `V3__project_kind_and_experience.sql:12` | Confirm prod state; document |
| 10 | **Frontend `javascript:` href** from job feed | `pages/Jobs.tsx:237` | Render link only for http(s) |

## P1 — Correctness bugs

1. **PdfCompiler timeout never fires** — `readAllBytes()` before `waitFor`; 2 hung compiles deadlock all (`render/PdfCompiler.java:73`). Wait first, `destroyForcibly`.
2. **LLM timeout check wrong** — wrapped cause not `TimeoutException`; timeouts retried, ~2x cost (`BaseLlmClient.java:71`, `OpenAiCompatibleLlmClient:119`). Walk cause chain.
3. **Google stream leak + uncancelled futures** (`GoogleLlmClient.java:171-195`). try-with-resources; `cancel(true)`.
4. **Retry on deterministic 4xx** (`BaseLlmClient:76`). Retry only 429/5xx/IO.
5. **Malformed-reply retry dead** — flag always false, parsers throw plain RuntimeException (`BaseLlmClient:266,301,913`).
6. **Temperature unvalidated** vs comment saying only 1.0 is safe (`BaseLlmClient:405+`, `GenerationConfigService:36`).
7. **Exception handler**: bad JSON / UUID path → 500; leaks `getMessage()` (`ApiExceptionHandler.java:101`). Add 400/409 handlers; generic 500 text. Same for job-failure messages stored in progress (`ApplicationController:74…`).
8. **Missing UNIQUE(user_id)** on `profile`, `generation_config` → race creates dup rows → permanent 500 (`V7`, `GenerationConfigService:16`, `ProfileService:30`). New migration + upsert.
9. **Register**: TOCTOU on username/email, case-sensitive email (`AuthController:87`). Catch `DataIntegrityViolationException`, lowercase email.
10. **`JobProgressStore.cleanup()` never called** → memory leak; O(n²) append; possible NPE (`api/JobProgressStore.java:55-65`).
11. **`RegisterRateLimiter` unbounded map + proxy IP** (`:21`). Caffeine + forward-headers.
12. **Bullet refit lost-update**, no `generating` guard, no `@Version` (`BulletService.java:262-357`).
13. **refitSelection drops REJECTED locked bullets** (`ApplicationService.java:750-780`).
14. **rerender no dedupe / O(n²) warnings** (`ApplicationService:596`).
15. **`ProjectService.update` not partial; stale repo cache on URL change; not transactional** (`:121-154`).
16. **`@Async` self-invocation bypassed** → GitHub fetch blocks request thread, no HTTP timeout (`ProjectService:156`, `GithubContextFetcher:25`).
17. **Repo map caches degraded result forever** (`RepoMapService:92`, `GithubService:215`).
18. **ProfileService.readEducation swallows parse error → `[]` → next save wipes data** (`:80`).
19. **RepoExplorer NPE on null step/action** (`:134-168`); **readFile no size cap before read** (`GithubClient:130`).
20. **Eval**: unbounded spawns, dup ids, no MDC, `Error` leaves RUNNING (`EvalService:106-151`).
21. **JobFeedService** swallows all integrity errors as "duplicate" (`:95`); count/LIKE queries unindexed (`:122-180`).
22. **LlmSettings**: unvalidated provider, silent decrypt fallback to env key (`LlmSettingsService:152-255`).
23. **Cost tracking** hardcoded Gemini prices for all providers (`TokenAccumulator:18-71`).

## P2 — Frontend bugs

1. **Partial PUT wipes fields** — `DescBlock`/`MetaBlock` PUT subset (`pages/ProjectDetail.tsx:94`, `MetaBlock.tsx:35`) (`verify` backend null behaviour; ties to P1-15).
2. **`EventStream` `onDone` never fires** when `doneLabel=""` → stale RepoTab/StoryGenerate (`EventStream.tsx:213`).
3. Poll: no failure cap, overlapping intervals (`EventStream:67`).
4. `useApplicationDetail.load` no catch, serial N+1 bullet fetch (`:56-93`).
5. Missing catches / no rollback: `setOutcome`, `toggleLock`, `refit`.
6. PDF effect: no `res.ok` check, blob URL leak on unmount (`:105`).
7. Errors shown as empty states (Projects/Applications/ProjectDetail).
8. Jobs poll stale-token race (`Jobs.tsx:83`).
9. `useGenerationConfig.save` lacks `loaded` guard (`:39`).
10. Login maps all errors to "Invalid credentials"; Register uses `msg.includes('409')`.
11. `useHeroLoop` freezes when tab hidden at boundary.
12. `api.ts`: localhost fallback in prod, `...init` drops default headers, 15x `catch (e: any)`.
13. A11y: nested interactive in `BulletGroupSection`, `EventStream` modal lacks dialog semantics, unlabeled inputs, tabs lack roles.

## P3 — Infra / ops

- Dockerfile: runs as root, tectonic tarball unverified, unpinned bases.
- compose.yml: `cloudflared:latest`; no healthcheck / `no-new-privileges` / `cap_drop`.
- deploy.sh: `down` before build (downtime), unpinned `git pull`, no rollback → build, `pull --ff-only`, `up -d`.
- Missing indexes: `llm_usage_log(user_id,created_at)`, `(application_id)`, `(project_id)`; `saved_job(job_posting_id)`; `bullet(story_id)`; `bullet_measure_diagnostic` FKs + retention. One new migration.
- V42 destructive category collapse; no backup (document only — already applied).
- Tests: tectonic-dependent tests skip silently in CI; no tests for auth/Admin/JdFetcher/obs/progress/GoogleLlmClient; time-dependent flakes (`EvalServiceTest:49`, `JobFeedServiceTest:186`) → inject `Clock`; weak string-match assertions.

## P4 — Cleanup / dedupe

- **Delete**: `SmokeController`, `BcryptGen`, unused sync endpoints (`ApplicationController:95-101,115-120,179-184`), `PdfCompiler.Result.success`, commented method `ApplicationService:1138`, `lastMatch`, one-arg `shorten`.
- **Consolidate async job plumbing** (3 controllers, unbounded virtual-thread executors, no per-user cap) into one bounded helper; dedupe progress endpoint.
- Dedupe: measurers, `parseUuid`, csv split, experience/project partition, shadowed `mapper`, Google exception mapping, per-row saves → `saveAll`, drop unused recruiter justification output (wasted tokens), `markdownBoldToHtml`.
- Default-locale `toLowerCase()` → `Locale.ROOT`.

## P5 — Lab code in prod

- `App.tsx` statically imports ~28 lab pages + CSS + `fakeApi` into main bundle; 29 public `/lab/*` routes. → `React.lazy` or `import.meta.env.DEV` gate. (Note: `lab/onboarding` and `App.tsx` are uncommitted WIP — confirm intent before touching.)
- Delete superseded variants (`landing`, `landing-v2`, `hero`, `how`) after moving `Picker`/`StoryStage` to `lab/shared/`.
- Dedupe 4–5 bullet-row components, duplicate Bullets/Generate tabs, fixtures.
- Leaky global CSS (`.masthead`, `.nav`, `body:has(.proto-picker) .pf-save`).
- Lab-only bugs: interval leak (`ProjectWorkspace:251`), `<a href>` reloads, fakeApi PDF `/Length`, `E` key drops query params.

## Suggested order (revised after verification)

0. **Partial-PUT data wipe** (P2 #1 / P1 #15) — real user data loss today; fix `ProjectService.update` to only set non-null fields, plus test.

## Original order

1. P0 #1–#4 (one PR: SSRF, seed pw, cookie, migration neutralizing seed hash).
2. P1 #1, #2, #8 + P3 index/unique migration.
3. P0 #5–#8, P1 #7.
4. P2 #1–#3.
5. P5 lazy-load gate.
6. P3 infra, P4 cleanup.

Each fix: write failing test first (CLAUDE.md goal-driven rule), keep diffs surgical.
