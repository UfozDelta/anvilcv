import { describe, it, expect } from 'vitest';
import { parseExtract } from './parseExtract';

// A trimmed stand-in for content_extract.md output: two sections that own a
// field outright, one that folds into an owned field, one that folds into a
// field with no owning section, and one unrecognized section that folds nowhere.
const DOC = `# Project Context Extractor

## Architecture Overview
→ AnvilCV field: **description** (paste this whole section)

Two Next.js apps sharing one Postgres layer.

---

## Hardest Problem Solved
→ AnvilCV field: **hardestProblem**

CONSTRAINT: five telephony backends. APPROACH: one provider-abstraction config.

---

## Standout Signal
→ fold into: **description** (use as its lead sentence)

**Bidirectional cross-repo AI bridge** — two deployed products calling each other.

---

## Notable Technical Decisions
→ AnvilCV field: **technicalDecisions**

Chose Redis over Postgres pub/sub to cut write amplification.

---

## Failure Modes Avoided
→ fold into: **technicalDecisions**

**Idempotent webhook handler** preventing **double-charges on provider retries**.

---

## Work Character
→ fold into: **yourRole** as framing context

\`built greenfield\` · \`hardened / secured\`

---

## Anti-Patterns — What to Suppress

| Anti-pattern | What to do |
|---|---|
| Inflated scale | omit or qualify |
`;

describe('parseExtract', () => {
  it('maps a section heading to the field it owns', () => {
    expect(parseExtract(DOC).description).toContain('Two Next.js apps');
  });

  it('drops the → pointer lines from the body', () => {
    for (const body of Object.values(parseExtract(DOC))) {
      expect(body).not.toContain('→');
    }
  });

  it('appends a "fold into" section to the field it names', () => {
    const description = parseExtract(DOC).description!;
    expect(description).toContain('Two Next.js apps');
    expect(description).toContain('Bidirectional cross-repo AI bridge');
  });

  it('keeps the owning section first when a fold is appended', () => {
    const { technicalDecisions } = parseExtract(DOC);
    expect(technicalDecisions!.indexOf('Chose Redis'))
      .toBeLessThan(technicalDecisions!.indexOf('Idempotent webhook handler'));
  });

  it('routes a fold to a field promoted out of an earlier fold', () => {
    const { technicalDecisions, hardestProblem } = parseExtract(DOC);
    expect(technicalDecisions).toContain('Idempotent webhook handler');
    expect(hardestProblem).not.toContain('Idempotent webhook handler');
  });

  it('fills a field that only a "fold into" section supplies', () => {
    expect(parseExtract(DOC).yourRole).toContain('built greenfield');
  });

  it('ignores sections that fold nowhere', () => {
    const all = Object.values(parseExtract(DOC)).join('\n');
    expect(all).not.toContain('Inflated scale');
  });

  it('returns nothing for empty input', () => {
    expect(parseExtract('')).toEqual({});
  });
});
