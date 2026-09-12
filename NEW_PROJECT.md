# Using the AnvilCV context agent on a new project

Replaces the old "download content_extract.md and paste it into a chat"
flow. This is an actual Claude Code agent — it has real tool access
(reading files, grepping, running git) instead of depending on whatever a
chat window happens to expose.

1. `GET /api/tools/context-agent` on your AnvilCV instance, or grab
   `src/main/resources/anvilcv-context-agent.md` directly from this repo.
2. Save it into your own project's `.claude/agents/anvilcv-context.md`.
3. In Claude Code, on that project, ask it to run the `anvilcv-context`
   agent. It explores the repo, verifies its own citations, and — if a
   field like "hardest problem" is genuinely ambiguous — asks you to pick
   between a few grounded candidates instead of guessing.
4. It prints one fenced JSON block. Paste that into AnvilCV's import box.

Every sentence in the output traces to something the agent actually
re-read in your repo. A field it couldn't verify comes back empty rather
than filled with something plausible-sounding — that's expected behavior,
not a bug to work around.
