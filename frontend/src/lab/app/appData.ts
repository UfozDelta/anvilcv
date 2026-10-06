// Demo data for the /lab/app and /lab/nav prototypes. Product-shaped, not real users.
// Bullet counts, last-edited and repo state are what the redesign needs; the live
// ProjectResponse doesn't carry bullet counts or updatedAt yet.

export type RepoState = 'none' | 'exploring' | 'explored';

export type DemoProject = {
  id: string;
  name: string;
  description: string;
  stack: string[];
  bullets: number;
  repo: RepoState;
  /** Minutes since last edit; drives "edited 2h ago" and the Bench hero. */
  editedMinAgo: number;
  topBullets: string[];
};

export const PROJECTS: DemoProject[] = [
  {
    id: 'p1', name: 'quorum-kv', description: 'Raft-backed key-value store in Go with a Jepsen test harness.',
    stack: ['Go', 'Raft', 'Jepsen'], bullets: 9, repo: 'explored', editedMinAgo: 35,
    topBullets: [
      'Built a Raft-backed key-value store in Go, linearizable under 3-node Jepsen partitions',
      'Cut leader-election time 60% by tuning heartbeat and randomized election timeouts',
      'Wrote a property-based test suite that caught 4 split-brain bugs before release',
    ],
  },
  {
    id: 'p2', name: 'ledger-split', description: 'Payments monolith split into gRPC services on Kubernetes.',
    stack: ['Java', 'gRPC', 'Kubernetes'], bullets: 12, repo: 'explored', editedMinAgo: 60 * 26,
    topBullets: [
      'Split the payments monolith into 6 gRPC services with zero-downtime cutover',
      'Cut p99 checkout latency 41% by batching ledger writes into Postgres COPY',
      'Automated canary rollouts across 3 clusters with Argo Rollouts',
    ],
  },
  {
    id: 'p3', name: 'shelf', description: 'Offline-first reading-list PWA with IndexedDB sync.',
    stack: ['TypeScript', 'React', 'PWA'], bullets: 4, repo: 'explored', editedMinAgo: 60 * 24 * 3,
    topBullets: [
      'Shipped an offline-first PWA with IndexedDB sync and sub-1s cold start',
      'Added Playwright visual-regression tests across 3 browsers',
    ],
  },
  {
    id: 'p4', name: 'pg-pool-exporter', description: 'Prometheus exporter for Postgres connection pools.',
    stack: ['Go', 'Prometheus'], bullets: 0, repo: 'exploring', editedMinAgo: 8,
    topBullets: [],
  },
  {
    id: 'p5', name: 'churn-lab', description: 'Churn model and retention-campaign targeting.',
    stack: ['Python', 'PyTorch'], bullets: 2, repo: 'none', editedMinAgo: 60 * 24 * 12,
    topBullets: ['Trained a churn model lifting retention-campaign precision to 0.81'],
  },
  {
    id: 'p6', name: 'anvil-cv', description: 'This app: résumé tailoring from a bullet bank.',
    stack: ['Spring Boot', 'React', 'LaTeX'], bullets: 7, repo: 'explored', editedMinAgo: 60 * 5,
    topBullets: [
      'Built an LLM pipeline that ranks résumé bullets against a job post in ~17s',
      'Compiled one-page LaTeX PDFs server-side with Tectonic',
      'Encrypted provider API keys at rest with AES-256-GCM',
    ],
  },
];

/** A project is "ready" once it has enough bullets to tailor from. */
export const READY_AT = 6;
/** Bar scale for bullet counts (a full bank). */
export const FULL_BANK = 12;

export type Readiness = 'needs' | 'exploring' | 'ready';
export function readiness(p: DemoProject): Readiness {
  if (p.repo === 'exploring') return 'exploring';
  return p.bullets >= READY_AT ? 'ready' : 'needs';
}

export function edited(min: number): string {
  if (min < 60) return `${min}m ago`;
  if (min < 60 * 24) return `${Math.round(min / 60)}h ago`;
  const d = Math.round(min / (60 * 24));
  return d === 1 ? 'yesterday' : `${d}d ago`;
}

export const REPO_LABEL: Record<RepoState, string> = {
  none: 'No repo',
  exploring: 'Reading repo…',
  explored: 'Repo read',
};


export type DemoJob = {
  id: string;
  source: 'linkedin' | 'indeed';
  saved: boolean;
  title: string;
  company: string;
  location: string;
  /** One line from the posting's role summary. */
  desc: string;
  postedMinAgo: number;
};

export const JOBS: DemoJob[] = [
  { id: 'j1', source: 'linkedin', saved: true, title: 'Backend Engineering Intern', company: 'Stripe', location: 'San Francisco, CA', desc: 'Build and scale the ledger services behind Stripe payments.', postedMinAgo: 22 },
  { id: 'j2', source: 'linkedin', saved: false, title: 'Software Engineer Intern, Workers', company: 'Cloudflare', location: 'Austin, TX', desc: 'Work on the runtime that executes customer code at the edge.', postedMinAgo: 95 },
  { id: 'j3', source: 'indeed', saved: false, title: 'Infrastructure Intern', company: 'Datadog', location: 'New York, NY', desc: 'Help run the Kubernetes and Kafka fleet that ingests metrics.', postedMinAgo: 60 * 5 },
  { id: 'j4', source: 'indeed', saved: false, title: 'Full-Stack Intern', company: 'Figma', location: 'Remote', desc: 'Ship product features across the React frontend and Node services.', postedMinAgo: 60 * 9 },
  { id: 'j5', source: 'linkedin', saved: true, title: 'Platform Engineering Intern', company: 'Vercel', location: 'Remote', desc: 'Improve build and deploy performance for the edge network.', postedMinAgo: 60 * 26 },
  { id: 'j6', source: 'indeed', saved: false, title: 'Product Engineering Intern', company: 'Linear', location: 'Remote', desc: 'Own a feature end to end, from API to polished UI.', postedMinAgo: 60 * 24 * 3 },
];

/** rejected = they said no. ghosted = they never replied. */
export type Outcome = 'applied' | 'interview' | 'offer' | 'rejected' | 'ghosted';
export const OUTCOMES: Outcome[] = ['applied', 'interview', 'offer', 'rejected', 'ghosted'];

export type DemoApplication = {
  id: string;
  company: string;
  role: string;
  outcome: Outcome;
  /** Days since the application was generated. */
  daysAgo: number;
  /** Fit = how well you match the job. Page = how well the rendered résumé sells you. null = pre-scoring. */
  fit: number | null;
  page: number | null;
};

export const APPLICATIONS: DemoApplication[] = [
  { id: 'a1', company: 'Stripe', role: 'Backend Engineer, Ledger', outcome: 'interview', daysAgo: 2, fit: 91, page: 84 },
  { id: 'a2', company: 'Cloudflare', role: 'Systems Engineer, Workers', outcome: 'applied', daysAgo: 3, fit: 78, page: 88 },
  { id: 'a3', company: 'Datadog', role: 'Software Engineer, Metrics', outcome: 'offer', daysAgo: 21, fit: 86, page: 90 },
  { id: 'a4', company: 'Figma', role: 'Full-Stack Engineer', outcome: 'applied', daysAgo: 4, fit: 64, page: 72 },
  { id: 'a5', company: 'Vercel', role: 'Infrastructure Engineer', outcome: 'rejected', daysAgo: 18, fit: 52, page: 69 },
  { id: 'a6', company: 'Linear', role: 'Product Engineer', outcome: 'interview', daysAgo: 9, fit: 82, page: 61 },
  { id: 'a7', company: 'Notion', role: 'Software Engineer, Sync', outcome: 'rejected', daysAgo: 34, fit: 58, page: 77 },
  { id: 'a8', company: 'Acme Robotics', role: 'Platform Engineer', outcome: 'ghosted', daysAgo: 62, fit: null, page: null },
];

export function applied(daysAgo: number): string {
  if (daysAgo === 0) return 'Today';
  if (daysAgo === 1) return 'Yesterday';
  if (daysAgo < 14) return `${daysAgo}d ago`;
  if (daysAgo < 60) return `${Math.round(daysAgo / 7)}w ago`;
  return `${Math.round(daysAgo / 30)}mo ago`;
}

export type DemoExperience = {
  id: string;
  title: string;
  company: string;
  location: string;
  /** [year, month]; end null = current role. */
  start: [number, number];
  end: [number, number] | null;
  bullets: number;
  repo: RepoState;
};

export const EXPERIENCES: DemoExperience[] = [
  { id: 'e1', title: 'Software Engineer', company: 'Northwind Payments', location: 'Remote', start: [2024, 6], end: null, bullets: 9, repo: 'explored' },
  { id: 'e2', title: 'Backend Engineering Intern', company: 'Datadog', location: 'New York, NY', start: [2023, 6], end: [2023, 8], bullets: 5, repo: 'none' },
  { id: 'e3', title: 'Research Assistant', company: 'Distributed Systems Lab, UIUC', location: 'Urbana, IL', start: [2022, 9], end: [2024, 5], bullets: 6, repo: 'explored' },
  { id: 'e4', title: 'Teaching Assistant, Algorithms', company: 'UIUC CS', location: 'Urbana, IL', start: [2022, 1], end: [2022, 12], bullets: 2, repo: 'none' },
  { id: 'e5', title: 'Web Developer', company: 'Freelance', location: 'Remote', start: [2020, 5], end: [2021, 12], bullets: 0, repo: 'exploring' },
];

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const ym = ([y, m]: [number, number]) => `${MONTHS[m - 1]} ${y}`;
export function datesLabel(e: DemoExperience): string {
  return `${ym(e.start)} – ${e.end ? ym(e.end) : 'Present'}`;
}
/** "1y 4m" tenure. Current roles run to a fixed demo "now" so the prototype doesn't drift. */
export function tenure(e: DemoExperience): string {
  const [ey, em] = e.end ?? [2026, 10];
  const months = (ey - e.start[0]) * 12 + (em - e.start[1]) + 1;
  const y = Math.floor(months / 12);
  const m = months % 12;
  return [y && `${y}y`, m && `${m}m`].filter(Boolean).join(' ') || '1m';
}
/** Sort key: newest-ending first, current roles on top. */
export const endKey = (e: DemoExperience) => (e.end ? e.end[0] * 12 + e.end[1] : Infinity);

export type DemoEducation = {
  id: string;
  school: string;
  location: string;
  degree: string;
  dates: string;
  coursework: string;
};

export const PROFILE = {
  basics: { name: 'Maks Y.', email: 'maks@example.com', phone: '(555) 010-2231', linkedin: 'maks-y', github: 'maks', portfolio: '' },
  edu: [
    { id: 'edu-1', school: 'University of Illinois Urbana-Champaign', location: 'Urbana, IL', degree: 'B.S. Computer Science & Mathematics', dates: 'Sep. 2022 – May 2026', coursework: 'Distributed Systems, Algorithms, Operating Systems, Probability, Linear Algebra' },
    { id: 'edu-2', school: 'Chicago Math & Science Academy', location: 'Chicago, IL', degree: 'High School Diploma', dates: 'Aug. 2018 – May 2022', coursework: '' },
  ] as DemoEducation[],
  skills: {
    languages: 'Go, Java, TypeScript, Python, SQL',
    frameworks: 'Spring Boot, React, gRPC',
    databases: 'PostgreSQL, Redis, pgvector, RAG',
    devops: 'Docker, Kubernetes, GitHub Actions, Terraform',
    interests: 'OpenAI API, LangChain, Stripe webhooks',
  },
};

export type DemoSettings = {
  wordFilterEnabled: boolean;
  singleLineLow: number; singleLineHigh: number;
  doubleLineLow: number; doubleLineHigh: number;
  deadZoneLow: number; deadZoneHigh: number;
  minWordFloor: number;
  temperature: number;
  boldDensity: 'NONE' | 'LIGHT' | 'HEAVY';
  tone: 'CONSERVATIVE' | 'NEUTRAL' | 'AGGRESSIVE';
  actionVerbStyle: 'TECHNICAL' | 'LEADERSHIP' | 'IMPACT';
};

export const SETTINGS: DemoSettings = {
  wordFilterEnabled: true,
  singleLineLow: 12, singleLineHigh: 22,
  doubleLineLow: 26, doubleLineHigh: 44,
  deadZoneLow: 23, deadZoneHigh: 25,
  minWordFloor: 8,
  temperature: 0.7,
  boldDensity: 'LIGHT',
  tone: 'NEUTRAL',
  actionVerbStyle: 'TECHNICAL',
};

/** One application per path, as the outcome-history endpoint would return it. */
export const FLOW_APPS: { id: string; company: string; role: string; path: string[] }[] = [
  { id: 'f1', company: 'Datadog', role: 'Software Engineer, Metrics', path: ['applied', 'oa', 'interview', 'offer'] },
  { id: 'f2', company: 'Stripe', role: 'Backend Engineer, Ledger', path: ['applied', 'oa', 'interview', 'rejected'] },
  { id: 'f3', company: 'Linear', role: 'Product Engineer', path: ['applied', 'oa', 'interview'] },
  { id: 'f4', company: 'Cloudflare', role: 'Systems Engineer, Workers', path: ['applied', 'oa', 'ghosted'] },
  { id: 'f5', company: 'Figma', role: 'Full-Stack Engineer', path: ['applied', 'oa', 'rejected'] },
  { id: 'f6', company: 'Vercel', role: 'Infrastructure Engineer', path: ['applied', 'interview', 'rejected'] },
  { id: 'f7', company: 'Notion', role: 'Software Engineer, Sync', path: ['applied', 'ghosted'] },
  { id: 'f8', company: 'Ramp', role: 'Backend Engineer', path: ['applied', 'rejected'] },
  { id: 'f9', company: 'Plaid', role: 'Platform Engineer', path: ['applied', 'ghosted'] },
  { id: 'f10', company: 'Acme Robotics', role: 'Platform Engineer', path: ['applied'] },
  { id: 'f11', company: 'Retool', role: 'Software Engineer', path: ['applied'] },
  { id: 'f12', company: 'Supabase', role: 'Database Engineer', path: ['applied'] },
];
export const FLOW_HISTORY: { applicationId: string; outcome: string; changedAt: string }[] = FLOW_APPS.flatMap((a) =>
  a.path.map((outcome, step) => ({ applicationId: a.id, outcome, changedAt: `2026-09-${String(step + 1).padStart(2, '0')}T12:00:00Z` })),
);
