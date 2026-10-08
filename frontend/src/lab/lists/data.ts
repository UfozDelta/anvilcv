/** Fictional fixtures for /lab/lists. A story is its lens wordings; an approved wording is upper-case in the code. */
import { type Lens } from '../story-flow/data';

export type Story = { lenses: Lens[]; ok: number };
type Base = { id: string; name: string; generatedAt: string | null; stories: Story[] };
export type ProjectItem = Base & { kind: 'project'; stack: string[]; repo: string | null };
export type ExpItem = Base & { kind: 'exp'; company: string; dates: string; now?: boolean };
export type Item = ProjectItem | ExpItem;

const CODE: Record<string, Lens> = { a: 'ai-ml', b: 'backend', d: 'data', g: 'general' };

/** 'aBg' → ai-ml, backend (approved), general. */
const st = (codes: string): Story[] => codes.split(' ').map(c => ({
  lenses: [...c].map(ch => CODE[ch.toLowerCase()]),
  ok: [...c].filter(ch => ch !== ch.toLowerCase()).length,
}));

const daysAgo = (d: number, h = 0) => new Date(Date.now() - (d * 24 + h) * 3600_000).toISOString();

export const PROJECTS: ProjectItem[] = [
  { kind: 'project', id: 'quayside', name: 'Quayside', stack: ['Go', 'PostgreSQL', 'Kafka'], repo: 'ferrow/quayside',
    generatedAt: daysAgo(0, 3), stories: st('aBg Bd aD g bG ab') },
  { kind: 'project', id: 'tidemark', name: 'Tidemark', stack: ['Python', 'PyTorch', 'Airflow'], repo: 'ferrow/tidemark',
    generatedAt: daysAgo(1), stories: st('AB ag D bd Ag Gb a aD B dg b Ga') },
  { kind: 'project', id: 'lanternfish', name: 'Lanternfish', stack: ['TypeScript', 'Node', 'Redis'], repo: 'ferrow/lanternfish',
    generatedAt: daysAgo(9), stories: st('bg G') },
  { kind: 'project', id: 'saltbox', name: 'Saltbox', stack: ['Rust', 'SQLite'], repo: null,
    generatedAt: daysAgo(21), stories: st('B bg dG b') },
  { kind: 'project', id: 'kestrel-notes', name: 'Kestrel Notes', stack: ['Kotlin', 'Spring'], repo: 'ferrow/kestrel-notes',
    generatedAt: daysAgo(4), stories: st('d Dd a bd D') },
  { kind: 'project', id: 'moorline', name: 'Moorline', stack: ['Python', 'dbt', 'Snowflake'], repo: 'ferrow/moorline',
    generatedAt: null, stories: [] },
];

export const EXPERIENCES: ExpItem[] = [
  { kind: 'exp', id: 'harbourlight', name: 'Backend Engineer', company: 'Harbourlight Freight', dates: '2024 – now', now: true,
    generatedAt: daysAgo(2), stories: st('Bg bD B ag bG Db b g') },
  { kind: 'exp', id: 'pellucid', name: 'ML Intern', company: 'Pellucid Labs', dates: '2023', generatedAt: daysAgo(40),
    stories: st('A ag') },
  { kind: 'exp', id: 'north-quarry', name: 'Data Analyst', company: 'North Quarry Co-op', dates: '2021 – 2023', generatedAt: daysAgo(75),
    stories: st('dG D dg Ad g') },
];
