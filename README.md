# AnvilCV

Turn your code into resume bullets, then tailor a one-page PDF to each job.

![How AnvilCV works](docs/anvilcv-overview.svg)

## What it does

- **Reads your repos.** Connect GitHub (read-only, private OK) and import a repo. AnvilCV maps
  the code: it ranks the modules, counts facts like tests and endpoints, and summarizes it from
  modules up to the whole project.
- **Writes grounded bullets.** Two LLM calls per project: the first picks its strongest
  stories, each backed by quotes checked against the source; each new story then gets wordings
  for its best lens, chosen from 15 candidates by code filters, a score and a recruiter judge
  (lenses: AI/ML, backend, data, general). Code filters cut vanity counts, filler, invented numbers, and bad lengths. Approved bullets are never touched. Stories are
  saved, so a rerun sees what the bank already covers and drops repeats; a project holds up to
  12 live stories.
- **Keeps you in control.** Approve or reject bullets, pin or exclude files, add notes, and pick
  which parts of the project to write about. View the bank by story, with a warning when a
  project has fewer than 3 usable stories.
- **Tailors per job.** Paste a job description or URL. AnvilCV pulls keywords and a role lens,
  pre-filters the bank by keyword, has the LLM rank the rest, then picks bullets in code (one page,
  no two wordings of one story, no one-bullet entries) and builds a LaTeX PDF plus a cover letter in 1-3 minutes. Skills
  and tech lines lead with the job's keywords (skills come only from your profile), and an ATS check counts what is on the page.
- **Lets you edit the result.** Add any bank bullet, reorder, see duplicate and awkward-wrap
  warnings, and a flag when the PDF is out of date.
- **Finds jobs.** A job feed filtered by remote, date added, tech stack, or overlap with your skills.
- **Tracks outcomes.** Application history as a sankey chart, with LLM cost per run.
- **Bullet style:** Google XYZ format, mostly one line, one bold, and a measured result only
  when the source states one.

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
   - Callback URL: `https://<API host>/api/github/callback`. Use the backend's domain, not the
     website's, if they differ.
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
