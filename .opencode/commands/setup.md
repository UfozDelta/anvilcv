---
description: Set up and verify the AnvilCV agent bridge, then show everything that is controllable
agent: job-agent
---

Set up the AnvilCV agent bridge and verify it end to end.

1. Run the bridge check:

!`node mcp/setup.mjs`

2. If any check failed, fix it before continuing:
   - Missing MCP dependencies → `cd mcp && npm install`
   - Missing credentials → tell the user to set `ANVIL_USERNAME` and `ANVIL_PASSWORD` in the
     environment **before starting OpenCode** (they are substituted into `.opencode/opencode.json`),
     then run `/reload`.
   - AnvilCV unreachable → start it with `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`
     (needs the DB, `application-local.yml`, and tectonic for PDFs).
   Re-run the check after each fix.

3. Load the `anvilcv-api` skill and call `anvil_status` and `anvil_projects` to confirm live data.

4. Show the user, briefly, what they can now do from here with 1:1 parity to the app:
   - **View** profile, config, projects, stories, bullets, jobs, applications, quota (`anvil_*` read tools).
   - **Edit** any field via `anvil_api` — profile, generation bands, project context, bullet text
     and status, application outcome/locks/selection, job saves, field, onboarding, admin LLM
     settings, GitHub connection.
   - **Generate** stories and bullets here, run AnvilCV's checks, and save them into AnvilCV.
   - **Tailor** here: AnvilCV renders the LaTeX, compiles the PDF, scores ATS/coverage, applies quota.
   - **Commands**: `/find-jobs`, `/generate-bullets`, `/tailor`, `/review`.

5. Ask what they want to do first. Do not change anything without their go-ahead.
