---
name: AnvilCV API — full feature surface
description: Every AnvilCV REST endpoint and request shape, for 1:1 control of the app from the agent. Use when a dedicated MCP tool does not cover the action (profile, config, projects, CRUD, jobs, billing, onboarding, admin, GitHub, field, resume import).
---

Call anything with `anvil_api({ method, path, query?, body? })`. The session cookie is handled
for you. Prefer the dedicated `anvil_*` tools when one exists; use this for everything else.
All paths are relative to `ANVIL_BASE_URL` (default `http://localhost:8080`).

## Auth / system
- `GET /api/me`, `GET /api/ping`, `GET /api/public/stats`
- `POST /api/login` `{username, password}`, `POST /api/logout`

## Profile, config, field
- `GET|PUT /api/profile` — profile object: `name, phone, email, linkedinHandle, githubHandle,
  portfolioUrl, education (JSONB), skillsLanguages, skillsFrameworks, skillsDatabases,
  skillsDevops, skillsInterests, field`
- `GET|PUT /api/config/generation` — `wordFilterEnabled, temperature, singleLineLow/High,
  deadZoneLow/High, doubleLineLow/High, boldDensity, tone, verbStyle`
- `GET|PUT /api/field` — `field` slug (`software` / `mechanical`); lenses and skill labels follow it
- `GET /api/quota` — current plan and meter

## Projects & experiences
- `GET /api/projects`, `GET /api/projects/{id}`, `GET /api/projects/{id}/stories`
- `POST /api/projects` — `{kind: "PROJECT"|"EXPERIENCE", name, description, githubUrl?,
  title?, company?, location?, dates?, current?}`
- `PUT /api/projects/{id}` — any of `{name, description, contextDescription, githubUrl,
  techStack, yourRole, ownership, scaleImpact, hardestProblem, technicalDecisions,
  userImpact, securityPosture, title, company, location, dates, current}`
- `DELETE /api/projects/{id}`, `POST /api/projects/{id}/duplicate`
- `POST /api/projects/{id}/bullets/generate`, `/bullets/refit`
- `POST /api/projects/{id}/stories/submit`,
  `POST /api/projects/{id}/stories/{storyId}/wordings/submit`
- `GET /api/projects/jobs/{jobId}/progress` — `{lines[], status, appId, error}`

## Bullets
- `GET /api/projects/{projectId}/bullets`, `POST /api/projects/{projectId}/bullets`
  — `{text, tags[], category}` (category is the lens)
- `PUT /api/bullets/{id}` — `{text?, tags?}`
- `PATCH /api/bullets/{id}/status` — `{status: "PENDING"|"APPROVED"|"REJECTED"}`
- `DELETE /api/bullets/{id}` — soft delete (REJECTED)
- `POST /api/bullets/preview` — `{bulletIds[]}` → PDF bytes (use `anvil_download_pdf` for files)

## Applications / tailoring
- `GET /api/applications`, `GET /api/applications/{id}?includePdf=true`,
  `GET /api/applications/outcome-history`
- `POST /api/applications` — `{jdText, jdUrl, roleEmphasis, includeCoverLetter}` (runs AnvilCV's LLM)
- `POST /api/applications/submit` — same body, returns `{jobId}`; poll
  `GET /api/applications/jobs/{jobId}/progress`
- `PATCH /api/applications/{id}` — `{outcome}`
- `DELETE /api/applications/{id}`
- `POST /api/applications/{id}/rerender` — `{selectedBulletIds[]}`
- `POST /api/applications/{id}/rerender/submit` — same, async
- `POST /api/applications/{id}/refit-selection[{/submit}]` — `{projectId?}`
- `POST /api/applications/{id}/rescore/submit`
- `PATCH /api/applications/{id}/locks` — `{lockedBulletIds[]}`
- `POST /api/applications/selection-check` — `{selectedBulletIds[]}` (read-only warnings)
- `GET /api/applications/{id}/pdf|/tex|/cover-letter`
- `POST /api/agent/applications` — **agent-authored** application (renders + compiles + persists)

## Jobs
- `GET /api/public/jobs`, `GET /api/public/jobs/tags` — filters: `q, location, remote, addedWithin,
  stack, mine`, limit 20 tags / 100 chars
- `PUT|DELETE /api/jobs/{id}/save`
- `POST /api/public/jobs/webhook` — HMAC-signed; not for the agent

## Resume import / onboarding
- `POST /api/resume/parse` — `{text}` → `{experiences[], projects[]}`, each with `bullets[]`
- `POST /api/resume/import` — `{items: [{kind, name, description, title?, company?, location?,
  dates?, bullets?}]}` (bullets max 30/item, 600 chars each; saved APPROVED)
- `GET /api/onboarding`, `POST /api/onboarding/complete`, `POST /api/onboarding/downloaded`

## Billing
- `POST /api/billing/checkout` — `{plan: "starter"|"pro"|"pack"}` → `{url}`
- `POST /api/billing/portal` → `{url}` (409 until a Stripe customer exists)
- Webhook is public/signature-gated; do not call it.

## GitHub
- `GET /api/github/status|/connect|/callback|/repos`, `DELETE /api/github`,
  `POST /api/github/link`
- `GET /api/github/projects/{id}/tree|file|map|bullet-sources`
- `POST /api/github/projects/{id}/explore/submit?rebuildMap=true` → poll project progress

## Admin (requires admin session)
- `GET /api/admin/stats|/logs|/bullet-measure-diagnostics`
- `GET|PUT /api/admin/llm`, `POST /api/admin/llm/test`
- `GET /api/admin/eval/sets|/compare`, `POST /api/admin/eval/snapshot|/generate`,
  `DELETE /api/admin/eval/sets/{id}`

## Agent bridge (dedicated tools exist)
- `POST /api/agent/checks/bullets`, `POST /api/agent/checks/coverage`
- `POST /api/agent/stories`, `POST /api/agent/stories/{storyId}/bullets`
- `POST /api/agent/applications`

## Response/error conventions
Errors are `{source, status, message}`: 400 validation, 402 quota, 404 request, 409 state,
503 GitHub/billing. `401` means the cookie expired — the MCP server re-logs in automatically.
