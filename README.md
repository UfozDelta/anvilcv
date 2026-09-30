# AnvilCV

Turn your code into resume bullets, then tailor a one-page PDF to each job.

![How AnvilCV works](docs/anvilcv-overview.svg)

## What it does

- **Reads your repos.** Connect GitHub (read-only, private OK) and import a repo. AnvilCV maps
  the code: it ranks the modules, counts facts like tests and endpoints, and summarizes it from
  modules up to the whole project.
- **Writes grounded bullets.** Eight lenses (AI/ML, backend, frontend, data, security, DevOps,
  systems, comms) each write from their own part of the code. Claims without code behind them
  are cut, and each bullet links to its source.
- **Keeps you in control.** Approve or reject bullets, pin or exclude files, add notes, and pick
  which parts of the project to write about.
- **Tailors per job.** Paste a job description or URL. AnvilCV ranks your bullets and builds a
  one-page LaTeX PDF (≤15 bullets) plus a cover letter in 1-3 minutes.
- **Tracks outcomes.** Application history as a sankey chart, with LLM cost per run.
- **Bullet style:** Google XYZ format, mostly one line, one bold, and numbers only when real.

More: [repo explorer flow](docs/github-repo-explorer.html) · [adding a project](NEW_PROJECT.md) ·
[full reference](docs/REFERENCE.md) (config, API, data model, gotchas).

## Stack

Java 21 + Spring Boot 3.4 · PostgreSQL + Flyway · React 18 + Vite · Gemini / OpenAI-compatible
LLMs · Tectonic (LaTeX → PDF).

## Quick start

Needs Java 21, Maven, Node 18+, PostgreSQL (with `pgcrypto`), `tectonic`, and an LLM key.

1. Create `src/main/resources/application-local.yml` (git-ignored):

   ```yaml
   spring.datasource: { url: jdbc:postgresql://localhost:5432/resume_pipeline, username: postgres, password: postgres }
   auth.seed: { username: you, email: you@example.com, password: change-me }
   llm.gemini.api-key: your_key
   ```

2. Run it:

   ```bash
   ./start.ps1                                                   # Windows
   (cd frontend && npm i && npm run build) && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
   ```

3. Open http://localhost:8080 and log in as the seed user.

## Connect GitHub (optional)

1. Create a GitHub App:
   - Callback URL: `https://<host>/api/github/callback`
   - Tick *Request user authorization during installation*
   - Webhook off
   - Permissions: *Contents* and *Metadata* read-only
2. Set `GITHUB_APP_ID`, `GITHUB_APP_SLUG`, `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET`,
   and `GITHUB_APP_PRIVATE_KEY`.
3. In the app: **Settings › Connect GitHub**, then **Projects › Import from GitHub**.

Leave the vars unset and the feature stays off.

## Test & deploy

```bash
mvn test && (cd frontend && npm test)       # no database needed
docker compose up -d --build                # app + Cloudflare Tunnel, no open ports
```

All env vars are in [`.env.example`](.env.example).
