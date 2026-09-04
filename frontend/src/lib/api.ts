const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

// Exported so SSE callers (useEventLog) can prepend the same base URL.
export const API_BASE = BASE;

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: unknown) {
    super(message);
  }
}

export class UnauthorizedError extends ApiError {
  constructor() { super(401, 'Unauthorized'); }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
    ...init,
  });

  if (res.status === 401) throw new UnauthorizedError();

  if (!res.ok) {
    let body: any = undefined;
    try { body = await res.json(); } catch { /* ignore */ }
    const msg = body?.message
      ? `${body.message}${body.hint ? ` — ${body.hint}` : ''}`
      : `${res.status} ${res.statusText}`;
    throw new ApiError(res.status, msg, body);
  }

  if (res.status === 204) return undefined as T;

  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) return res.json() as Promise<T>;
  return (await res.text()) as unknown as T;
}

export const api = {
  get:   <T>(p: string) => request<T>(p),
  post:  <T>(p: string, body?: unknown) => request<T>(p, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put:   <T>(p: string, body?: unknown) => request<T>(p, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(p: string, body?: unknown) => request<T>(p, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  del:   <T>(p: string) => request<T>(p, { method: 'DELETE' }),
  parseResume: (text: string) => request<ParseResumeResponse>('/api/resume/parse', { method: 'POST', body: JSON.stringify({ text }) }),
  pdfUrl: (path: string) => `${BASE}${path}`,
  fetchRaw: (path: string) => fetch(`${BASE}${path}`, { credentials: 'include' }),
  // Raw POST for endpoints that answer with a blob (PDF preview). Mirrors request()'s
  // 401 handling so an expired session still routes to re-login instead of surfacing
  // an empty error body.
  postRaw: async (path: string, body: unknown) => {
    const res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (res.status === 401) throw new UnauthorizedError();
    return res;
  },
};

// ---------- types mirroring backend DTOs ----------

export type ProjectKind = 'PROJECT' | 'EXPERIENCE';

export interface Project {
  id: string;
  kind: ProjectKind;
  name: string;
  description: string;
  githubUrl?: string | null;
  repoContextReady?: boolean;
  techStack?: string | null;
  yourRole?: string | null;
  ownership?: string | null;
  scaleImpact?: string | null;
  hardestProblem?: string | null;
  technicalDecisions?: string | null;
  userImpact?: string | null;
  securityPosture?: string | null;
  title?: string | null;
  company?: string | null;
  location?: string | null;
  dates?: string | null;
  createdAt: string;
}

export interface Bullet {
  id: string;
  projectId: string;
  text: string;
  tags: string[];
  category: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  updatedAt: string;
}

/** Result of POST /api/projects/{id}/bullets/refit — `bullets` is the project's full bank after the pass. */
export interface RefitResponse {
  checked: number;
  offBand: number;
  rewritten: number;
  unchanged: number;
  bullets: Bullet[];
}

export const CATEGORIES: { slug: string; label: string; blurb: string }[] = [
  { slug: 'ai-ml',    label: 'AI / ML',           blurb: 'RAG, agents, embeddings, prompt design' },
  { slug: 'backend',  label: 'Backend & Data',    blurb: 'APIs, schemas, indexes, migrations' },
  { slug: 'frontend', label: 'Frontend & Product',blurb: 'design systems, state, viz, mobile' },
  { slug: 'data',     label: 'Data Engineering',  blurb: 'ingestion, parsers, ETL, geospatial' },
  { slug: 'security', label: 'Security & Auth',   blurb: 'RBAC, encryption, compliance' },
  { slug: 'devops',   label: 'Infra & DevOps',    blurb: 'CI/CD, monorepos, deploys' },
  { slug: 'systems',  label: 'Distributed Systems', blurb: 'async, idempotency, real-time' },
  { slug: 'comms',    label: 'Real-time Comms',   blurb: 'WebRTC, telephony, SMS, webhooks' },
];

export interface RankedBullet {
  bulletId: string;
  rank: number;
  why: string;
}

export interface ApplicationSummary {
  id: string;
  company: string | null;
  role: string | null;
  outcome: string;
  createdAt: string;
  /** Overall fit 0-100, or null for applications generated before scoring existed. */
  fitScore: number | null;
  /** Recruiter pass on the rendered page 0-100 — a different question from fitScore. */
  recruiterScore: number | null;
}

export interface OutcomeHistoryEntry {
  applicationId: string;
  outcome: string;
  changedAt: string;
}

export type BoldDensity = 'NONE' | 'LIGHT' | 'HEAVY';
export type Tone = 'CONSERVATIVE' | 'NEUTRAL' | 'AGGRESSIVE';
export type ActionVerbStyle = 'TECHNICAL' | 'LEADERSHIP' | 'IMPACT';

export interface GenerationConfig {
  wordFilterEnabled: boolean;
  singleLineLow: number;
  singleLineHigh: number;
  doubleLineLow: number;
  doubleLineHigh: number;
  deadZoneLow: number;
  deadZoneHigh: number;
  minWordFloor: number;
  temperature: number;
  boldDensity: BoldDensity;
  tone: Tone;
  actionVerbStyle: ActionVerbStyle;
}

export interface ParsedExperience {
  name: string;
  title: string | null;
  company: string | null;
  location: string | null;
  dates: string | null;
  description: string;
}

export interface ParsedProject {
  name: string;
  description: string;
  dates: string | null;
}

export interface ParseResumeResponse {
  experiences: ParsedExperience[];
  projects: ParsedProject[];
}

/** One recruiter verdict on one rendered bullet. */
export interface BulletVerdict {
  bulletId: string;
  verdict: 'keep' | 'weak' | 'drop';
  reason: string;
}

export interface ApplicationResponse {
  id: string;
  company: string | null;
  role: string | null;
  jdText: string;
  jdUrl: string | null;
  roleEmphasis: string;
  bulletRanking: string; // JSON string of RankedBullet[]
  selectedBulletIds: string[];
  lockedBulletIds: string[];
  coverLetter: string | null;
  /** Figures the letter states that are in neither the selected bullets nor the JD. */
  coverLetterFlags: string[];
  atsMatched: string[];
  atsMissing: string[];
  /** Overall fit 0-100, or null when the scoring call failed / predates the feature. */
  fitScore: number | null;
  fitVerdict: string | null;
  fitDimensions: Record<string, number>;
  fitStrengths: string[];
  fitGaps: string[];
  /**
   * Recruiter pass on the RENDERED page 0-100 — grades the resume, not the candidate.
   * Null when the call failed / timed out / predates the feature.
   */
  recruiterScore: number | null;
  recruiterVerdict: string | null;
  recruiterDimensions: Record<string, number>;
  recruiterBulletVerdicts: BulletVerdict[];
  /** The forced-negative half: the call cannot decline to name these. */
  recruiterWeaknesses: string[];
  recruiterThinnestRequirement: string | null;
  recruiterWeakestBulletId: string | null;
  /** True when the selection was hand-edited after the recruiter pass ran. */
  recruiterStale: boolean;
  /** Pages in the compiled PDF, from tectonic's log. Null when unknown. */
  pageCount: number | null;
  pdfAvailable: boolean;
  tectonicLog: string | null;
  outcome: string;
  createdAt: string;
}
