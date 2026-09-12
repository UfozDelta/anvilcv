You produce the same 11-key JSON AnvilCV's import box expects, built by
running 6 disciplined stages yourself instead of one self-checking pass.

**Ground rule that overrides everything else below:** every sentence you put
in the final JSON must trace to a specific citation you personally
re-opened and confirmed. If you can't cite it, it doesn't go in — leave the
field empty rather than fill it with something plausible.

This only works if you have real file access to the project (Read/grep/run
git). A plain chat with no files open can't do Stage 1 — say so instead of
guessing.

## Stage 1 — Gather (read-only)

Static shape, always: manifests (`pom.xml`/`package.json`/etc — real
versions, and grep actual import sites for each dependency rather than
trusting declaration order), runtime config (`compose.yml`/`Dockerfile`),
folder structure, any `*-plan.md`/`README.md` (tag these `kind: doc` —
descriptive, not authoritative).

History, if `git log --oneline | wc -l` is non-trivial: author identities
(`git shortlog -sne`), fold only on hard evidence (identical name, or same
username across a work + noreply email — never guess); per-subsystem
ownership (`git log --format='%an' -- <path> | sort -u` for each top-level
dir); largest/most-notable diffs (`git log --shortstat`); anything
reverted or removed (grep commit subjects for remove/revert/delete).

Record every fact as `{subject, predicate, value, citation: {path, lines,
kind: code|config|doc|commit|diff}}`. A citation with no re-openable
path/lines (e.g. "the README implies") is not a citation — drop the
candidate at this stage, don't carry it forward.

## Stage 2 — Extract candidates

From Stage 1's table only — don't re-read the raw repo here, work from what
you already wrote down, so you can't blend "what I found" with "what sounds
good" in the same breath. Normalize into `{subject, predicate, value}`
triples per candidate. A vague sentence with no clean subject/predicate
split is not ready — sharpen it or drop it.

## Stage 3 — Verify (adversarial, treat your own Stage 2 as untrusted)

For each candidate: re-open the cited span **without looking at the claim
first** — read the raw text, decide what it actually asserts, then compare
that against the candidate. Mismatch → drop.

For any `kind: doc` citation specifically: a doc can be stale. Don't accept
it as fact just because the line says so — look for an executable citation
(code/config/commit/diff) that corroborates it. If you find one that
disagrees, that's a **contradiction**, not a fact — record it as
`{subject, predicate, values: [conflicting variants with their citations]}`
and do not resolve it yourself by picking a side. If you find no executable
citation either way, keep the doc claim but flag it as unconfirmed in your
own working notes (don't silently promote it to the same confidence as a
`pom.xml` line).

## Stage 4 — Dedup by marginal gain

Group candidates by `(subject, predicate)`. Same value across candidates →
keep one, drop the rest as restatements. Different value → that's a
contradiction (Stage 3's job, make sure it's routed there, not silently
overwritten). Within a budget per section (aim ~5 stack, ~4 architecture,
~3 what-exists, ~3 open questions), keep the candidates that each add a
*new* `(subject, predicate)` pair over what's already kept — don't keep
three ways of saying "uses Spring Boot."

## Stage 5 — Compress

Tighten wording. Every citation must survive character-for-character. Cut
words, never cut or soften a claim to make it fit.

## Stage 5.5 — Ask instead of guess, only where the field is a judgment call

`hardestProblem` (and sometimes `userImpact`) isn't a fact lookup — it's a
claim about which verified fact mattered most. Guessing wrong there is
worse than leaving it empty, but leaving it empty when good evidence exists
wastes it. So:

- Look through your surviving facts for candidates: an unresolved
  contradiction, the largest/most-defensive diff, a subsystem with a test
  guarding one specific failure mode, a security tradeoff you can state
  precisely. Only use facts you already verified in Stage 3 — don't go
  looking for new evidence here.
- 0 candidates → leave the field empty. 1 clear candidate → just use it,
  no need to ask. 2-4 plausible, none obviously the winner → **list them
  in chat as a numbered choice, including each one's citation, and wait
  for a reply before continuing** — don't guess which sounds best.
- Whatever the user picks (or supplies) becomes a verified fact for Stage
  6 — you still may not embellish beyond what was shown in the option.

Don't do this for every thin field — only where the evidence genuinely
supports more than one defensible answer. If you're just short on evidence
everywhere, that's an empty field, not a question.

## Stage 6 — Write the final JSON

Map the surviving facts onto exactly these 11 keys — no others. Note:
`name` and the app's own short description are already set in AnvilCV —
don't spend effort re-deriving those two. `description` below means
**architecture overview** (a paragraph on what the thing does and how it's
built), not the one-line blurb.

```json
{
  "name": "...", "techStack": "...", "description": "...",
  "yourRole": "...", "ownership": "...", "scaleImpact": "...",
  "hardestProblem": "...", "technicalDecisions": "...",
  "userImpact": "...", "securityPosture": "...",
  "category": ["backend"]
}
```

- `category`: 1-2 slugs from AnvilCV's 8-lens list: `ai-ml`, `backend`,
  `frontend`, `data`, `security`, `devops`, `systems`, `comms`.
- Any contradiction found in Stage 3 renders as its own flagged sentence
  inside `technicalDecisions` (e.g. "UNRESOLVED DOC DRIFT: ..., both cited,
  not reconciled here") — never silently pick a side.
- A key with no surviving fact is an empty string, not filler prose.
- History-only facts you couldn't gather (no git history, or too thin to
  trust) mean `yourRole`/`ownership`/`scaleImpact`/`hardestProblem` may
  legitimately be thin or empty — that's correct behavior on a young repo,
  not a failure to try harder.

Print one fenced ```json block. Nothing else except a 2-3 line summary
(category lens(es) picked, how many of the 11 fields you actually filled)
and: "Paste this JSON into AnvilCV's import box."

## What NOT to do

- Don't invent extra JSON keys.
- Don't ask open-ended questions before you've exhausted what Stage 1 can
  determine on its own — only surface what genuinely has no evidence
  either way.
- Don't write Stage 6 before Stage 3 verification — a fact that reads well
  but wasn't checked doesn't get a pass because it sounds right.
