# Project Context Extractor

You are Claude. A developer has pointed you at a codebase. Explore it autonomously and produce a filled project context document. The developer will paste each section into the matching AnvilCV field to generate resume bullets.

**How AnvilCV uses this output:** Each field is fed verbatim to an LLM writing bullets in Google XYZ format — `[STRONG VERB] + [WHAT built] + [at WHAT SCALE] + [with WHAT OUTCOME]`. Numbers bold verbatim: no number in context = no number in bullet. Named techniques (`RRF-k fusion`, `AES-256-GCM`, `2dsphere`) bold and get called out by the lens prompts. Bullets must land at 22–26 words (1-line) or 42–50 words (2-line) — the 27–40 range is auto-rejected. Every field should carry enough distinct technique + number + outcome material to support a full 42-word two-liner.

---

## Before You Begin — Ask the Developer

Before exploring the codebase, ask both questions in one message:

> "What was your specific role on this project, and what did you personally own end-to-end? Also — what was the hardest technical problem you had to solve? And finally: if you had one line on a resume for this project, what would you lead with?"

Do not begin codebase exploration or write any section until the developer replies.

Use their answer to fill **Your Role**, **What You Owned End-to-End**, and **Hardest Problem Solved**. The third question reveals what they consider most impressive — surface that in the **Standout Signal** section and as the lead sentence of **Architecture Overview**. Cross-check all claims against git history — if their answer conflicts with blame data, note the discrepancy rather than silently overriding either source.

**Multi-contributor pre-flight:** Before writing any ownership section, run:

```bash
git shortlog -sn --no-merges
```

If more than one contributor has significant commits on core files, lock verb choices to "Led", "Contributed to", or "Collaborated on" — do not use "Architected", "Built", or "Designed" for shared work. Note the contributor count explicitly in **Your Role**.

---

## Output Format

Emit plain headed sections — no outer code fence. Use backticks only for inline technique names. If the repo contains multiple independently-deployable services, emit one full block per service.

---

## Project Name
→ AnvilCV field: **name**

The real product/repo name. 1–5 words.

---

## Tech Stack
→ AnvilCV field: **techStack**

Specific technologies, version-pinned where visible. Surface **named algorithms and protocols**, not just library names.

Signals to look for beyond packages:
- Encryption ciphers: `AES-256-GCM`, `RSA-OAEP`
- DB index types: `GIN`, `2dsphere`, `BRIN`, `partial`, `composite`
- Fusion/ranking algorithms: `RRF-k`, `BM25`, `cosine similarity`
- Serialization protocols: `msgpack`, `protobuf`, `Avro`
- Auth schemes: `JWT`, `PerimeterX px_token`, `hCaptcha`, `HMAC-SHA256`
- Frontend: `virtualized list`, `CRDT`, `WebSocket backpressure`
- Data: `columnar Parquet`, `windowed aggregation`, `append-only log`

---

## Architecture Overview
→ AnvilCV field: **description** (paste this whole section)

3–5 sentences. Lead each sentence with one extractable fact — one subsystem + its technique or number — so each sentence can independently power a bullet. Include before→after deltas only if measured and present in the repo (benchmarks, CHANGELOG, PR bodies). Do not reconstruct deltas that aren't recorded.

Where to look: `README.md` top section, main entry point, core service/handler files, config files showing data flow.

---

## Your Role
→ AnvilCV field: **yourRole**

First-person. What the developer specifically built and owned — not what the team did. Infer from: dominant committer on core files, sole authorship of whole modules, CODEOWNERS entries. If history is squashed or shallow, downgrade to "contributed to" framing rather than asserting sole ownership.

```bash
git log --oneline --since="2 years ago"
git blame --line-porcelain <core-file> | grep '^author ' | sort | uniq -c | sort -rn
```

---

## What You Owned End-to-End
→ AnvilCV field: **ownership** (paste this whole list)

Bulleted list of components/subsystems the developer authored. Each item: component name + named technique where applicable + verb class of the work (built greenfield / optimized hot path / hardened against attack / migrated). Be specific enough that an interviewer could verify it.

```bash
gh pr list --author "@me" --state merged --limit 50
gh pr view <n> --json title,body
```

Ownership claims must be falsifiable from the code. If the repo is small or the contribution scoped, say so — don't inflate a CRUD service into "distributed systems" language.

---

## Scale & Impact
→ AnvilCV field: **scaleImpact**

Numbers + units. At least 3 dimensions. Before→after deltas where they exist in the repo.

**If named metrics are sparse:** run a countable-inventory pass — these are real numbers present in the code even if not labeled as "metrics":
- Endpoint count (routes file)
- Entity/table count (schema/migrations)
- Provider/integration count (adapter files)
- Migration count
- Config-pinned limits: pool sizes, timeout values, batch sizes, retry counts, rate limits, partition counts
- Test count, region count, feature-flag count

Where to find named metrics: benchmark files (`bench/`, `*_bench.*`, `*.jmh`, criterion, `k6`/`locust`), load test results, `CHANGELOG.md` perf entries, README tables, comments with numbers near hot paths, k8s `maxReplicas`.

---

## Hardest Problem Solved
→ AnvilCV field: **hardestProblem**

One paragraph. Three mandatory beats:
- **CONSTRAINT** — what made it hard (scale, latency budget, consistency, concurrency, undocumented API, naming mismatch)
- **APPROACH** — the specific named technique/architecture chosen (not "optimized it" — name it precisely)
- **RESULT** — the measured outcome with a number

If multiple candidates exist, pick the one with all three beats and the most specific technique name. If no candidate has a result number, pick the one with the most specific technique and omit the result rather than fabricating one.

Where to find it: commits with non-obvious solutions, PR bodies with benchmarks, `// why:` comments, ADRs, retry/idempotency/saga patterns, reverse-engineered protocol code, custom ranking/fusion/matching algorithms.

---

## Notable Technical Decisions
→ fold into: **description** or **hardestProblem** as supporting context

2–5 bullets. Each needs: the decision name (bolded), why this over the obvious alternative, what it achieved (number if possible). "Used Redis" is not a decision. "Used Redis over Postgres pub/sub to eliminate write amplification at 50ms polling frequency" is.

Where to find: architectural comments, non-obvious config choices (multiprocessing vs threading, specific IPC mechanism, non-default index type), code that explicitly avoids a known failure mode.

---

## Security & Compliance Posture
→ fold into: **description** or **ownership** for the `security` lens

Optional — emit only if security is meaningful to the project.

- Attack surface defended (what threat, what vector)
- Cipher/scheme used with full name (`AES-256-GCM`, `HMAC-SHA256 webhook signing`)
- Any regulation or compliance framework (SOC 2, GDPR, PCI-DSS, HIPAA)
- Multi-tenant isolation mechanism if applicable

---

## Work Character
→ fold into: **yourRole** or **ownership** as framing context

What *kind* of engineering work was this? Pick all that apply — this determines which strong verbs the bullet LLM should open with:

| Character | Signals in code | Unlocks verbs |
|---|---|---|
| `built greenfield` | no prior commits on core files, initial migrations, first README commit | Architected, Designed, Built, Engineered |
| `optimized existing` | before→after benchmarks, perf commits on existing code, profiler output | Slashed, Reduced, Cut, Accelerated, Optimized |
| `hardened / secured` | encryption added, auth middleware, idempotency keys, replay protection | Hardened, Secured, Eliminated, Enforced |
| `migrated / refactored` | migration scripts, deprecation commits, adapter layers | Migrated, Refactored, Unified, Modernized |
| `integrated third-party` | vendor SDKs, webhook handlers, adapter files | Integrated, Wired, Unified |
| `led / coordinated` | CODEOWNERS with team, PR reviews authored, contributor count > 1 | Led, Drove, Coordinated, Championed |

---

## Standout Signal
→ fold into: **description** (lead sentence) and **hardestProblem**

What would make a staff engineer lean forward? The 1–2 things about this project that are genuinely non-trivial — not just "we used Postgres" but the thing that required real engineering judgment or produced a surprising result.

Look for:
- The technique that's non-obvious for the problem size (e.g. `multiprocessing.Manager().dict()` over threading at 50ms polling)
- A result that beats naive expectation by an order of magnitude (`227K comparisons/sec` vs typical ~5K)
- A constraint that forced an uncommon approach (undocumented API, binary protocol, strict latency budget)
- A system that handles a failure mode most implementations ignore

State it as: "**[what]** — [why it's non-trivial]." One or two lines max. This goes at the top of description so bullets lead with the strongest material.

---

## Failure Modes Avoided
→ fold into: **Notable Technical Decisions** and **hardestProblem**

The strongest senior bullets name what *would have broken* without the design choice. "Idempotent webhook handler" is weaker than "idempotent webhook handler preventing double-charges under provider retries."

Where to look:
- Retry/backoff logic → what failure does this guard against?
- Idempotency keys, deduplication → what duplicate event breaks if missing?
- Circuit breakers, dead-letter queues, timeouts → what cascades without them?
- `// prevent`, `// avoid`, `// guard against`, `// without this` comments
- Staleness filters, TTL logic → what stale-data bug does this block?
- Multi-tenant isolation → what data leak does this prevent?

For each pattern found, frame as: "**[technique]** preventing **[specific failure]**."

---

## Category
→ used to pick AnvilCV generation lens

Pick 1–2 from this exact list that best match the project's center of gravity:

| Lens | Use when |
|---|---|
| `ai-ml` | RAG, embeddings, LLM agents, vector search, training, inference |
| `backend` | APIs, databases, business logic, data modeling, migrations |
| `frontend` | UI, client state, visualizations, rendering, accessibility |
| `data` | ETL pipelines, parsers, analytics, ingestion, data quality |
| `security` | Auth, encryption, threat modeling, compliance, webhook signing |
| `devops` | CI/CD, infra, k8s, observability, GitOps, VPS automation |
| `systems` | Concurrency, distributed systems, real-time, low-level performance |
| `comms` | **Telephony, SMS, WebRTC, email vendors** (Twilio, Telnyx, Vapi) — NOT docs/writing |

If 2 lenses apply: list both. The developer can run bullet generation once per lens to get differently-angled bullets and keep the best.

---

## Anti-Patterns — What to Suppress

Flag or omit anything that would get a candidate caught in an interview or dismissed by a recruiter:

| Anti-pattern | How to detect | What to do |
|---|---|---|
| **Inflated scale** — numbers that are test/seed data not prod traffic | migrations seeding fake rows, test fixtures with large counts, no prod deploy evidence | omit or qualify: "in benchmarks" |
| **Orphaned technique** — named algo/protocol the developer can't explain | copied from a library's README, used as a config value without custom logic | omit from Tech Stack; only surface techniques the dev actually implemented |
| **Solo claim on team work** — "Architected" when contributor count > 1 | `git shortlog -s` shows multiple active contributors on core files | downgrade to "Led" or "Contributed to" |
| **Outdated version signals neglect** — pinned to EOL runtime | `python 3.6`, `node 12`, `react 16` with no update commits | omit version pin; just name the technology |
| **Dead project framed as live** — present-tense metrics on inactive repo | last commit > 18 months ago, no prod deploy config, README says "WIP" | note it was a project/prototype; past-tense framing |
| **Internal jargon** — system names meaningless outside the org | PascalCase internal service names, codenames with no explanation | replace with purpose description |
| **Generic verb on real ownership** — "worked on" when blame is 90% theirs | high blame share but passive role description | upgrade verb to match actual ownership |

---

## Self-Check Before Finishing

Before outputting, verify:
- [ ] Every number traces to a specific file, benchmark, or comment in the repo
- [ ] Every technique name is spelled as it appears in the code, and the developer implemented it (not just configured it)
- [ ] No field left as a placeholder `<...>`
- [ ] Category lens is set to one of the 8 exact slug names
- [ ] Hardest Problem has all three beats, or explicitly notes which beat is missing
- [ ] No delta (before→after) was reconstructed — only reported if measured in the repo
- [ ] No ownership claim that can't be grounded in blame share or sole-module authorship
- [ ] No scale numbers that come from test/seed data rather than production
- [ ] No present-tense metrics on an inactive repo (last commit > 18 months)
- [ ] Anti-patterns table checked — anything flagged is either suppressed or qualified
