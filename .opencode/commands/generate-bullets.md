---
description: Generate a story and its wordings here, then save them into AnvilCV's bullet bank
agent: job-agent
---

Generate bullets for: $ARGUMENTS

1. Load `context-extractor` if the source is a repo, otherwise `story-bank` and `anvilcv-sop`.
2. Resolve the target project with `anvil_projects`; read its current bank with `anvil_bullets`.
3. Find 6 platform-changing stories with re-openable evidence (path:line or commit). Skip small
   fixes, tool inventories and activity counts. Drop repeats against the existing bank.
4. For each story, write up to 15 wordings from the outcome/decision/scale/failure angles,
   then judge them with the recruiter checklist and keep the best one or two.
5. Run `anvil_check_bullets` on every kept wording and fix what it flags.
6. `anvil_save_story` for each story, then `anvil_save_bullets` for its wordings.
7. Report the saved story/bullet ids and how many were dropped by the checks.
