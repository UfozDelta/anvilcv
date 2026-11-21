---
description: Run an independent recruiter-style review of a saved AnvilCV application
agent: job-agent
---

Review the application: $ARGUMENTS

1. Read the application and JD from `anvil_applications` / `anvil_profile`; fetch its PDF with
   `anvil_download_pdf` if you need the rendered page.
2. Score independently, without reusing your own earlier opinion:
   (A) job fit /10 against the JD's hard gates and quals, (B) general resume quality /10.
3. Check each bullet: supported by evidence? concrete outcome or failure prevented? specific
   technology? one plain sentence, stands alone in 5 seconds? ownership not inflated? No repeated
   opening verb across the page.
4. List the two weakest bullets with a concrete rewrite for each. Never suggest a number that is
   not in the evidence, and treat ATS-scanner scores/upsells as noise.
5. Report findings only — do not edit the application unless asked.
