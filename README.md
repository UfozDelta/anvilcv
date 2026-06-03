# AnvilCV

Full-stack app for managing resume content, generating tailored PDFs, and tracking job applications. Paste a job description, get a ranked bullet selection, and compile a PDF — all in one flow. Supports multiple users, each with isolated data.

## Features

- **Multi-user** — each user has isolated profile, projects, bullets, and applications
- **Bullet management** — create, edit, tag, and filter resume bullets by skill
- **Job application pipeline** — paste JD → LLM cleans and analyzes it → bullets ranked by relevance → PDF compiled
- **Cover letter** — optional co-generation alongside the resume
- **Async progress tracking** — long LLM + PDF jobs run in background; frontend polls for status
- **Bulk import** — upload page with rule-based resume parser for importing existing content
- **LaTeX PDF output** — resume rendered via a Tectonic-compiled LaTeX template

## What Is In This Repo

- `src/main/java/com/resumepipeline` — Spring Boot backend
- `src/main/resources/db/migration` — Flyway database migrations
- `src/main/resources/template/resume.tex` — LaTeX resume template used for PDF output
- `frontend` — React + Vite frontend
- `.legacy` — older resume notes and drafts

## Backend Modules

- `api` — REST controllers and request/response DTOs
- `auth` — registration, login, session auth, CORS, and Spring Security setup
- `profile` — profile/resume identity data (one per user, auto-created on first access)
- `project` — projects and experience entries
- `bullet` — resume bullet storage and editing
- `application` — job applications, JD matching, and PDF rendering flow
- `llm` — Gemini integration for JD cleanup, bullet generation, and ranking
- `render` — LaTeX escaping, template rendering, and PDF compilation
- `jd` — job description parsing and normalization
- `progress` — async job tracking (UUID-keyed job store, polling endpoint)
- `config` — per-user generation config (LLM tuning, word filter, tone)

## Application Pipeline Flow

1. User submits job description + selects projects
2. Backend spawns async job (returns UUID immediately)
3. LLM cleans JD, ranks bullets, optionally drafts cover letter
4. Tectonic compiles LaTeX → PDF
5. Frontend polls `/api/applications/jobs/{id}/progress` until done, then renders result

Typical pipeline takes 1–3 minutes depending on LLM response time.

## Auth and Users

Users register at `/register` with a username, email, and password. All data is scoped to the authenticated user — profiles, projects, bullets, and applications are never shared across accounts.

On a fresh deployment, a seed user is created automatically from config (see `auth.seed.*` below). All data that existed before the multi-user migration is owned by this seed user.

New users can register via the UI. There is no admin role — all accounts are equal.

## Requirements

- Java 21
- Maven
- Node.js 18 or newer
- PostgreSQL database
- `tectonic` for PDF generation (optional — skip for non-PDF flows)
- Gemini API key for LLM features (optional — required for bullet ranking and JD analysis)

## Backend Setup

Create a local config file at:

```text
src/main/resources/application-local.yml
```

Example:

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
    password: yourpassword

llm:
  api-key: your_gemini_api_key

tectonic:
  binary: tectonic
```

`application-local.yml` is git-ignored — safe to put secrets here.

The seed user is created (or updated) automatically on startup. No manual password hashing needed.

## Start The Backend

From the repo root:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run
```

Backend runs at `http://localhost:8080`.

## Start The Frontend

In a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173` and talks to the backend at `http://localhost:8080` by default.

To override the backend URL:

```powershell
$env:VITE_API_BASE="http://localhost:8080"
```

## Docker

Build and run the backend as a container:

```powershell
docker build -t resume-pipeline .
docker run -p 8080:8080 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/resume_pipeline `
  -e SPRING_DATASOURCE_USERNAME=postgres `
  -e SPRING_DATASOURCE_PASSWORD=postgres `
  -e SEED_USERNAME=yourname `
  -e SEED_EMAIL=you@example.com `
  -e SEED_PASSWORD=yourpassword `
  -e GEMINI_API_KEY=your_gemini_api_key `
  resume-pipeline
```

## Useful Commands

Run backend tests:

```powershell
mvn test
```

Build frontend for production:

```powershell
cd frontend
npm run build
```

Build backend jar:

```powershell
mvn package
```
