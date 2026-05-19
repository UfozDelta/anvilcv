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
  pdfUrl: (path: string) => `${BASE}${path}`,
};

// ---------- types mirroring backend DTOs ----------

export type ProjectKind = 'PROJECT' | 'EXPERIENCE';

export interface Project {
  id: string;
  kind: ProjectKind;
  name: string;
  description: string;
  sourcePath?: string | null;
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
  createdAt: string;
  updatedAt: string;
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

export interface ApplicationResponse {
  id: string;
  company: string | null;
  role: string | null;
  jdText: string;
  jdUrl: string | null;
  roleEmphasis: string;
  bulletRanking: string; // JSON string of RankedBullet[]
  selectedBulletIds: string[];
  coverLetter: string | null;
  atsMatched: string[];
  atsMissing: string[];
  pdfAvailable: boolean;
  tectonicLog: string | null;
  outcome: string;
  createdAt: string;
}
