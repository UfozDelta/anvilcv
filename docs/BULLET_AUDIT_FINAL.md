# Resume Bullet Bank Audit — Final Re-Score

**Corpus:** 80 bullets across 10 projects (6 PROJECT, 4 EXPERIENCE), after dedupe/rewrite.
**Source:** `bullets.json` (80) re-scored against `BULLET_AUDIT.md` (206) and the AnvilCV SOP rubric.
**Method:** char counts measured after stripping `**`; bold spans, openers, sentence count, terminal
periods, filler words, and length bands computed mechanically; A/B/C assigned by rubric judgment.

---

## 1. Overall verdict + corpus stats

**Verdict: Accept as a publishable bank after two micro-fixes.** The rewrite removed essentially all
systemic failures found in the original audit. Every bullet now sits in a legal length band, is a
single sentence with a terminal period, respects the bold-span cap, and carries no internal
identifier, activity count, or invented/conflicting number. **71 A / 9 B / 0 C.** Two bullets have
hard violations (banned filler; bold on the opening verb) and need a one-line edit; the nine B
bullets are usable but carry soft or unsupported outcomes.

### Corpus stats

| Metric | Count | Note |
|---|---|---|
| Total bullets | 80 | 10 projects |
| **A / B / C** | **71 / 9 / 0** | 89% / 11% / 0% |
| One-line band (96–104) | 13 | 16% |
| Two-line band (170–229) | 67 | 84% |
| DEAD ZONE (111–163) | **0** | eliminated |
| Too short (<96) / too long (>229) | **0 / 0** | eliminated |
| Gaps 105–110 / 164–169 | **0 / 0** | eliminated |
| Length-band compliance | **80/80 (100%)** | was 95/206 (46%) |
| Single sentence + terminal period | **80/80** | was 204/206 |
| Embedded `\n` | **0** | was 3 (Foundify) |
| Bold-span violations (>1 one-line / >2 two-line) | **0** | was 62 |
| Improper bold *target* | **2 hard, 9 advisory** | see below |
| Banned filler (ensuring/enabling/robust/comprehensive/leveraging/streamlined) | **1 bullet** | `804e47d6` |
| Internal identifiers / activity counts in text | **0** | was ≈75 |
| Openers above cap (>2 uses) | **0** | was 7 verbs at 16–35 uses |
| Weak openers | **0** | none |
| Number inconsistencies | **0 hard, 2 minor notes** | original 3 conflicts resolved |

**Count per project:** Portal 6 · pestwise 12 · Engineering Requirement V&V 14 · mls-realestate 11 ·
Uoft Prerequisite Explorer 5 · ArbRight 5 · Aerospace and Defense Index 6 · Foundify 3 ·
Hylo 8 · AnvilCv 10.

**Bold detail (span counts are all legal):** 13 one-line bullets each use 1 span. Of the 67 two-line
bullets, 46 use 1 span and 21 use 2 spans — none exceed 2. Two *target* problems remain:
- `eacd2edb` bolds the **opening verb** "Established" (not a result/scale/tech/technique).
- `804e47d6` bolds "**ensuring uninterrupted service**" — bold wrapping a banned filler phrase.

Nine further bullets bold a generic noun rather than a result/scale/marquee tech/named technique
(advisory, not a cap violation): `6623b36f`, `74031e2d`, `1e537451`, `ba588b52`, `cd9ee086`,
`76dd7e7f`, `dd5c1b6e`, `d4ac7430`, `b658c778`.

**Repeated openers:** cap is satisfied — no verb exceeds 2 uses. Nineteen verbs sit *exactly* at the
cap of 2 with no headroom: produced, constructed, introduced, enforced, established, wrote, drove,
shipped, built, architected, engineered, hardened, implemented, designed, unified, authored,
integrated, maintained, modeled. Compared with the original (Engineered 35, Implemented 32,
Designed 26, Architected 21), opener diversity is dramatically better but now has zero slack.

**Internal identifiers / activity counts:** none remain in any bullet *text*. `mymls`/`mycrm`,
`ContextualizerBackend`, `validateClientData`, `JSESSIONID`, `pgcrypto`, LOC/commits/tests/migrations,
`SHA-256[:12]`, `PerimeterX px_token`, etc. were all removed. Note: these names still appear in
`tags` metadata (e.g. `contextualizerbackend`, `voyage-ai`, `mymls`, `qwen3.5:9b`), which is not
rendered — harmless for the resume but worth cleaning if tags ever surface.

**Number consistency:** the original three conflicts are resolved. ArbRight is now uniformly
**3 sportsbooks** (the "6" bullets were dropped); Aerospace now carries only **10-month** and
**Sharpe 2.976** (the "2-year"/"Sharpe 2" bullets were dropped); **2,121 statements / 8 documents**
and **2,450 trees** are consistent everywhere they appear. Two minor notes remain (see §4):
`b353ba91` says "10-15 ticker universe" while `078c7d05` says "15-ticker universe" (compatible but
worth aligning), and `f40c03d3` spells out "sixteen-millisecond" where other bullets use numeric ms.

---

## 2. Comparison to the original audit

**What improved (most of it):**

| Dimension | Original (206) | Final (80) |
|---|---|---|
| A / B / C | 12 / 73 / 121 | **71 / 9 / 0** |
| Length compliance | 46% (111 bad) | **100%** |
| Bold-span violations | 62 | **0** |
| Banned filler / second restating sentences | ≈30 bullets | **1 filler word** |
| Internal IDs + activity counts | ≈75 bullets | **0** |
| Worst opener (Engineered) | 35 uses | **2 uses** |
| Number conflicts | 3 unresolved | **0 (2 wording notes)** |

- **Deduplication worked:** 206 → 80 bullets; the ≈51 near-duplicate clusters are gone. Every kept
  bullet stands alone and is understandable in 5 seconds.
- **Worst projects were rebuilt:** ArbRight went 0A/3B/20C → 5A; Aerospace 0A/0B/15C → 6A;
  Hylo 0A/4B/15C → 8A; AnvilCv 0A/9B/21C → 10A. The 2–7-sentence, 300–400-char monsters are gone.
- **Metrics survived and are now attached cleanly:** 913x, 46.44% / Sharpe 2.976 / -10.74%,
  2,121 / 8 / 2,450 / 8,484, 1,261 / 2.76 s, 500+ — all preserved with valid bold targets.
- **Formatting is uniform:** one sentence, terminal period, no newlines, legal bands, caps respected.

**What still needs work:**

- Two hard violations: banned **"ensuring"** in `804e47d6` and **bold on the opener** in `eacd2edb`.
- Nine B bullets whose outcomes are vague, generic, or unsupported ("one consistent structure",
  "enhancing semantic search", "flexible annotations", "dependable reporting", "attribution gaps",
  "no stated outcome"). These are not broken, but a recruiter would call them soft.
- Nine advisory bold targets on generic nouns rather than results/tech/technique.
- Two minor number-wording alignments (10-15 vs 15 tickers; sixteen-millisecond vs numeric ms).
- Several bullets still read as capability/inventory statements (`2ae4526b` lists tokenizer steps,
  `1f995492` lists metrics, `160afca8` lists resources) rather than leading with an outcome — legal,
  but not the strongest XYZ form.

---

## 3. Per-project tables

Rating key: **A** = fully rubric-compliant with concrete result/failure prevented. **B** = compliant
format, but soft, vague, unsupported, or mis-bolded. **C** = disqualifying issue (none remain).
Reasons are under 12 words each.

### Portal — 6 bullets (5 A / 1 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 6843ddca | A | 102 | Named technique, clobbering prevented; tight one-liner. |
| 2a0bf28a | A | 173 | Clean XYZ; leakage prevented, no identifiers. |
| bbd5e762 | A | 101 | Crash-safe mechanism plus result; tight one-liner. |
| fa335da1 | A | 101 | Concrete security outcome; legal one-line. |
| eacd2edb | B | 180 | Bold on opener "Established" misplaced; content strong. |
| 673e91ae | A | 195 | Failure isolation clear; single sentence. |

### pestwise — 12 bullets (8 A / 4 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 160afca8 | A | 183 | Decoupled backend with independent scaling; filler removed. |
| 099adf78 | A | 194 | Native-driver tradeoff plus control; clean. |
| e3a32621 | A | 181 | Leads with failure prevented; concrete validation detail. |
| cc369955 | A | 213 | Compliance result plus specifics; no filler. |
| 2e6c8db1 | B | 180 | Outcome "one consistent structure" too vague. |
| bd2a2bb2 | A | 203 | Security outcome across both apps; clean. |
| 83951557 | A | 197 | CORS failure prevented; identifiers stripped. |
| 6623b36f | B | 178 | "Planned" weak opener; bold on generic noun. |
| 74031e2d | B | 198 | "Flexible annotations" vague; bold generic. |
| bfaea77d | A | 204 | BFF shields UI; named technique. |
| 1e537451 | B | 180 | "Dependable reporting" soft; bold generic. |
| 213ebb8c | A | 185 | Concrete "configurable without code changes" result. |

### Engineering Requirement V&V Service — 14 bullets (12 A / 2 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 5cab0eb6 | A | 99 | Scale evidenced; RAG named; legal one-liner. |
| c7babdea | A | 208 | Leakage prevented; internal interface names removed. |
| edb70c96 | A | 182 | Evidenced 913x result; two valid bolds. |
| 9eb05e3a | A | 193 | Strong ground-truth big idea; no filler. |
| b8a6b327 | A | 201 | Parser with scale and provenance; numbers consistent. |
| 3ef05a34 | A | 102 | Reload cost avoided; scale evidenced. |
| 134295e9 | A | 190 | Concrete failure prevented plus recovery. |
| 5f2a5cbe | A | 186 | Specs and batching; clean single sentence. |
| 5d76f7bc | A | 182 | Quality outcome, one bold, legal band. |
| e114972e | B | 100 | Activity only; no stated outcome. |
| 92ae4949 | B | 96 | "Enhancing semantic search" remains vague. |
| 4574f6f4 | A | 177 | Prompt drift prevented; two valid bolds. |
| 5e4a3ecd | A | 183 | Traceability result with 2,121 scale. |
| 7d2e15a3 | A | 191 | Silently failing model caught; strong failure. |

### mls-realestate — 11 bullets (9 A / 2 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 60c13c4f | A | 203 | Bridge big idea; tail tightened. |
| 670da8be | A | 215 | No-redeploy outcome; clean. |
| bac985dd | A | 206 | Billing accuracy result; repo names stripped. |
| 068cbc7a | A | 102 | User-facing capability; internal name removed. |
| a6392f45 | A | 99 | Identical-data outcome; legal one-liner. |
| ce4dbc3e | A | 185 | Burst abuse prevented; named limiter. |
| ba588b52 | A | 196 | Real security outcome; strong. |
| cd9ee086 | B | 179 | "Attribution gaps" vague; bold on process. |
| 0a647b5f | A | 189 | Per-source parsing eliminated; clean. |
| 5e03c61d | A | 217 | Vendor list dropped; consistency outcome. |
| 804e47d6 | B | 182 | Banned filler "ensuring"; outage result otherwise good. |

### Uoft Prerequisite Explorer — 5 bullets (5 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| c18b17f5 | A | 171 | Scale plus filtering result; backticks gone. |
| e0db1c7e | A | 171 | Strong metrics, no looping; consistent 2,450. |
| a698e45b | A | 101 | One bold; delivery outcome; legal. |
| 291e8abe | A | 182 | Named grammar; preserved structure; consistent. |
| 2ae4526b | A | 186 | Tokenizer mechanism; parser reads messy text. |

### ArbRight Sports Betting Arbitrage — 5 bullets (5 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 807acc3b | A | 226 | Protocol decoded; 3 sportsbooks consistent. |
| 648ced47 | A | 217 | Two stated results; hash key removed. |
| d8e71c63 | A | 97 | Token access outcome; legal one-liner. |
| 813adbd1 | A | 219 | Stale odds rejected; specs evidenced. |
| ea76468a | A | 225 | Phantom arbitrage prevented; one bold. |

### Aerospace and Defense Index — 6 bullets (6 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| b353ba91 | A | 214 | Diversification forced; two named techniques. |
| 078c7d05 | A | 218 | Look-ahead bias prevented; clean. |
| b776bb81 | A | 200 | Cost drag quantified; two results. |
| 2557550b | A | 217 | Drawdown result benchmarked; risk controls. |
| 1f995492 | A | 183 | Distribution checks; specificity restored. |
| b06bf631 | A | 188 | Headline return and Sharpe; single sentence. |

### Foundify — 3 bullets (3 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 1c4ba5c4 | A | 172 | Real founder outcomes; newline removed. |
| 444d7d7f | A | 209 | Scale plus scoring purpose; clean. |
| 61930bbc | A | 174 | User outcome; manual work avoided. |

### Hylo - Trading Terminal — 8 bullets (8 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 604c2fa7 | A | 190 | Deadlocks and leaks prevented. |
| dca9a49b | A | 188 | Indicator drift eliminated; internal name gone. |
| c7c50813 | A | 185 | Stale data rejected; clean. |
| 7214b269 | A | 178 | Charts never stall; outcome stated. |
| f46cec21 | A | 188 | Trader-facing outcome; named analytics. |
| 812c812c | A | 100 | Single market view; legal one-liner. |
| 2ab2c64a | A | 104 | Deterministic tests; legal one-liner. |
| f40c03d3 | A | 171 | Draw budget held; "16ms" spelled out. |

### AnvilCv — 10 bullets (10 A / 0 B)

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 7125678c | A | 225 | RAG ranking with resume outcome; one bold. |
| 76dd7e7f | A | 188 | Credential theft prevented; clean. |
| dd5c1b6e | A | 210 | Coverage gaps surfaced; internal name gone. |
| 7a5d834e | A | 191 | Cross-tenant leak prevented; counts removed. |
| 71246137 | A | 209 | Overflow avoided; outcome now stated. |
| d4ac7430 | A | 180 | Runaway requests prevented; clear. |
| 0dd923ec | A | 201 | No public attack surface; one bold. |
| e134cd0f | A | 199 | Regressions caught before release. |
| b658c778 | A | 201 | Users trace resume flow; path removed. |
| 5b7288b7 | A | 199 | Cold-start failures eliminated. |

---

## 4. Final prioritized fixes

**No bullet is C**, so no full rewrites are required. The following are the remaining fixes in
priority order; the two hard violations include concrete drop-in replacements verified to stay in
the legal band and use only facts already in the bullet.

1. **HARD — `804e47d6`: remove banned filler "ensuring" and its bold span.** Current text bolds
   "**ensuring uninterrupted service**". Replace with (182 chars, two-line, 1 bold, one sentence):

   > Implemented a dynamic **LiveKit-to-Vapi capacity-overflow policy** for inbound/outbound calls, so service stays uninterrupted across peak loads and unexpected telephony provider outages.

2. **HARD — `eacd2edb`: move the misplaced bold off the opener.** Current text bolds "**Established**",
   which is not a result/scale/tech/technique. Replace with (180 chars, two-line, 1 bold):

   > Established the deployment sequence with the app container starting first, tunnel ingress configured second, and DNS last, so traffic never reaches an **unready tunnel** for end users.

3. **Soften none — sharpen the 9 B outcomes** (all already legal; each fix uses only facts in the bullet):
   - `2e6c8db1` — replace "one consistent structure for…" with the actions: *"so users manage clients, schedule, and view reports in one place."*
   - `6623b36f` — swap weak opener "Planned" → "Modeled" and re-target bold to "six nested sub-structures."
   - `74031e2d` — delete vague "flexible annotations": *"letting technicians attach notes per record without reshaping the schema or risking existing records."*
   - `1e537451` — drop "for dependable reporting": *"keeping per-service financial records accurate and queryable inside each client profile."*
   - `cd9ee086` — replace "closing attribution gaps": *"so only authenticated users see unavailable statuses."*
   - `e114972e` — add the implied result: *"so each received the correct extraction rules."* (still process-level; consider merging into a sibling bullet.)
   - `92ae4949` — "enhancing semantic search" needs an evidenced result; without one, merge into `5d76f7bc`/`7d2e15a3` rather than inventing a number.
   - `ba588b52` — re-target bold from "tracked remediation program" to the outcome ("IDOR and cost-abuse vulnerabilities closed").
   - `804e47d6` — covered by fix 1; after removing "ensuring" it promotes to A.

4. **Advisory — re-target 9 soft bolds** to a stated result/scale/marquee tech/named technique:
   `6623b36f`, `74031e2d`, `1e537451`, `ba588b52`, `cd9ee086`, `76dd7e7f`, `dd5c1b6e`,
   `d4ac7430`, `b658c778`. Low priority — span counts are legal.

5. **Minor number wording:** align `b353ba91` "10-15 ticker universe" with `078c7d05` "15-ticker
   universe" (pick one), and consider writing `f40c03d3` "sixteen-millisecond" as "16 ms" to match
   the numeric style used elsewhere.

6. **Watch the opener cap:** 19 verbs are used exactly twice — any future addition must reuse none of
   them. If a new bullet needs one of those verbs, pick a fresh strong opener instead.

7. **Optional polish:** `160afca8`, `2ae4526b`, and `1f995492` read as inventories (resources,
   tokenizer steps, metrics). They are legal A bullets; lead with an outcome only if a factual one is
   available, otherwise leave as-is.
