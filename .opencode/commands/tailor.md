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
4. Pre-filter bullets by keyword, rank them, and select with a FIXED SHAPE: 2 experience entries
   x 3 bullets + 3 project entries x 3 bullets = 15, exactly 3 per entry (never a 1-bullet entry).
   One wording per story, no repeated opening verbs, three angles (WHAT → MECHANISM → OUTCOME).
   Only widen to a 4th project (18 bullets) if the rendered PDF still has room — check pageCount
   after each rerender and never ship a 2-page PDF.
5. Run `anvil_check_bullets` and `anvil_check_coverage` on JD vs rendered text. Fix gaps and
   hard-gate misses you can honestly fix; report the ones you cannot. Bullet TEXT must stay in
   the 96-104 / 170-229 bands — do not "fix" a band by padding words, since the calibrated
   template was tuned for the exact one-page resume texts.
5b. MUST run `anvil_check_fit` on the candidate selection (bullet ids + the skills/courses plan)
   BEFORE saving. It compiles the real LaTeX and returns `fitsOnePage`, `pageCount`, `overfullHbox`
   and per-bullet `measuredLines`. If `fitsOnePage` is false or any bullet measures >2 lines, drop a
   whole lower-priority entry (or swap in a shorter bullet) and re-check — never ship a 2-page PDF
   and never stretch the surviving bullets to fix a band.
7. Print only a JD-relevant SKILL SUBSET: 7-8 items per row (all five rows honor `selectedSkills`,
   including AI & Integrations). Keep the full set in the profile, but long rows trigger the
   `\skillrow` auto-shrink and make the bottom of the page unreadable. Use the 7-course education
   line for the same reason.
8. Write the cover letter from the selected bullet evidence only.
9. `anvil_save_application` with the ordered bullet ids, keywords, skills, courses and letter.
   AnvilCV renders the LaTeX, compiles the PDF, scores ATS and records quota. Verify the returned
   `pageCount` is 1 (the pre-check in 5b should already guarantee this); if it is 2, drop a
   lower-priority entry and rerender before reporting done.
10. Report the application id, the PDF path (via `anvil_download_pdf` if asked), ATS matches,
    gaps and hard-gate misses.
