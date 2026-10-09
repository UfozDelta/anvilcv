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
  /** Long-form architecture/role overview used as generation context — filled in later via
   *  Info & Context, separate from the short `description` shown in list rows. Lab-only for now. */
  contextDescription?: string | null;
  githubUrl?: string | null;
  repoContextReady?: boolean;
  /** Set when linked through the GitHub App — the branch and the commit every repo read is pinned to. */
  repoBranch?: string | null;
  repoCommitSha?: string | null;
  techStack?: string | null;
  /** Canonical tech names matched out of `techStack` server-side (see TechStackSummary.java) — use this for anything that needs discrete tokens instead of re-parsing the prose. */
  techTerms?: string[];
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
  /** "I currently work here" (experiences). */
  current?: boolean;
  createdAt: string;
  /** Last edit; absent on a just-created project (the API returns it only once the row is re-read). */
  updatedAt?: string | null;
  /** Bullets in this project's bank (the list endpoint counts them in one grouped query). */
  bulletCount?: number;
  /** Distinct stories a resume can draw on; only GET /api/projects/{id} fills it. */
  usableStories?: number | null;
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
  /** Wordings of one story share it; null for a bullet of its own. */
  storyId?: string | null;
  /** outcome, decision, scale or failure: the angle the story bank gave this wording. Null when none. */
  angle?: string | null;
  /** Only the project's bullet list fills it. */
  storyTitle?: string | null;
}

/** A live story from GET /api/projects/{id}/stories: its evidence quotes and the lenses it carries. */
export interface Story {
  id: string;
  title: string;
  evidence: string[];
  lenses: string[];
  createdAt: string;
}

export interface StoriesResponse {
  cap: number;
  stories: Story[];
}

export interface GithubStatus {
  configured: boolean;
  connected: boolean;
  account: string | null;
  manageUrl: string | null;
}

export interface GithubRepo {
  fullName: string;
  isPrivate: boolean;
  defaultBranch: string;
  description: string;
  pushedAt: string;
}

export interface RepoTree {
  entries: { path: string; size: number }[];
  truncated: boolean;
}

/** Hierarchical repo map for the pinned commit — see RepoMap.java. */
export interface RepoMap {
  sha: string;
  facts: { label: string; value: string; source: string }[];
  modules: {
    path: string; files: number; loc: number; languages: string[]; rank: number;
    symbols: string[]; routes: string[]; dependsOn: string[]; topFile: string;
    summary: string | null; purpose: string | null;
  }[];
  project: {
    overview: string;
    audience: string;
    subsystems: { name: string; purpose: string; lenses: string[]; modules: string[] }[];
    flows: { name: string; steps: string[] }[];
  } | null;
}

/** One evidence span a bullet traces to — a file line range, or a commit. */
export interface BulletSource {
  path: string | null;
  startLine: number;
  endLine: number;
  commit: string | null;
  field: string;
  claim: string;
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
  { slug: 'ai-ml',   label: 'AI / ML',         blurb: 'RAG, agents, embeddings, prompt design' },
  { slug: 'backend', label: 'Backend',         blurb: 'APIs, schemas, indexes, migrations' },
  { slug: 'data',    label: 'Data Engineering', blurb: 'ingestion, parsers, ETL, geospatial' },
  { slug: 'general', label: 'General',         blurb: 'everything else: product, tooling, ops' },
];

/** A bullet that repeats `conflictId`, earlier on the same page (POST /api/applications/selection-check). */
export interface SelectionWarning {
  bulletId: string;
  conflictId: string;
  reason: 'same story' | 'near-duplicate';
}

export interface RankedBullet {
  bulletId: string;
  rank: number;
  why: string;
}

/** A posting from the public intern-job feed (GET /api/public/jobs). */
export interface JobPosting {
  id: string;
  source: string;
  title: string | null;
  company: string | null;
  location: string | null;
  posted: string | null;
  spotted: string | null;
  url: string;
  companyUrl: string | null;
  role: string | null;
  stack: string[];
  receivedAt: string;
  /** Always false for guests. */
  saved: boolean;
  /** Stack tags the signed-in user's profile skills cover; empty for guests. */
  matched: string[];
}

/** Totals under the current search filters; `saved` is 0 for guests. */
export interface JobCounts { linkedin: number; indeed: number; saved: number }

export interface JobList { jobs: JobPosting[]; total: number; counts?: JobCounts }

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
  /** Recruiter pass still running in the background after create; poll until false. */
  recruiterPending: boolean;
  /** Pages in the compiled PDF, from tectonic's log. Null when unknown. */
  pageCount: number | null;
  pdfAvailable: boolean;
  /** Something the PDF prints was edited (here or on another page) since it was compiled. */
  pdfStale: boolean;
  tectonicLog: string | null;
  outcome: string;
  createdAt: string;
}
