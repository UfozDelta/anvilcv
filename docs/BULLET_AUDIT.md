# Resume Bullet Bank Audit

**Corpus:** 206 bullets across 10 projects (6 PROJECT, 4 EXPERIENCE). Rubric: AnvilCV SOP.

## Overall verdict

**Reject as a publishable bank; rebuild per project.** The corpus has strong raw material — several standout bullets with genuine stated results — but it is dominated by three systemic failures: (1) extreme near-duplication (≈51 clusters), (2) length and bold-format violations on ~60% of bullets, and (3) internal code identifiers and activity counts presented as achievements. Only **12 bullets (6%) are A**; **121 (59%) are C** (rewrite or drop). The worst projects — ArbRight, Aerospace and Defense Index, Hylo, AnvilCv — would each need near-total rewriting. Net recommendation: select one bullet per story, rebuild it inside a legal length band, then re-run this audit.

## Corpus-wide stats

| Metric | Count | Note |
|---|---|---|
| Total bullets | 206 | 10 projects |
| Score A / B / C | **12 / 73 / 121** | 6% / 35% / 59% |
| One-line band (96–104) | 23 | 11% |
| Two-line band (170–229) | 72 | 35% |
| **DEAD ZONE (111–163)** | 17 | 8%, forbidden |
| Too short (<96) | 40 | 19% |
| Too long (>229) | 34 | 17%, likely 3 lines |
| Gap 105–110 | 4 | too long for one line |
| Gap 164–169 | 16 | too short for two lines |
| Bold violations | **62** | 3+ spans, or 2 spans on a one-line bullet |
| Bullets missing terminal period | 2 | `d0ed1e9d`, `4f0103cc` |
| Bullets with embedded `\n` | 3 | all in Foundify |
| Near-duplicate clusters | **≈51** | spanning the large majority of the bank |
| Internal identifier / activity count | **≈75 (36%)** | see below |

**Length summary:** only 95 of 206 bullets (46%) sit in a legal band. The other 111 are either in the dead zone, in a gap, too short, or too long.

**Bold:** 144 bullets carry 2+ spans; 62 exceed the cap outright. The worst offenders are Aerospace (`1f995492`, `2557550b` at 12 spans each), AnvilCv (`4f0103cc` at 5 spans on a 103-char line), and ArbRight (20 of 23 bullets have 3+ spans).

**Top repeated opening verbs (rubric cap: 2):**

| Verb | Count | Verb | Count |
|---|---|---|---|
| Engineered | 35 | Built | 13 |
| Implemented | 32 | Integrated | 10 |
| Designed | 26 | Owned | 8 |
| Architected | 21 | Developed | 5 |
| Authored | 16 | Hardened | 15 |

Seven openers account for 158 of 206 bullets (77%). Clichés also recur: `leveraging` (≈8), `robust` (≈10), `comprehensive` (≈8), `streamlined` (2), `ensuring` (≈8).

**Internal identifiers / activity counts (≈75 bullets):** `multiprocessing.Manager().dict()`, `SHA-256[:12]`, `PerimeterX px_token`, `validateClientData`, `requireAuth()`, `ContextualizerBackend`, `EmbeddingBackend`, `PdfViewerFix`, `RAG` pipeline object names, `mymls`/`mycrm`/`mls_number`, `JSESSIONID`, `FRONTEND_URL`, `JobPosting`, `tz_localize(None)`, `RiskManager`, `TickSource`, `F3L Bands`, `/flow`, `@WebMvcTest` — plus activity counts used as results: `2,306 LOC`, `632 LOC`, `6,062 lines of Python`, `87 commits`, `~192 unit tests`, `20 Flyway migrations`, `156 in-repo assert checks`, `9 modules / 14 classes / 39 features`.

**Cross-cluster number inconsistencies (reconcile before publishing):**
- ArbRight: most bullets say **3 sportsbooks**; `dcfa445e` and `7db5407a` say **6**.
- Aerospace: **2 years** (`d0ed1e9d`) vs **10-month** (`b06bf631`) for the same backtest; **2 sharpe** (`d0ed1e9d`) vs **Sharpe 2.976** (`b353ba91`, `b06bf631`).

## 5 highest-impact fixes

1. **Deduplicate.** Cut ≈51 clusters to one bullet per story — roughly 206 → 60–70 bullets. This alone removes most C ratings and most verb repetition.
2. **Rebuild length + bold.** Move the 34 overlong bullets into 170–229, lift the 40 too-short bullets into 96–104, eliminate the 17 dead-zone bullets, and cap bold at 1/2 spans (62 violations).
3. **Strip internal identifiers and activity counts** from ≈75 bullets. Replace code names with user-facing capability; delete LOC/commits/tests/migrations as results.
4. **Diversify openers and kill clichés.** Cap every verb at 2 uses; replace `Engineered`/`Implemented`/`Designed` and `robust`/`comprehensive`/`leveraging`/`ensuring` with specific verbs.
5. **Delete second restating sentences and filler outcomes.** "This ensured…", "This enabled…", "enabling comprehensive business logic", "empowering users…", "ensuring robust performance" appear in ~30 bullets and add zero information.

---

## Project: Portal
**Kind:** PROJECT | **Bullets:** 10 | **A/B/C:** 3 / 3 / 4

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 6843ddca | B | 91 | Good stamp idea; 91 chars is too short. |
| eacd2edb | A | 180 | Clear deploy sequence with end-user outcome. |
| 9b741d46 | B | 168 | Failure prevented; 168 just below two-line band. |
| 2a0bf28a | B | 185 | Two sentences; merge mechanism into one. |
| 07e1deac | C | 81 | Trivial flag-vs-env config detail; no outcome. |
| bbd5e762 | A | 101 | Tight one-liner; crash-safe mechanism plus outcome. |
| 6aae1ffd | C | 97 | Near-duplicate of bbd5e762; drop. |
| fa335da1 | C | 83 | Internal compose directive; no user outcome. |
| 673e91ae | A | 198 | Failure isolation clear; slightly wordy tail. |
| cb4ae775 | C | 178 | Near-duplicate of 673e91ae; weaker verb. |

**Recommendations:** Keep the three A bullets; merge `9b741d46` with `eacd2edb` if the deployment story is one story. Drop `07e1deac` and `fa335da1` (config detail, internal directive). Replace the `ports: !override` and `cloudflared` references with "zero host ports" / "tunnel" language. Best candidate rewrite — `2a0bf28a` (185 → 175, two-line, 1 bold, one sentence):

> Architected state storage holding only identifiers and paths so tokens never touched disk, and loaded credentials from environment sources without caching, **preventing leakage**.

---

## Project: pestwise
**Kind:** EXPERIENCE | **Bullets:** 37 | **A/B/C:** 2 / 16 / 19

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 160afca8 | B | 206 | Inventory plus "comprehensive" filler ending. |
| 344d95a8 | C | 168 | Unsupported "optimizing retrieval"; schema duplicate. |
| 099adf78 | B | 150 | Dead zone; drop driver version, trim to one line. |
| dbf230e4 | B | 164 | Internal middleware name; 164-char gap. |
| 97cad556 | B | 156 | Internal requireAuth() name; dead zone. |
| bc88cbaf | B | 166 | Good decoupling outcome; expand past gap slightly. |
| b16ff7ab | C | 154 | Dead zone; CRUD/schema feature inventory. |
| 6b52fa5a | C | 160 | Dead zone; feature list, report-cluster overlap. |
| 0e16477a | C | 90 | Too short; two bold spans; no outcome. |
| e3a32621 | A | 193 | Validation with stated failure prevented. |
| 898b73e4 | C | 182 | "Robust" and "comprehensive"; schema duplicate. |
| 7d28b2d6 | B | 168 | Good compliance outcome; expand past gap. |
| 0a41c40c | C | 181 | "Utilizing"/"robust" clichés + internal name. |
| 19edea15 | C | 166 | Gap-length feature list; PDF duplicate. |
| 83906eac | B | 110 | Good outcome; trim six chars to one line. |
| fba48807 | C | 106 | Gap length; "comprehensive CRUD" feature list. |
| f7ce0f11 | C | 144 | Dead zone; parenthetical inventory; duplicate. |
| cc369955 | A | 205 | Best PDF report bullet: outcome plus specifics. |
| 0ceecc47 | B | 223 | "Critical attestations" filler; signature overlap. |
| 2e6c8db1 | C | 96 | Two bold on one line; route count only. |
| 90183cda | C | 88 | Tool inventory; too short; no outcome. |
| a76eb49e | B | 174 | Minor filler; otherwise clean frontend-auth bullet. |
| 4cf27d98 | C | 221 | Feature list with filler outcome; trim heavily. |
| 93c13782 | C | 171 | Small bug fix with internal component name. |
| 1a9585e9 | C | 83 | Too short; BFF duplicate of bfaea77d. |
| bd2a2bb2 | C | 194 | Duplicate of two auth bullets; pick one scope. |
| e279976d | B | 172 | Strong "bypassing frontend" angle; remove name. |
| 83951557 | B | 157 | Remove FRONTEND_URL and "robust"; fix length. |
| cb268e7c | C | 101 | Gap length; "effectively" filler; duplicate. |
| 61fa1e1c | C | 175 | Vague "critical for analytics"; validation duplicate. |
| a10cae3e | C | 187 | Two clichés; feature inventory; endpoint duplicate. |
| 9d84e971 | C | 99 | Two bold on one line; duplicate validation story. |
| 6623b36f | B | 189 | Best schema bullet; drop "robust" cliché. |
| 74031e2d | B | 181 | Distinct content; replace vague "flexible annotation". |
| bfaea77d | B | 169 | Best BFF version; 169 chars, tighten outcome. |
| 1e537451 | B | 176 | Decent outcome; swap weak "Managed" opener. |
| 213ebb8c | B | 183 | Distinct work; replace vague service-management outcome. |

**Recommendations:** This is the most duplicated project — seven clusters (17 endpoints, 6 nested sub-structures, validation middleware, Clerk auth, PDF reports/signatures, BFF/8 segments, raw driver). Keep `e3a32621` and `cc369955` as-is; merge `6623b36f`, `bc88cbaf`, `a76eb49e`, `bfaea77d`, and `74031e2d` after trimming. Delete `93c13782` (small bug fix) and all tool inventories. Two rewrites — `dbf230e4` (164 → 192, two-line, 2 bold) and `83906eac` (110 → 100, one-line, 1 bold):

> **Hardened** data integrity with custom server-side middleware enforcing required fields, email formats, and status enums before any client record is written to **MongoDB**, rejecting malformed input.

> **Hardened** ingestion with custom validation middleware, preventing malformed records reaching MongoDB.

---

## Project: Engineering Requirement V&V Service
**Kind:** PROJECT | **Bullets:** 33 | **A/B/C:** 5 / 19 / 9

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 5cab0eb6 | B | 99 | Drop one bold span; strong scale plus outcome. |
| c7babdea | B | 195 | Good outcome; drop internal interface/library names. |
| 47871e63 | C | 88 | Too short; no result; benchmark duplicate. |
| edb70c96 | A | 182 | Rare stated metric (913x); clean standout bullet. |
| 9eb05e3a | A | 193 | Big idea on ground-truth sourcing; no filler. |
| b8a6b327 | B | 201 | Solid parser; overlaps heading-inference bullet. |
| 45dedc0e | B | 194 | Drop library name; same story as 9eb05e3a. |
| df5510df | C | 95 | Too short; two bold; no outcome. |
| 3ef05a34 | B | 106 | Trim two chars into one-line band. |
| 134295e9 | A | 194 | Concrete failure prevented; clean two-liner. |
| ffb4b561 | C | 89 | Too short; 7-model count is not an outcome. |
| 1e022d2d | C | 183 | "Leveraging advanced" cliché; segmenter duplicate. |
| cdc7f85e | B | 168 | Fix gap; drop X-API-Key header name. |
| 5f2a5cbe | B | 192 | Drop internal class name; "scalable" unsupported. |
| 5d76f7bc | A | 177 | Best segmenter: quality outcome, one bold. |
| e114972e | C | 90 | "Orchestrated" cliché; too short; no outcome. |
| dca3df79 | C | 86 | Too short; model-name inventory; no outcome. |
| 92ae4949 | B | 96 | Clean length; "enhancing semantic search" too vague. |
| 37d2b33b | B | 96 | Drop one bold; keep concrete s/req metric. |
| 4574f6f4 | A | 176 | Clean mechanism plus prompt-drift prevented. |
| 4989e8d1 | C | 89 | Tool inventory; too short; no outcome. |
| 71f3b4c9 | C | 168 | Internal class name; duplicate of c7babdea. |
| 5e4a3ecd | B | 199 | Good traceability outcome; trim provenance list. |
| 85613820 | B | 179 | Good standards angle; remove backtick, tighten. |
| c19e1d5a | B | 199 | Drop "leveraging"; overlaps b8a6b327. |
| c59b8946 | B | 168 | Drop "novel"; tighten outcome; fix gap. |
| 5e3532f8 | B | 174 | Good XXE-prevention story; drop library name. |
| 2a869860 | C | 194 | "Comprehensive"/"streamlining"; no real outcome. |
| 7d2e15a3 | B | 187 | Strong failure prevented; drop "leveraging". |
| 3c3d37b2 | B | 181 | Concrete 9x result; parenthetical model list bloats. |
| 8dc74c2c | B | 179 | Good leakage prevention; drop "rigorous". |
| 771d05d5 | B | 177 | Drop internal class name; overlaps 3ef05a34. |
| a5dc5eb5 | B | 169 | Good big picture; expand four chars past gap. |

**Recommendations:** Eight clusters (2,121-corpus RAG, 8,484 embeddings, benchmark harness, contextualizer backends, ReqIF/DOORS, segmenter/25-char, font-ratio parser, Pydantic schema). Keep all five A bullets. Consolidate the six benchmark bullets into `edb70c96` plus one comparison bullet; consolidate the five segmenter bullets into `5d76f7bc`. Strip `ContextualizerBackend`, `EmbeddingBackend`, `EmbeddedRequirement`, `xml.etree.ElementTree`, and `X-API-Key`. Two rewrites — `5cab0eb6` (99 → 99, one-line, 1 bold) and `47871e63` (88 → 96, one-line, 1 bold):

> Built a **RAG pipeline** over 2,121 requirement statements from 8 documents for aerospace traceability.

> Built a four-axis **benchmark harness** that scores LLM context blurbs via a cosine embedding delta.

---

## Project: mls-realestate
**Kind:** EXPERIENCE | **Bullets:** 31 | **A/B/C:** 1 / 12 / 18

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 60c13c4f | B | 167 | Strong bridge story; fix gap, tighten tail. |
| 670da8be | B | 166 | Good no-redeploy story; drop "leveraging". |
| bac985dd | B | 168 | Strong billing idea; strip internal repo names. |
| 068cbc7a | C | 78 | Too short; internal product name; vague outcome. |
| ec2e12b9 | C | 98 | Two bold on one line; duplicate of 670da8be. |
| 1a8f010c | C | 185 | Internal names plus "leveraging"; bridge duplicate. |
| 31fda471 | C | 92 | Too short; internal names; tool inventory. |
| a6392f45 | B | 99 | Clean length; remove internal system/field names. |
| fb6f8a27 | C | 157 | Dead zone; internal names; billing duplicate. |
| 290423f9 | B | 107 | Distinct overflow routing; add outcome, fix gap. |
| 9b7bfb9a | B | 151 | Compliance angle worth keeping; fix zone, drop name. |
| ecabc5f2 | C | 104 | Duplicate of 60c13c4f; internal names. |
| c6619d9f | C | 147 | Dead zone; internal name; config duplicate. |
| ce4dbc3e | B | 126 | Good abuse prevention; 126 chars needs rewrite. |
| ba588b52 | B | 139 | Real security outcome; drop names, fix length. |
| 1bb4ad24 | C | 183 | Internal names plus "ensuring"; billing duplicate. |
| cd9ee086 | B | 174 | Concrete compliance outcome; swap weak "Owned". |
| f7441f57 | C | 129 | Dead zone; internal field; schema duplicate. |
| 922cd37a | C | 180 | Vague "ensuring"; compliance duplicate. |
| 814e2b8a | C | 157 | Dead-zone config duplicate; weak "Managed". |
| 90d09bf2 | C | 155 | Internal names; vague outcome; bridge duplicate. |
| ea22e90f | C | 176 | Internal names; billing-ingestion duplicate. |
| 0a647b5f | B | 152 | Distinct work; drop "robust", name, fix zone. |
| 5e03c61d | B | 189 | Good big idea; drop parenthetical vendor list. |
| a5816c0a | B | 178 | Voice-to-search story; drop names and "sophisticated". |
| e66372c5 | C | 190 | Billing duplicate; "granular" filler. |
| 804e47d6 | A | 182 | Concrete outage prevented; clean two-liner. |
| 357f9986 | C | 183 | Weak opener, vague outcome; telephony duplicate. |
| 7ed22857 | C | 186 | Cliché plus vendor list; telephony duplicate. |
| 991b6951 | C | 176 | Two clichés, vague outcome; telephony duplicate. |
| 81f4db29 | C | 173 | Vague "agent efficiency"; bridge duplicate. |

**Recommendations:** Seven clusters (bidirectional bridge, no-redeploy config, 9-event billing ledger, shared data layer, five telephony providers, VOW/PropTx compliance, security/rate limiting). Keep `804e47d6`. Consolidate five telephony bullets into one; consolidate six billing bullets into `bac985dd`. Systematically replace backticked `mymls`/`mycrm` with "MLS platform"/"CRM" or remove — 16 bullets use them. Two rewrites — `068cbc7a` (78 → 98, one-line, 1 bold) and `ecabc5f2` (104 → 101, one-line, 1 bold):

> Shipped the **AI chat/search system** for MLS listings, enhancing user engagement across the platform.

> Architected a **bidirectional AI service bridge** enabling cross-repo AI logic calls between CRM and MLS.

---

## Project: Uoft Prerequisite Explorer
**Kind:** PROJECT | **Bullets:** 5 | **A/B/C:** 1 / 4 / 0

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| c18b17f5 | B | 167 | Good scale story; expand four chars past gap. |
| e0db1c7e | B | 165 | Strong metrics; remove stray backticks, fix gap. |
| a698e45b | B | 99 | Drop one bold; keep size plus delivery outcome. |
| 291e8abe | A | 182 | Named technique plus preserved structure; clean. |
| 2ae4526b | B | 176 | Tokenizer detail; drop backticked identifier. |

**Recommendations:** The healthiest project — five distinct stories, no duplicates. Fix the three formatting gaps and the two one-line bold violations, and remove the stray backticks around `2,450`/`41,317` and `recursive-descent parser`. Note `a698e45b` needs one bold span removed (drop "11.5 MB" or "2,450" emphasis), not a content change. Rewrite of `c18b17f5` (167 → 171, two-line, 2 bold):

> Engineered a BeautifulSoup4 ingestion pipeline to extract **9 metadata fields** across **70 discipline prefixes** from the UTM academic calendar, filtering out 62 retired courses.

---

## Project: ArbRight Sports Betting Arbitrage
**Kind:** PROJECT | **Bullets:** 23 | **A/B/C:** 0 / 3 / 20

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 807acc3b | C | 347 | 347 chars, 7 bold, two sentences; split or drop. |
| 50f93266 | C | 314 | 314 chars, 6 bold; second sentence restates. |
| 153c61e0 | C | 305 | Internal shared-state API; 305 chars, two sentences. |
| 648ced47 | C | 299 | Internal hash key; 299 chars, two sentences. |
| d8e71c63 | B | 214 | Three bold spans; trim to two, drop "ensuring". |
| bb61982e | C | 257 | Internal hash key; duplicate lifecycle tracker. |
| 813adbd1 | B | 223 | Good stale-data guard; cut to two bold spans. |
| ea76468a | C | 238 | 238 chars, 5 bold; name-matcher duplicate. |
| 3a007562 | C | 258 | 2,306 LOC is activity count; 258 chars. |
| 5e9db3cd | C | 314 | LOC count, two sentences, 314 chars. |
| ab7ba697 | C | 340 | Internal shared-state API; 340 chars, two sentences. |
| d08ab56d | C | 304 | 632 LOC activity count; 304 chars, two sentences. |
| c919a6e2 | C | 336 | Internal hash key; 336 chars, two sentences. |
| 393ffeaf | C | 263 | Internal px_token; 263 chars, two sentences. |
| 4faee7c9 | C | 265 | LOC count; 265 chars with 6 bold. |
| 6a880de5 | C | 275 | Internal shared-state API; engine duplicate. |
| 80b97309 | C | 246 | Internal hash key; 246 chars; tracker duplicate. |
| c3aa30b8 | C | 249 | 249 chars, four bold; integration duplicate. |
| 63b46d3f | B | 214 | Four bold spans; keep one ingestion story. |
| dcfa445e | C | 198 | Four bold; "6 sportsbooks" conflicts with cluster's 3. |
| 7db5407a | C | 191 | Five bold; "6 sportsbooks" conflicts with cluster's 3. |
| e9d6d89b | C | 255 | Internal hash key; 255 chars, two sentences. |
| 1a9d8eb2 | C | 155 | Garbled dead-zone summary; 3 bold, no outcome. |

**Recommendations:** Five clusters (msgpack reverse-engineering, 3-tier name matcher, concurrency/50 ms loop, lifecycle tracker, API integration). Reduce to ~5 bullets total. Every bullet must lose internal identifiers (`multiprocessing.Manager().dict()`, `SHA-256[:12]`, `PerimeterX px_token`) and LOC counts, and every two-sentence bullet must become one. Reconcile the 3-vs-6 sportsbooks discrepancy. Two rewrites — `813adbd1` (223 → 196, two-line, 2 bold) and `648ced47` (299 → 187, two-line, 2 bold):

> Engineered an event-driven arbitrage detection engine with a **50 ms** loop and a **2.0 s** staleness gate, rejecting stale odds from 3 sportsbooks to prevent exploitation and protect financial integrity.

> Designed an arbitrage lifecycle tracker keyed on normalized event and sorted runner pairs, tracking **1,261 records** over a ~9-hour session and yielding a **median open window of 2.76 seconds**.

---

## Project: Aerospace and Defense Index
**Kind:** PROJECT | **Bullets:** 15 | **A/B/C:** 0 / 0 / 15

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| d0ed1e9d | C | 170 | Six bold, no period, code-size inventory. |
| 196afb80 | C | 271 | 271 chars, 6 bold, two sentences. |
| b353ba91 | C | 278 | 278 chars, 5 bold, two sentences. |
| 643768b1 | C | 291 | Normality-test names; 291 chars, two sentences. |
| 6020c1ab | C | 244 | Internal function name; 244 chars, two sentences. |
| 078c7d05 | C | 418 | 418 chars, 7 bold, two sentences. |
| c95788e5 | C | 358 | 358 chars, 9 bold, garbled "parser design" claim. |
| b776bb81 | C | 391 | 391 chars, 8 bold, two sentences. |
| 2557550b | C | 384 | 12 bold spans; mislabeled "data quality" engine. |
| 1f995492 | C | 405 | 405 chars, 12 bold, two sentences. |
| b06bf631 | C | 340 | LOC count; 340 chars, 8 bold, two sentences. |
| ea5a277d | C | 329 | 329 chars, 10 bold, two sentences. |
| cd2eaefc | C | 181 | Second "Enabling..." fragment; no stated result. |
| d600866c | C | 191 | Six bold, broken second sentence, no result. |
| 345ef284 | C | 125 | Dead zone; cliché; metric counts as content. |

**Recommendations:** **All 15 bullets are C** — rewrite the whole project from ~6 stories (backtest infra, alpha engine, execution simulator, metrics suite, multi-region pipeline, risk manager). Keep the real headline numbers (46.44% return, Sharpe 2.976, -10.74% max drawdown) but attach them to one bullet each. Remove code-size counts (6,062 lines, 9 modules, 14 classes) and internal names (`RiskManager`, `tz_localize(None)`, `Shapiro-Wilk`). Reconcile 2-year vs 10-month and 2-sharpe vs 2.976. Rewrite of `d0ed1e9d` (170 → 184, two-line, 2 bold, terminal period added):

> Architected a full backtesting infrastructure that delivered a **46.44% cumulative return** over a **2-year** systematic equity backtest, validating the strategy against benchmark performance.

---

## Project: Foundify
**Kind:** PROJECT | **Bullets:** 3 | **A/B/C:** 0 / 3 / 0

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 1c4ba5c4 | B | 189 | Strong outcomes; stray newline and semicolon chain. |
| 444d7d7f | B | 207 | Good outcome; newline and parenthetical feature list. |
| 61930bbc | B | 198 | Good user outcome; newline; trim feature list. |

**Recommendations:** Best raw content in the corpus — real founder outcomes (SPUR, VC letter of intent, $200K seed discussions) and no internal identifiers. Only problems: literal `\n` in each text and missing bold. Rewrite of `1c4ba5c4` (189 → 193, two-line, 1 bold, newline removed):

> Co-founded an AI-powered startup-to-VC matching platform; pitched at the SPUR accelerator, secured a letter of intent from a VC firm, and advanced to discussions on a proposed **$200K seed round**.

---

## Project: Hylo - Trading Terminal
**Kind:** PROJECT | **Bullets:** 19 | **A/B/C:** 0 / 4 / 15

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 604c2fa7 | C | 327 | 327 chars, 6 bold, two sentences. |
| 34723766 | B | 99 | Drop one bold; 1e8 keys are internal detail. |
| 02c2f928 | C | 96 | Two bold plus internal TickSource; no outcome. |
| dca9a49b | C | 262 | 8 bold; internal "F3L Bands"; two sentences. |
| 5dc83571 | C | 85 | Assert-check count as result; 85 chars, two bold. |
| c7c50813 | C | 337 | 337 chars, 7 bold, internal state names. |
| 7214b269 | C | 89 | Too short; two bold; no stated outcome. |
| f46cec21 | C | 231 | 231 chars, 6 bold, two sentences; TPO duplicate. |
| fae0d756 | B | 98 | Drop one bold; add user-facing outcome. |
| 7a93a07e | C | 89 | Too short; streaming-queue duplicate. |
| 812c812c | C | 90 | Too short; internal name; venue duplicate. |
| 426c4550 | B | 99 | Drop one bold; keep 16.7ms render budget. |
| 56d54d2e | C | 95 | Too short; two bold; indicator count only. |
| 2ab2c64a | B | 81 | Good replay idea; expand into one-line band. |
| f72b1150 | C | 101 | Two bold on one line; queue duplicate. |
| f40c03d3 | C | 292 | 292 chars, 9 bold, two sentences. |
| bcad9eae | C | 95 | Too short; internal state names; duplicate. |
| bf4579dd | C | 84 | Too short; two bold; engine duplicate. |
| 6438009d | C | 84 | Too short; internal Broadcaster name; no outcome. |

**Recommendations:** Six clusters (L2 order book, streaming engine/indicator-op queue, four venues, indicator/Footprint/TPO engines, WebSocket server, backpressure). Reduce to ~6 bullets. Strip `TickSource`, `F3L Bands`, `Broadcaster`, `LIVE/RESYNCING/STALE`, `1e8 scaled-integer keys`, and the "156 in-repo assert checks" activity count. Two rewrites — `2ab2c64a` (81 → 102, one-line, 1 bold) and `34723766` (99 → 104, one-line, 1 bold):

> Built a **deterministic record/replay system** on an append-only JSONL tick corpus for repeatable testing.

> Architected **L2 order book maintenance** with scaled-integer keys for exact level equality across 4 venues.

---

## Project: AnvilCv
**Kind:** PROJECT | **Bullets:** 30 | **A/B/C:** 0 / 9 / 21

| ID (8) | Rating | Chars | Reason |
|---|---|---|---|
| 7125678c | C | 94 | Too short, two bold; RAG duplicate of 52b548ee. |
| 76dd7e7f | B | 200 | Cut to two bold, merge sentences, drop "ensuring". |
| dd5c1b6e | C | 96 | Two bold on one line; internal JobPosting name. |
| a1199288 | B | 86 | Distinct work; expand, one bold, add outcome. |
| 7a5d834e | C | 102 | Activity counts (migrations/endpoints); two bold. |
| a6395c80 | C | 91 | Internal JSESSIONID; tool inventory; too short. |
| 5d910f68 | B | 94 | Real latency outcome; expand, one bold. |
| f16f65c3 | C | 192 | Test count as result plus second restating sentence. |
| 2ec7ebf0 | B | 90 | Distinct PDF work; expand, one bold, concrete speed. |
| 7d6f03b4 | C | 96 | Duplicate of dd5c1b6e; two bold, internal name. |
| 71246137 | C | 93 | Character-model detail; no outcome; two bold. |
| b4292425 | C | 280 | 280 chars, 7 bold, two sentences. |
| 3ed9d6e0 | C | 195 | Five bold; "robust" filler; vague outcome. |
| d4ac7430 | B | 93 | Distinct telemetry work; expand and state outcome. |
| dc1d64a4 | C | 84 | Commit count as result; 84 chars; weak opener. |
| 24e76397 | C | 83 | Too short; config duplicate; two bold. |
| 0dd923ec | B | 96 | Good zero-ports outcome; drop one bold span. |
| 4f0103cc | C | 103 | Five bold, no period, tool inventory. |
| e134cd0f | C | 178 | Commit count plus second restating sentence. |
| b658c778 | B | 224 | Distinct visualization; cut bold, drop path/filler. |
| e788600c | C | 83 | Too short; two bold; no stated outcome. |
| eda20b76 | C | 84 | Too short; polling interval is detail, no outcome. |
| 52b548ee | C | 101 | Two bold; "Ensuring" fragment; RAG duplicate. |
| a3d109ba | C | 196 | Two sentences with "This ensured" plus "robust". |
| 1ae95166 | C | 85 | Too short; endpoint count; no outcome. |
| 03549b8c | C | 90 | Migration count plus internal pgcrypto. |
| 3463ccb8 | B | 91 | Real isolation outcome; expand, drop tool name. |
| 229e8b07 | C | 86 | Too short; internal business-rule detail. |
| a42f3359 | C | 90 | Internal test annotations; vague "fast". |
| 5b7288b7 | B | 82 | Good scale-to-zero outcome; expand to one line. |

**Recommendations:** Eight clusters (RAG ranking, multi-provider LLM/AES keys, JD scraping, Postgres 9 tables/20 migrations/41 endpoints, HikariCP tuning, testing, 87 commits, PDF/LaTeX). Keep none as-is; merge into ~8 bullets. Delete all commit/test/migration/endpoint counts used as results and internal names (`JSESSIONID`, `JobPosting`, `pgcrypto`, `/flow`, `@WebMvcTest`). Two rewrites — `0dd923ec` (96 → 98, one-line, 1 bold) and `5b7288b7` (82 → 99, one-line, 1 bold):

> Engineered **self-hosted deployment** via Docker Compose and Cloudflare Tunnel for zero inbound ports.

> Set a **4-minute** connection lifetime to keep PostgreSQL connections alive through Neon scale-to-zero.

---

## Prioritized action list

1. **Global deduplication (do first).** Work project by project, ≈51 clusters. One bullet per story. This removes ~140 candidate bullets and most verb-repetition problems at a stroke.
2. **Resolve number inconsistencies.** ArbRight 3 vs 6 sportsbooks; Aerospace 2-year vs 10-month and Sharpe 2 vs 2.976. A recruiter comparing bullets will catch these.
3. **Rewrite the 15 Aerospace bullets and 20 ArbRight C bullets from scratch.** They are the bulk of the length/bold violations and cannot be salvaged by trimming.
4. **Strip internal identifiers and activity counts** from the ≈75 affected bullets. Priority: `multiprocessing.Manager().dict()`, `SHA-256[:12]`, `PerimeterX px_token`, LOC counts, commit/test/migration counts, `mymls`/`mycrm`.
5. **Fix format mechanically:** 34 overlong → two-line band; 40 too-short → one-line band; 17 dead-zone → one of the two bands; 16 gap-164/169 → into two-line; 62 bold violations → ≤1/≤2 spans; add terminal periods to `d0ed1e9d` and `4f0103cc`; remove the three literal `\n`.
6. **Diversify openers and remove clichés** in the surviving ~65 bullets; enforce the 2-use cap per verb and delete `leveraging`, `robust`, `comprehensive`, `ensuring`, `streamlined`.
7. **Final pass:** confirm every surviving bullet names a user-visible capability or a failure prevented, and is understandable in 5 seconds. Re-run this audit; target ≥90% A.
