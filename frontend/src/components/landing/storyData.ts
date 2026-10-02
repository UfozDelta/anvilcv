// Demo content for the landing "How it works" stage. Product-shaped, not real users or jobs.

export const PAGE_SLOTS = 6;
/** Max bullets one role or project may put on the page. */
export const CAP = 3;
export const AVG_SEC = 17.2;

export type Entry = { id: string; kind: 'EXPERIENCE' | 'PROJECTS'; org: string; title: string; when: string };
export type Bullet = { id: string; entry: string; text: string };
export type Keyword = { label: string; terms: string[] };
export type Job = {
  id: string;
  tab: string;
  company: string;
  role: string;
  where: string;
  jd: string;
  keywords: Keyword[];
  scores: Record<string, number>;
  /** ATS match of the tailored page vs. one generic résumé. */
  match: number;
  generic: number;
};

export const ENTRIES: Entry[] = [
  { id: 'tally', kind: 'EXPERIENCE', org: 'Tallyhouse', title: 'Software Engineer II', when: '2023 – now' },
  { id: 'bright', kind: 'EXPERIENCE', org: 'Brightline Health', title: 'Software Engineer', when: '2021 – 23' },
  { id: 'quorum', kind: 'PROJECTS', org: 'quorum-kv', title: 'Go, open source', when: '' },
  { id: 'shelf', kind: 'PROJECTS', org: 'shelf', title: 'Reading-list PWA', when: '' },
];

export const BANK: Bullet[] = [
  { id: 't1', entry: 'tally', text: 'Cut p99 checkout latency 41% by batching ledger writes into Postgres COPY' },
  { id: 't2', entry: 'tally', text: 'Split the payments monolith into 6 gRPC services on Kubernetes, zero-downtime cutover' },
  { id: 't3', entry: 'tally', text: 'Built a Kafka → BigQuery pipeline feeding fraud dashboards, 40M events/day' },
  { id: 't4', entry: 'tally', text: 'Wrote Terraform modules for 30+ services; new-service setup fell from 2 days to 1 hour' },
  { id: 'b1', entry: 'bright', text: 'Led migration of 120 React screens to a shared design system documented in Storybook' },
  { id: 'b2', entry: 'bright', text: 'Trained a churn model in Python/PyTorch, lifting retention-campaign precision to 0.81' },
  { id: 'b3', entry: 'bright', text: 'Modeled the patient-event warehouse in dbt: 140 tested SQL models, run nightly in Airflow' },
  { id: 'b4', entry: 'bright', text: 'Rebuilt patient intake in TypeScript to WCAG AA; mobile completion up 18%' },
  { id: 'q1', entry: 'quorum', text: 'Raft-backed key-value store in Go, linearizable under Jepsen tests' },
  { id: 'q2', entry: 'quorum', text: 'Postgres connection-pool exporter for Prometheus; 1.2k GitHub stars' },
  { id: 's1', entry: 'shelf', text: 'Offline-first PWA with IndexedDB sync and sub-1s cold start' },
  { id: 's2', entry: 'shelf', text: 'Playwright visual-regression suite across 3 browsers; caught 40+ UI regressions pre-merge' },
];

const kw = (label: string, ...terms: string[]): Keyword => ({ label, terms: terms.length ? terms : [label] });

export const JOBS: Job[] = [
  {
    id: 'backend', tab: 'BACKEND', company: 'Ledgerly', role: 'Senior Backend Engineer', where: 'Remote, US',
    jd: 'Ledgerly moves $2B a month through Go and gRPC services on Kubernetes. You will tune Postgres under load, own checkout p99, and finish our move to Terraform. Shared on-call, one week in six.',
    keywords: [kw('Go'), kw('gRPC'), kw('Kubernetes'), kw('Postgres'), kw('p99'), kw('Terraform'), kw('On-call')],
    scores: { t2: 97, t1: 94, q1: 90, t4: 86, q2: 81, t3: 64, b3: 58, s1: 31, s2: 24, b2: 20, b1: 14, b4: 12 },
    match: 91, generic: 38,
  },
  {
    id: 'data', tab: 'DATA', company: 'Northwind Analytics', role: 'Data Engineer', where: 'New York, hybrid',
    jd: 'Northwind needs a Data Engineer to own streaming ingestion in Kafka, model the warehouse in dbt and SQL, schedule it in Airflow and land it in BigQuery. Python daily; Snowflake is a plus.',
    keywords: [kw('Kafka'), kw('dbt'), kw('SQL'), kw('Airflow'), kw('BigQuery'), kw('Python'), kw('Snowflake')],
    scores: { t3: 98, b3: 96, b2: 88, t1: 61, q2: 55, t4: 50, t2: 42, q1: 35, s2: 20, s1: 18, b4: 15, b1: 12 },
    match: 88, generic: 34,
  },
  {
    id: 'frontend', tab: 'FRONTEND', company: 'Kite', role: 'Product Engineer, Web', where: 'Remote, EU',
    jd: 'Kite ships a React and TypeScript PWA to 400k travelers. Grow our design system in Storybook, write Playwright tests for what you ship, and hold the line on accessibility. GraphQL is a bonus.',
    keywords: [kw('React'), kw('TypeScript'), kw('PWA'), kw('Storybook'), kw('Playwright'), kw('Accessibility', 'accessibility', 'WCAG'), kw('GraphQL')],
    scores: { b1: 96, s1: 93, s2: 91, b4: 89, q2: 34, t2: 30, t1: 26, t4: 22, q1: 20, t3: 18, b3: 14, b2: 10 },
    match: 94, generic: 29,
  },
];

export type RankedRow = { bullet: Bullet; score: number; status: 'page' | 'cap' | 'bank' };

/** Ranks the whole bank for a job: top PAGE_SLOTS go on the page, max CAP per entry. */
export function rankBank(job: Job): RankedRow[] {
  const perEntry: Record<string, number> = {};
  let placed = 0;
  return [...BANK]
    .sort((a, b) => job.scores[b.id] - job.scores[a.id])
    .map((bullet) => {
      const score = job.scores[bullet.id];
      if (placed >= PAGE_SLOTS) return { bullet, score, status: 'bank' as const };
      const n = perEntry[bullet.entry] ?? 0;
      if (n >= CAP) return { bullet, score, status: 'cap' as const };
      perEntry[bullet.entry] = n + 1;
      placed += 1;
      return { bullet, score, status: 'page' as const };
    });
}

const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function termRegex(terms: string[]) {
  return new RegExp(`(?<![\\w-])(${terms.map(esc).join('|')})(?![\\w-])`, 'gi');
}

export type Seg = { t: string; kw?: string };

/** Splits text into plain runs and keyword hits, tagged with the keyword label. */
export function segments(text: string, keywords: Keyword[]): Seg[] {
  const byTerm = new Map<string, string>();
  keywords.forEach((k) => k.terms.forEach((t) => byTerm.set(t.toLowerCase(), k.label)));
  const re = termRegex([...byTerm.keys()]);
  const out: Seg[] = [];
  let last = 0;
  for (const m of text.matchAll(re)) {
    const i = m.index ?? 0;
    if (i > last) out.push({ t: text.slice(last, i) });
    out.push({ t: m[0], kw: byTerm.get(m[0].toLowerCase()) });
    last = i + m[0].length;
  }
  if (last < text.length) out.push({ t: text.slice(last) });
  return out;
}

/** Keyword labels covered by at least one bullet on the page. */
export function coveredKeywords(job: Job, rows: RankedRow[]): Set<string> {
  const covered = new Set<string>();
  rows.filter((r) => r.status === 'page').forEach((r) =>
    segments(r.bullet.text, job.keywords).forEach((s) => s.kw && covered.add(s.kw)),
  );
  return covered;
}

export const FEED = [
  { company: 'Ledgerly', role: 'Senior Backend Engineer', where: 'Remote, US', tags: ['Go', 'Postgres', 'K8s'], age: '2h' },
  { company: 'Northwind Analytics', role: 'Data Engineer', where: 'New York, hybrid', tags: ['Kafka', 'dbt', 'BigQuery'], age: '5h' },
  { company: 'Kite', role: 'Product Engineer, Web', where: 'Remote, EU', tags: ['React', 'TS', 'PWA'], age: '1d' },
  { company: 'Halcyon', role: 'Site Reliability Engineer', where: 'Austin, TX', tags: ['Terraform', 'AWS'], age: '1d' },
  { company: 'Arbor', role: 'ML Engineer', where: 'San Francisco', tags: ['PyTorch', 'Python'], age: '2d' },
];
