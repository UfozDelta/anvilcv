---
name: AnvilCV Bullet SOP
description: Calibrated XYZ bullet rules, character bands, big-idea and three-angle rules, bold placement, verb variety, recruiter judge checklist. Use whenever writing or judging resume bullets.
---

## software-writingRules

─────────────────────────────────────────────────────────────
## 1. LENGTH — line-filling discipline (CRITICAL)

Each bullet must compile to EITHER exactly 1 full line OR exactly 2 full lines on the
rendered resume. NEVER produce a bullet that overflows by a few words into a sparse
second line — that looks broken.

Length is measured in CHARACTERS including spaces, ignoring the ** bold markers
(they compile to \textbf{} and take no width). Word counts are approximate guides;
the character range is what actually decides whether a line fills.

Targets:
  • 1-line bullet: 96 to 104 characters (roughly 13 to 14 words).
  • 2-line bullet: 170 to 229 characters (roughly 23 to 31 words).
  • NEVER produce a bullet of 111-163 characters — that range half-fills line 2.

Aim for about 70% 1-line and 30% 2-line bullets. Recruiters skim a page in seconds
and dense multi-line bullets get skipped, so use the 2-line form only when the
substance genuinely needs it — every 2-liner spends double the vertical space of a
1-liner that lands just as hard.

## 2. FORMAT — Google XYZ pattern

Every bullet reads as "Accomplished X, as measured by Y, by doing Z":
  [STRONG ACTION VERB] + [WHAT was built, X] + [HOW, Z] + [MEASURE, Y].
Y is a result someone outside the codebase would notice — latency, cost, error or
failure rate, users served, data volume handled, time saved — and only when the source
material states it. When it does not, write X + Z and name the problem solved or the
failure the design prevents, then stop. A tight bullet without Y beats a padded one.
Lead with the big idea: the headline capability or the change it made for users,
then the mechanism. Prefer a stated outcome or a failure prevented over a list of
code changes. Do not write small bug fixes ("Fixed duplicate call-log rows"), tool
inventories in parentheses ("4 tools (knowledge, listing, ...)"), feature lists with
no problem or outcome ("a month grid with service badges and a seven-day view"), or
activity counts as results ("11 validation checks", "19-field model"). If the work is
only a plan, use design verbs (Designed, Specified, Planned) and never invent a
benchmark number.
Counts of your own work are activity, not results, and never belong in a bullet:
commits, lines of code, files, tests, tables, migrations, "zero broken builds".

ONE sentence per bullet. Never add a second sentence that restates the first
("This ensured...", "This enabled...") and never trail off with filler like
", ensuring robust and reliable X".

Open each bullet with a strong, specific action verb, e.g. Built · Designed ·
Shipped · Implemented · Migrated · Automated · Integrated · Reduced · Wrote.
Give every bullet in this batch a different opening verb — and do not reuse the
opener of an ALREADY COVERED bullet when another fits. Check all opening verbs
before finishing the batch: no verb may open more than two bullets, and a distinct
opener per bullet is better. Fix repetition by swapping openers, not the substance.
Claim leadership or ownership (Led · Owned · Architected · Directed · Mentored)
only when the source material says so. Never upgrade a contribution into leadership.

Forbidden openers: "Worked on", "Helped with", "Was responsible for", "Assisted",
"Contributed to", "Collaborated on" — these are passive and weak. Also avoid the
clichés "Spearheaded", "Leveraged", "Utilized", "Orchestrated", "Honed" and
"Streamlined", and puffed adjectives ("robust", "comprehensive", "cutting-edge").

EVERY bullet ends with a period.

## 3. BOLD — **double asterisks** (compiles to \textbf{})

Use AT MOST 1 bold in a 1-line bullet and 2 in a 2-line bullet — bold is emphasis,
and a bullet that bolds everything emphasizes nothing. Reserve it for what a
recruiter's eye should land on first, in priority order:
  (a) the headline result or scale figure the source states;
  (b) a marquee technology, protocol or vendor the bullet names;
  (c) the name of the system or technique you designed.
Do NOT bold: weak verbs, plain English nouns, generic adjectives, the action verb itself.

## 4. CONTENT RULES

  • Quote anchor numbers from the source VERBATIM. NEVER fabricate metrics.
    If the source doesn't have a number, omit it — don't invent one.
  • NO internal identifiers (table names, function names, file paths, env-var names).
    Those belong in interview answers, not on a resume.
  • Each bullet stands alone — a recruiter must understand it in 5 seconds without
    reading neighbors.
  • Keep compact mechanism adjectives that name a real design decision —
    "self-cancelling", "fail-closed", "hash-locked". They compress a whole
    failure-prevention design into one word: substance, not buzzwords.
  • Tag each bullet with 2 to 5 specific technologies/tools/frameworks/protocols
    explicitly named in the bullet text itself (e.g. "react", "kubernetes",
    "postgresql", "grpc"). Lowercase, no fixed list — pull from what you wrote.
    Do not invent tags for things not mentioned in the bullet.

─────────────────────────────────────────────────────────────
## EXAMPLES — shape only. Never copy their wording, domain or numbers.

  ✓ [Verb] [what was built, in the source's own words] with [technology the source names],
    [how it works or the key decision], [result the source states].
  ✓ [Verb] [system] so [who] could [do what], [the failure or cost it removed].

  ✗ Maintained zero broken main builds across 120 commits.
    (activity counts, not a result)
  ✗ Built a backend test suite of 190 unit tests. This enabled rapid development.
    (artifact count, plus a filler second sentence)
  ✗ Worked on backend stuff using various tools and got things faster.
    (passive opener, vague, no result)

