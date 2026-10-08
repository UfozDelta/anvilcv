/** Fictional fixtures for /lab/lists. */

/** repoReady false = the repo is still being read. */
type Base = { id: string; name: string; stack: string[]; bullets: number; repo: string | null; repoReady?: boolean; createdAt: string; updatedAt: string };
export type ProjectItem = Base & { description: string };
export type ExpItem = Base & { company: string; location?: string; dates: string; current?: boolean };

const daysAgo = (d: number) => new Date(Date.now() - d * 24 * 3600_000).toISOString();

export const PROJECTS: ProjectItem[] = [
  { id: 'quayside', name: 'Quayside', description: 'Event bus for a harbour logistics sim', stack: ['Go', 'PostgreSQL', 'Kafka', 'gRPC', 'Docker'],
    bullets: 18, repo: 'ferrow/quayside', createdAt: daysAgo(60), updatedAt: daysAgo(0.1) },
  { id: 'tidemark', name: 'Tidemark', description: 'Tide forecasting from buoy data', stack: ['Python', 'PyTorch', 'Airflow', 'Pandas'],
    bullets: 11, repo: 'ferrow/tidemark', createdAt: daysAgo(90), updatedAt: daysAgo(1) },
  { id: 'lanternfish', name: 'Lanternfish', description: 'Realtime chat for reading clubs', stack: ['TypeScript', 'Node', 'Redis'],
    bullets: 4, repo: 'ferrow/lanternfish', createdAt: daysAgo(30), updatedAt: daysAgo(9) },
  { id: 'saltbox', name: 'Saltbox', description: 'Offline recipe manager', stack: ['Rust', 'SQLite'],
    bullets: 7, repo: null, createdAt: daysAgo(120), updatedAt: daysAgo(21) },
  { id: 'kestrel-notes', name: 'Kestrel Notes', description: 'Markdown notes with sync', stack: ['Kotlin', 'Spring', 'PostgreSQL', 'React', 'AWS', 'Terraform'],
    bullets: 23, repo: 'ferrow/kestrel-notes', createdAt: daysAgo(200), updatedAt: daysAgo(4) },
  { id: 'moorline', name: 'Moorline', description: 'Warehouse models for a co-op shop', stack: ['dbt', 'Snowflake'],
    bullets: 0, repo: 'ferrow/moorline', repoReady: false, createdAt: daysAgo(2), updatedAt: daysAgo(2) },
];

export const EXPERIENCES: ExpItem[] = [
  { id: 'harbourlight', name: 'Backend Engineer', company: 'Harbourlight Freight', location: 'Remote', dates: 'Jun 2024 – Present', current: true,
    stack: ['Go', 'PostgreSQL', 'Kubernetes', 'Kafka'], bullets: 14, repo: null, createdAt: daysAgo(100), updatedAt: daysAgo(2) },
  { id: 'pellucid', name: 'ML Intern', company: 'Pellucid Labs', location: 'Leeds', dates: 'May 2023 – Aug 2023',
    stack: ['Python', 'PyTorch', 'Weights & Biases'], bullets: 5, repo: 'pellucid-labs/eval-kit', createdAt: daysAgo(300), updatedAt: daysAgo(40) },
  { id: 'north-quarry', name: 'Data Analyst', company: 'North Quarry Co-op', dates: 'Sep 2021 – Apr 2023',
    stack: ['SQL', 'Excel', 'Tableau', 'Python', 'dbt'], bullets: 9, repo: null, createdAt: daysAgo(500), updatedAt: daysAgo(75) },
];
