/**
 * Placeholder data for the /lab UI prototypes.
 *
 * Nothing here talks to the API — the lab routes render these fixtures directly so the
 * Applications UI can be redesigned and reviewed without a login, a database, or a
 * tectonic build. Shapes mirror `lib/api.ts` exactly so a prototype that reads well here
 * drops onto the real page without a data rewrite.
 */
import type {
  ApplicationResponse,
  ApplicationSummary,
  Bullet,
  BulletVerdict,
  GenerationConfig,
  Project,
  RankedBullet,
} from '../lib/api';

export const LAB_CFG: GenerationConfig = {
  wordFilterEnabled: true,
  singleLineLow: 10,
  singleLineHigh: 14,
  doubleLineLow: 18,
  doubleLineHigh: 26,
  deadZoneLow: 15,
  deadZoneHigh: 17,
  minWordFloor: 8,
  temperature: 0.4,
  boldDensity: 'LIGHT',
  tone: 'NEUTRAL',
  actionVerbStyle: 'TECHNICAL',
};

/** The one-page line budget the real detail page enforces — keep in step with the backend's
 *  BulletSelector.MAX_TOTAL_LINES, same as useApplicationDetail's copy. */
export const LAB_MAX_LINES = 31;

// ---------------------------------------------------------------- list page

export const LAB_SUMMARIES: ApplicationSummary[] = [
  {
    id: 'app-northwind',
    company: 'Northwind Systems',
    role: 'Senior Backend Engineer',
    outcome: 'interview',
    createdAt: '2026-09-02T14:10:00Z',
    fitScore: 84,
    recruiterScore: 71,
  },
  {
    id: 'app-vellum',
    company: 'Vellum Analytics',
    role: 'Data Platform Engineer',
    outcome: 'applied',
    createdAt: '2026-08-28T09:30:00Z',
    fitScore: 61,
    recruiterScore: 48,
  },
  {
    id: 'app-orbit',
    company: 'Orbit Labs',
    role: 'ML Infrastructure Intern',
    outcome: 'rejected',
    createdAt: '2026-08-19T18:45:00Z',
    fitScore: 72,
    recruiterScore: 80,
  },
  {
    id: 'app-harbor',
    company: 'Harbor & Co.',
    role: 'Frontend Engineer',
    outcome: 'offer',
    createdAt: '2026-08-11T11:05:00Z',
    fitScore: null,
    recruiterScore: null,
  },
  {
    id: 'app-kestrel',
    company: 'Kestrel Dynamics',
    role: 'Platform Engineer, Payments',
    outcome: 'applied',
    createdAt: '2026-08-04T16:20:00Z',
    fitScore: 55,
    recruiterScore: 59,
  },
  {
    id: 'app-mirrorlake',
    company: 'Mirrorlake',
    role: null,
    outcome: 'applied',
    createdAt: '2026-07-30T08:00:00Z',
    fitScore: 90,
    recruiterScore: 77,
  },
];

// ---------------------------------------------------------------- detail page

export const LAB_PROJECTS: Project[] = [
  {
    id: 'proj-atlas',
    kind: 'EXPERIENCE',
    name: 'Atlas Freight',
    description: 'Logistics marketplace, 40-person eng org.',
    title: 'Backend Engineer',
    company: 'Atlas Freight',
    location: 'Remote',
    dates: 'Jun 2024 — Present',
    createdAt: '2026-01-04T00:00:00Z',
  },
  {
    id: 'proj-brightline',
    kind: 'EXPERIENCE',
    name: 'Brightline Health',
    description: 'Telehealth scheduling platform.',
    title: 'Software Engineer Intern',
    company: 'Brightline Health',
    location: 'Boston, MA',
    dates: 'May 2023 — Aug 2023',
    createdAt: '2026-01-04T00:00:00Z',
  },
  {
    id: 'proj-tidepool',
    kind: 'PROJECT',
    name: 'Tidepool',
    description: 'Self-hosted RSS reader with a vector search index.',
    githubUrl: 'https://github.com/placeholder/tidepool',
    techStack: 'Go, Postgres, pgvector',
    dates: '2025',
    createdAt: '2026-01-04T00:00:00Z',
  },
  {
    id: 'proj-semaphore',
    kind: 'PROJECT',
    name: 'Semaphore',
    description: 'Terminal dashboard for CI pipelines.',
    githubUrl: 'https://github.com/placeholder/semaphore',
    techStack: 'Rust, ratatui',
    dates: '2024',
    createdAt: '2026-01-04T00:00:00Z',
  },
];

function b(id: string, projectId: string, text: string, tags: string[], category: string): Bullet {
  return {
    id,
    projectId,
    text,
    tags,
    category,
    status: 'APPROVED',
    createdAt: '2026-01-04T00:00:00Z',
    updatedAt: '2026-02-01T00:00:00Z',
  };
}

export const LAB_BULLETS: Bullet[] = [
  b('b1', 'proj-atlas', 'Cut p99 quote latency from **1.8s to 240ms** by replacing a per-request pricing scan with a materialized rate table refreshed on carrier webhook.', ['postgres', 'performance'], 'backend'),
  b('b2', 'proj-atlas', 'Designed the idempotency layer for carrier booking, eliminating duplicate shipments during retry storms across **12 carrier integrations**.', ['distributed-systems', 'reliability'], 'systems'),
  b('b3', 'proj-atlas', 'Shipped a rate-limit budget service that gave each integration its own token bucket.', ['api', 'reliability'], 'backend'),
  b('b4', 'proj-atlas', 'Migrated 80 endpoints from a hand-rolled router to a typed OpenAPI spec, catching **31 contract mismatches** before release.', ['api', 'typescript'], 'backend'),
  b('b5', 'proj-atlas', 'Wrote the on-call runbook for the pricing service.', ['docs'], 'devops'),
  b('b6', 'proj-brightline', 'Built the provider availability solver that packs appointment slots against clinician constraints, lifting booked-hours utilization **9 points**.', ['algorithms', 'scheduling'], 'backend'),
  b('b7', 'proj-brightline', 'Added structured audit logging across PHI-touching endpoints ahead of a HIPAA review.', ['security', 'compliance'], 'security'),
  b('b8', 'proj-brightline', 'Paired with design to rebuild the intake form.', ['frontend'], 'frontend'),
  b('b9', 'proj-tidepool', 'Built a self-hosted reader indexing **50k articles** into pgvector, serving semantic search in under 80ms on a single $5 VPS.', ['go', 'pgvector', 'search'], 'ai-ml'),
  b('b10', 'proj-tidepool', 'Wrote an incremental feed crawler with conditional GETs that dropped outbound bandwidth **70%**.', ['go', 'http'], 'data'),
  b('b11', 'proj-semaphore', 'Built a terminal dashboard streaming CI logs over WebSocket with a 4MB ring buffer, keeping memory flat during multi-hour builds.', ['rust', 'tui'], 'devops'),
  b('b12', 'proj-semaphore', 'Added a fuzzy job filter.', ['rust'], 'devops'),
];

export const LAB_BULLET_MAP: Record<string, Bullet> =
  Object.fromEntries(LAB_BULLETS.map(x => [x.id, x]));

export const LAB_PROJECT_MAP: Record<string, Project> =
  Object.fromEntries(LAB_PROJECTS.map(p => [p.id, p]));

export const LAB_RANKING: RankedBullet[] = [
  { bulletId: 'b1',  rank: 1,  why: 'The JD opens with "latency-sensitive pricing APIs" — this is the only bullet with a measured before/after on exactly that.' },
  { bulletId: 'b2',  rank: 2,  why: 'Names idempotency and retry handling, both listed as must-haves.' },
  { bulletId: 'b9',  rank: 3,  why: 'Covers the vector-search requirement that nothing in your work history touches.' },
  { bulletId: 'b6',  rank: 4,  why: 'Closest thing to the "constraint solving" line in the JD.' },
  { bulletId: 'b4',  rank: 5,  why: 'API contract discipline; the JD mentions OpenAPI by name.' },
  { bulletId: 'b11', rank: 6,  why: 'Systems-level Rust, adjacent to the infrastructure half of the role.' },
  { bulletId: 'b7',  rank: 7,  why: 'Compliance exposure — a nice-to-have, not a requirement.' },
  { bulletId: 'b10', rank: 8,  why: 'Reinforces the Go experience already covered by rank 3.' },
  { bulletId: 'b3',  rank: 9,  why: 'Overlaps with rank 2 and carries no number.' },
  { bulletId: 'b5',  rank: 10, why: 'No measurable outcome; documentation rarely clears a screen.' },
  { bulletId: 'b8',  rank: 11, why: 'Frontend work, off-target for a backend role.' },
  { bulletId: 'b12', rank: 12, why: 'Too small to spend a line on.' },
];

export const LAB_VERDICTS: BulletVerdict[] = [
  { bulletId: 'b1',  verdict: 'keep', reason: 'Concrete number, named mechanism, matches the top JD requirement.' },
  { bulletId: 'b2',  verdict: 'keep', reason: 'Shows systems judgment and scope in one line.' },
  { bulletId: 'b9',  verdict: 'keep', reason: 'Only evidence of search infrastructure anywhere on the page.' },
  { bulletId: 'b6',  verdict: 'weak', reason: 'The figure has no baseline — nine points from what to what?' },
  { bulletId: 'b4',  verdict: 'keep', reason: 'Migration scope is clear and the error count is credible.' },
  { bulletId: 'b11', verdict: 'weak', reason: 'Reads as a hobby tool; no user or impact named.' },
  { bulletId: 'b7',  verdict: 'drop', reason: 'Compliance is not in the JD and this costs a full line.' },
];

const COVER_LETTER = [
  'Dear Northwind hiring team,',
  '',
  'Your posting leads with p99 latency on the pricing path, which is the problem I spent most of last year on at Atlas Freight. Replacing a per-request pricing scan with a webhook-refreshed rate table took our quote endpoint from 1.8s to 240ms at p99, and the idempotency layer I designed around carrier booking held through retry storms across twelve integrations.',
  '',
  'The vector search work in Tidepool is the closest I have come to your relevance-ranking stack: 50k articles in pgvector, sub-80ms semantic search, running on one small VPS.',
  '',
  'I would welcome the chance to talk.',
  '',
  'Placeholder Candidate',
].join('\n');

export const LAB_APP: ApplicationResponse = {
  id: 'app-northwind',
  company: 'Northwind Systems',
  role: 'Senior Backend Engineer',
  jdUrl: 'https://example.com/jobs/northwind-senior-backend',
  jdText: 'Northwind is hiring a senior backend engineer for our latency-sensitive pricing APIs.',
  roleEmphasis: 'backend / distributed systems',
  bulletRanking: JSON.stringify(LAB_RANKING),
  selectedBulletIds: ['b1', 'b2', 'b9', 'b6', 'b4', 'b11', 'b7'],
  lockedBulletIds: ['b1', 'b9'],
  coverLetter: COVER_LETTER,
  coverLetterFlags: ['twelve integrations'],
  atsMatched: ['postgres', 'idempotency', 'openapi', 'latency', 'go', 'webhooks', 'p99', 'rest', 'observability', 'ci/cd'],
  atsMissing: ['kafka', 'terraform', 'grpc', 'kubernetes', 'graphql'],
  fitScore: 84,
  fitVerdict: 'strong match',
  fitDimensions: { technical: 88, experience: 79, domain: 72 },
  fitStrengths: [
    'Direct, measured experience on latency-sensitive pricing APIs.',
    'Owns reliability primitives (idempotency, rate limiting) rather than just consuming them.',
    'Postgres depth including pgvector, which the JD lists as a plus.',
  ],
  fitGaps: [
    'No Kafka or streaming experience; the JD names it twice.',
    'No Kubernetes or Terraform anywhere in the bank.',
    'Team-lead scope is implied but never stated.',
  ],
  recruiterScore: 71,
  recruiterVerdict: 'would advance, with reservations',
  recruiterDimensions: { evidenceStrength: 74, relevanceDensity: 68, readability: 81 },
  recruiterBulletVerdicts: LAB_VERDICTS,
  recruiterWeaknesses: [
    'Two of seven bullets carry no number at all.',
    'Nothing on the page shows work at this scale — the largest figure is 50k articles.',
    'The compliance bullet spends a line on a requirement the JD never asks for.',
  ],
  recruiterThinnestRequirement: 'Streaming / event pipelines — the JD asks for Kafka twice and the page answers with webhooks.',
  recruiterWeakestBulletId: 'b7',
  recruiterStale: true,
  pageCount: 2,
  pdfAvailable: true,
  tectonicLog: null,
  outcome: 'interview',
  createdAt: '2026-09-02T14:10:00Z',
};
