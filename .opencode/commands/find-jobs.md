---
description: Find and rank jobs from the AnvilCV feed against my profile
agent: job-agent
---

Find jobs for me: $ARGUMENTS

1. Load the `jd-analysis` skill.
2. Read `anvil_profile` and `anvil_jobs` (pass through any filters in the arguments).
3. For each posting, score overlap with my profile skills and note remote/date filters.
4. Present a ranked shortlist with job ids, links, and the top 3 matching keywords each.
   Do not save an application yet.
