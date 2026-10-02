// Demo content for the landing page animations. Product-shaped, not real users.

/** Set to a published sample résumé (e.g. '/sample-resume.pdf') to show the hero's link. */
export const SAMPLE_PDF_URL: string | null = null;

export const AVG_SEC = 17;
export const RUNS = 412;

export const JD_TEXT =
  "We're hiring a Senior Backend Engineer to scale our payments platform. " +
  'You will own gRPC services on Kubernetes, tune Postgres under load, ' +
  'and help us move infra to Terraform.';

export const PIPELINE = [
  { key: 'PARSE', value: '1', unit: 'JD', caption: 'company, role, keywords extracted' },
  { key: 'RANK', value: '34', unit: 'bullets', caption: 'every bullet scored against the JD' },
  { key: 'SELECT', value: '8', unit: 'picked', caption: 'max 3 per project, override anything' },
  { key: 'COMPILE', value: '17.2', unit: 'sec', caption: 'LaTeX → Tectonic → one-page PDF' },
] as const;

export type Bullet = { id: string; section: 'EXPERIENCE' | 'PROJECTS'; text: string };

export const BULLETS: Bullet[] = [
  { id: 'b1', section: 'EXPERIENCE', text: 'Cut p99 checkout latency 41% by moving ledger writes to batched Postgres COPY' },
  { id: 'b2', section: 'EXPERIENCE', text: 'Split payments monolith into 6 gRPC services on Kubernetes, zero-downtime cutover' },
  { id: 'b3', section: 'EXPERIENCE', text: 'Built Kafka → BigQuery pipeline feeding fraud dashboards for 40M events/day' },
  { id: 'b4', section: 'EXPERIENCE', text: 'Led migration of 120 React screens to a shared design system with Storybook' },
  { id: 'b5', section: 'PROJECTS', text: 'Wrote a Raft-backed key-value store in Go; linearizable under Jepsen tests' },
  { id: 'b6', section: 'PROJECTS', text: 'Trained churn model in PyTorch, lifting retention-campaign precision to 0.81' },
  { id: 'b7', section: 'PROJECTS', text: 'Shipped offline-first PWA with IndexedDB sync and sub-1s cold start' },
  { id: 'b8', section: 'PROJECTS', text: 'Open-sourced Postgres connection-pool exporter, 1.2k GitHub stars' },
];

/** The bullets the Assembly variant drops onto the page, in order. */
export const ASSEMBLY_PICKS = ['b2', 'b1', 'b3', 'b5', 'b8'];

export type SampleJd = {
  id: string;
  label: string;
  role: string;
  company: string;
  scores: Record<string, number>;
  matched: string[];
  missing: string[];
};

export const SAMPLE_JDS: SampleJd[] = [
  {
    id: 'backend', label: 'BACKEND', role: 'Senior Backend Engineer', company: 'Ledgerly',
    scores: { b1: 94, b2: 97, b3: 71, b4: 18, b5: 88, b6: 22, b7: 30, b8: 80 },
    matched: ['GRPC', 'KUBERNETES', 'POSTGRES'], missing: ['TERRAFORM'],
  },
  {
    id: 'data', label: 'DATA', role: 'Data Engineer', company: 'Northwind Analytics',
    scores: { b1: 62, b2: 41, b3: 98, b4: 12, b5: 35, b6: 91, b7: 20, b8: 58 },
    matched: ['KAFKA', 'BIGQUERY', 'PYTORCH'], missing: ['DBT', 'AIRFLOW'],
  },
  {
    id: 'frontend', label: 'FRONTEND', role: 'Product Engineer, Web', company: 'Kite',
    scores: { b1: 28, b2: 33, b3: 25, b4: 96, b5: 15, b6: 10, b7: 93, b8: 40 },
    matched: ['REACT', 'PWA', 'STORYBOOK'], missing: ['PLAYWRIGHT'],
  },
];

export const TICKER_ROLES = [
  'Backend Engineer · Ledgerly', 'Data Engineer · Northwind', 'SRE · Halcyon',
  'Product Engineer · Kite', 'ML Engineer · Arbor', 'Platform Engineer · Quarry',
  'iOS Engineer · Fieldnote', 'Staff Engineer · Tidewater',
];
