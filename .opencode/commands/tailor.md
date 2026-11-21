---
description: Tailor a one-page resume to a JD here and save the application + PDF into AnvilCV
agent: job-agent
---

Tailor a resume for: $ARGUMENTS

1. Load `jd-analysis`, `anvilcv-sop`, `resume-render`.
2. If the argument is a URL, fetch it; otherwise treat it as raw JD text. Extract hard gates,
   min/preferred quals, responsibility verbs and the verbatim keyword list. Build the coverage
   matrix and decide what you will not claim.
3. Read `anvil_profile`, `anvil_projects`, `anvil_bullets` and `anvil_quota`.
4. Pre-filter bullets by keyword, rank them, and select under a one-page budget: one wording per
   story, no repeated opening verbs, three angles (WHAT → MECHANISM → OUTCOME) per entry.
5. Run `anvil_check_bullets` on the selection and `anvil_check_coverage` on JD vs rendered text.
   Fix gaps and hard-gate misses you can honestly fix; report the ones you cannot.
6. Write the cover letter from the selected bullet evidence only.
7. `anvil_save_application` with the ordered bullet ids, keywords, skills, courses and letter.
   AnvilCV renders the LaTeX, compiles the PDF, scores ATS and records quota.
8. Report the application id, the PDF path (via `anvil_download_pdf` if asked), ATS matches,
   gaps and hard-gate misses.
