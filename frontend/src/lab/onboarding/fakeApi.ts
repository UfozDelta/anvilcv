import { useSyncExternalStore } from 'react';
import { API_BASE, type ApplicationResponse, type Bullet, type GenerationConfig, type ParseResumeResponse, type Project, type Story } from '../../lib/api';

/**
 * An in-memory stand-in for the backend, so the REAL pages run in the lab. fetch is patched
 * while the lab is mounted; every route a page in the onboarding journey calls is answered
 * here from one small store. Nothing leaves the browser.
 */

export type Field = 'software' | 'mechanical';

type Profile = {
  name: string; phone: string; email: string; linkedinHandle: string; githubHandle: string; portfolioUrl: string | null;
  education: { school: string; location: string; degree: string; dates: string; coursework: string }[];
  skillsLanguages: string; skillsFrameworks: string; skillsDatabases: string; skillsDevops: string; skillsInterests: string;
};

type Seed = {
  parsed: ParseResumeResponse;
  bullets: Record<string, string[]>; // item name → drafted bullets
  jd: string;
};

const SEEDS: Record<Field, Seed> = {
  software: {
    parsed: {
      experiences: [
        { name: 'Northwind Labs', title: 'Backend Engineer', company: 'Northwind Labs', location: 'Remote', dates: '2023 – present', description: 'Order routing and batch pipelines for a logistics platform.' },
        { name: 'Halcyon AI', title: 'ML Intern', company: 'Halcyon AI', location: 'Austin, TX', dates: 'Summer 2022', description: 'Evaluation tooling for retrieval models.' },
      ],
      projects: [
        { name: 'orderflow', description: 'Event-sourced order service in Java, Kafka and Postgres.', dates: '2024' },
        { name: 'tinygrad-lens', description: 'GPU kernel profiler in Python and CUDA.', dates: '2023' },
      ],
    },
    bullets: {
      'Northwind Labs': [
        'Cut p95 order-routing latency from 840ms to 210ms by rebuilding the service on Spring Boot with event sourcing',
        'Moved three batch jobs onto Kafka consumers, removing a nightly four-hour reconciliation window',
        'Added Postgres partial indexes that cut checkout query time by 62%',
      ],
      'Halcyon AI': [
        'Built an eval harness that scored 40k retrieval queries per run, replacing a two-day manual review',
        'Found a tokenizer mismatch that was costing 6 points of recall and shipped the fix in one sprint',
      ],
      orderflow: ['Designed an event-sourced order service that held 1.2k orders/sec in load tests', 'Replayed 90 days of events to rebuild read models in under 4 minutes'],
      'tinygrad-lens': ['Profiled CUDA kernels to find a 31% occupancy loss, then fixed the launch config', 'Shipped a trace viewer that cut time-to-diagnosis from hours to minutes'],
    },
    jd: 'Stripe — Backend Engineer, Payments. Build low-latency services in Java and Kafka. You will own reliability for money-moving APIs and work with Postgres at scale.',
  },
  mechanical: {
    parsed: {
      experiences: [
        { name: 'Ridgeline Motors', title: 'Powertrain Design Intern', company: 'Ridgeline Motors', location: 'Fresno, CA', dates: 'Summer 2025', description: 'Transmission housing design and validation.' },
        { name: 'Formula SAE Team', title: 'Suspension Lead', company: 'State University Racing', location: 'Fresno, CA', dates: '2023 – 2025', description: 'Led a six-person suspension team through two competition seasons.' },
      ],
      projects: [
        { name: 'Press frame FEA', description: 'Sized a 50-ton hydraulic press frame in ANSYS.', dates: '2025' },
        { name: 'Heat exchanger rig', description: 'Thermal test rig with a LabVIEW DAQ.', dates: '2024' },
      ],
    },
    bullets: {
      'Ridgeline Motors': [
        'Cut bracket mass 18% by redesigning the mount in SolidWorks and verifying it with ANSYS static FEA',
        'Wrote a DFMEA covering 14 failure modes for the transmission housing, closing 9 actions before prototype build',
        'Held ±0.05 mm on machined housings by revising the fixture and GD&T callouts',
      ],
      'Formula SAE Team': [
        'Led a six-person suspension team; shipped a double-wishbone design that passed tech inspection first try',
        'Raised lateral grip 8% in testing by tuning spring and damper rates against track data',
      ],
      'Press frame FEA': ['Sized a 50-ton press frame in ANSYS to a safety factor of 2.1 at rated load', 'Specified weld details to AWS D1.1, cutting fabrication time 15%'],
      'Heat exchanger rig': ['Built a thermal test rig with 8 thermocouples and a LabVIEW DAQ at 0.5 K resolution', 'Validated the ε-NTU model to within 4% of measured effectiveness'],
    },
    jd: 'Tesla — Mechanical Design Engineer, Powertrain. Own housing and bracket design from CAD through FEA and DFMEA. Drive designs to manufacturing with tight GD&T.',
  },
};

const GENERIC: Record<Field, string[]> = {
  software: [
    'Built and shipped the core feature end to end, from schema to UI',
    'Cut build time 40% by caching dependencies in CI',
    'Wrote integration tests covering the main user flows',
  ],
  mechanical: [
    'Modeled the assembly in SolidWorks and checked clearances across every moving part',
    'Ran static FEA on the critical bracket and cut mass 12% at the same safety factor',
    'Wrote a test plan and validated the design against the spec on the bench',
  ],
};

type Job = { start: number; appId: string; lines: string[]; gen?: { projectId: string; applied: boolean } };

export type GuidePath = 'import' | 'manual' | 'skip';

type State = {
  field: Field;
  username: string;
  profile: Profile;
  projects: Project[];
  bullets: Bullet[];
  stories: (Story & { projectId: string })[];
  apps: ApplicationResponse[];
  jobs: Record<string, Job>;
  path: GuidePath;
  profileSaved: boolean;
  pdfViewed: boolean;
  tracked: boolean;
};

const blankProfile = (): Profile => ({
  name: '', phone: '', email: '', linkedinHandle: '', githubHandle: '', portfolioUrl: null, education: [],
  skillsLanguages: '', skillsFrameworks: '', skillsDatabases: '', skillsDevops: '', skillsInterests: '',
});

const fresh = (field: Field): State => ({
  field, username: 'mara', profile: blankProfile(), projects: [], bullets: [], stories: [], apps: [], jobs: {}, path: 'import',
  profileSaved: false, pdfViewed: false, tracked: false,
});

let S: State = fresh('software');
let version = 0;
const listeners = new Set<() => void>();
const emit = () => { version++; listeners.forEach((l) => l()); };

/** Re-render when the fake backend changes. Read the store with `fake()`. */
export function useFakeVersion() {
  return useSyncExternalStore((cb) => { listeners.add(cb); return () => { listeners.delete(cb); }; }, () => version);
}
export const fake = () => S;

export function resetFake(field: Field = 'software') { S = fresh(field); emit(); }
export function setField(field: Field) { S = { ...S, field }; emit(); }
export function setGuidePath(path: GuidePath) { S = { ...S, path }; emit(); }

export const progressOf = (s: State) => ({
  imported: s.projects.length > 0,
  profile: s.profileSaved,
  approved: s.bullets.filter((b) => b.status === 'APPROVED').length,
  tailored: s.apps.length > 0,
  pdfViewed: s.pdfViewed,
  tracked: s.tracked,
});

export const sampleJd = (field: Field) => SEEDS[field].jd;

export const sampleResume = (field: Field) => {
  const p = SEEDS[field].parsed;
  return [
    'MARA OKAFOR',
    '',
    'EXPERIENCE',
    ...p.experiences.flatMap((e) => [`${e.title}, ${e.company} — ${e.dates}`, ...SEEDS[field].bullets[e.name].map((b) => `• ${b}`), '']),
    'PROJECTS',
    ...p.projects.flatMap((x) => [`${x.name} — ${x.description}`, ...SEEDS[field].bullets[x.name].map((b) => `• ${b}`), '']),
  ].join('\n');
};

// ── helpers ──
const now = () => new Date().toISOString();
const uid = (p: string) => `${p}-${Math.random().toString(36).slice(2, 8)}`;

/** Draft one story per text under a project, as the generate job would. */
function draft(id: string, texts: string[]) {
  const stories = texts.map((t, i) => ({
    id: `${id}-s${S.stories.filter((x) => x.projectId === id).length + i}`, projectId: id, title: t.split(' ').slice(0, 5).join(' '), evidence: [], lenses: ['general'], createdAt: now(),
  }));
  const bullets: Bullet[] = texts.map((t, i) => ({
    id: `${stories[i].id}-b`, projectId: id, text: t, tags: [], category: 'general', status: 'PENDING', createdAt: now(), updatedAt: now(),
    storyId: stories[i].id, storyTitle: stories[i].title, angle: null, judgeNote: null,
  }));
  S = { ...S, stories: [...S.stories, ...stories], bullets: [...S.bullets, ...bullets] };
}

/** `seed`: a résumé import drafts bullets at once; a hand-made entry waits for Generate. */
function addItem(kind: 'PROJECT' | 'EXPERIENCE', item: Partial<Project> & { name: string }, seed = true) {
  const id = uid(kind === 'PROJECT' ? 'p' : 'e');
  const project: Project = {
    id, kind, description: '', createdAt: now(), updatedAt: now(), techTerms: [], bulletCount: 0, ...item,
  } as Project;
  S = { ...S, projects: [...S.projects, project] };
  if (seed) draft(id, SEEDS[S.field].bullets[item.name] ?? []);
}

const withCount = (p: Project): Project => ({ ...p, bulletCount: S.bullets.filter((b) => b.projectId === p.id && b.status !== 'REJECTED').length });

const CFG: GenerationConfig = {
  wordFilterEnabled: true, singleLineLow: 90, singleLineHigh: 112, doubleLineLow: 160, doubleLineHigh: 215,
  deadZoneLow: 113, deadZoneHigh: 159, minWordFloor: 8, temperature: 0.4, boldDensity: 'LIGHT', tone: 'NEUTRAL', actionVerbStyle: 'TECHNICAL',
};

const JOB_LINES = (company: string) => [
  'Reading the posting…', `Role: ${company}`, 'Ranking your approved bullets against the posting…', 'kept: 4 bullets across 2 entries',
  'ATS matched: 6 of 9 keywords', 'Rendering one page…', 'saved resume.pdf (1 page)', 'done - ready',
];

function createApp(body: { jdText?: string; includeCoverLetter?: boolean; roleEmphasis?: string }): string {
  const jd = body.jdText || SEEDS[S.field].jd;
  const m = jd.match(/^(.+?) — (.+?)\./);
  const company = m?.[1] ?? 'Acme';
  const role = m?.[2] ?? 'Engineer';
  const pool = S.bullets.filter((b) => b.status === 'APPROVED');
  const use = (pool.length ? pool : S.bullets).slice(0, 8);
  const id = uid('a');
  const app: ApplicationResponse = {
    id, company, role, jdText: jd, jdUrl: null, roleEmphasis: body.roleEmphasis ?? '',
    bulletRanking: JSON.stringify(use.map((b, i) => ({ bulletId: b.id, rank: i + 1, why: 'Matches the posting’s core requirement.' }))),
    selectedBulletIds: use.map((b) => b.id), lockedBulletIds: [],
    coverLetter: body.includeCoverLetter ? `Dear ${company} team,\n\n${use[0]?.text ?? ''}. That is the kind of work this ${role} role needs, and I would like to bring it to your team.\n\nMara Okafor` : null,
    coverLetterFlags: [], atsMatched: ['Java', 'Kafka', 'Postgres'], atsMissing: ['Go'],
    fitScore: 82, fitVerdict: 'Strong fit on the core requirements.', fitDimensions: { skills: 84, experience: 80 }, fitStrengths: ['Direct match on the core stack'], fitGaps: ['No Go yet'],
    recruiterScore: 78, recruiterVerdict: 'Clear, scannable page.', recruiterDimensions: {}, recruiterBulletVerdicts: [], recruiterWeaknesses: [],
    recruiterThinnestRequirement: null, recruiterWeakestBulletId: null, recruiterStale: false, recruiterPending: false,
    pageCount: 1, pdfAvailable: true, pdfStale: false, tectonicLog: null, outcome: '', createdAt: now(),
  };
  S = { ...S, apps: [app, ...S.apps] };
  return id;
}

function fakePdf(): Blob {
  const lines = [S.profile.name || 'Mara Okafor', ...S.bullets.filter((b) => b.status === 'APPROVED').slice(0, 8).map((b) => `- ${b.text}`.slice(0, 92))];
  const esc = (t: string) => t.replace(/[\\()]/g, '\\$&');
  const content = `BT /F1 11 Tf 56 740 Td 16 TL ${lines.map((l) => `(${esc(l)}) '`).join(' ')} ET`;
  const pdf = `%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n4 0 obj<</Length ${content.length}>>stream\n${content}\nendstream endobj\n5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R/Size 6>>\n%%EOF`;
  return new Blob([pdf], { type: 'application/pdf' });
}

// ── the router ──
type Reply = { status?: number; json?: unknown; blob?: Blob };

function route(method: string, path: string, query: URLSearchParams, body: any): Reply {
  let m: RegExpMatchArray | null;
  if (path === '/api/me') return { json: { username: S.username, isAdmin: false } };
  if (path === '/api/profile') {
    if (method === 'PUT') { S = { ...S, profile: { ...S.profile, ...body }, profileSaved: true }; emit(); }
    return { json: S.profile };
  }
  if (path === '/api/config/generation') return { json: CFG };
  if (path === '/api/resume/parse') return { json: SEEDS[S.field].parsed };
  if (path === '/api/resume/import') {
    for (const it of body.items as { kind: 'PROJECT' | 'EXPERIENCE'; name: string }[]) addItem(it.kind, it as any);
    emit();
    return { json: { imported: body.items.length } };
  }
  if (path === '/api/projects') {
    if (method === 'POST') { addItem(body.kind, body, false); emit(); return { json: S.projects[S.projects.length - 1] }; }
    const kind = query.get('kind');
    return { json: S.projects.filter((p) => !kind || p.kind === kind).map(withCount) };
  }
  if ((m = path.match(/^\/api\/projects\/([^/]+)$/))) {
    const p = S.projects.find((x) => x.id === m![1]);
    if (!p) return { status: 404, json: { message: 'Not found' } };
    if (method === 'PUT') { S = { ...S, projects: S.projects.map((x) => (x.id === p.id ? { ...x, ...body, updatedAt: now() } : x)) }; emit(); return { json: p }; }
    if (method === 'DELETE') { S = { ...S, projects: S.projects.filter((x) => x.id !== p.id) }; emit(); return { status: 204 }; }
    return { json: { ...withCount(p), usableStories: S.stories.filter((s) => s.projectId === p.id).length } };
  }
  if ((m = path.match(/^\/api\/projects\/([^/]+)\/stories\/submit$/))) {
    const jobId = uid('g');
    S = { ...S, jobs: { ...S.jobs, [jobId]: { start: Date.now(), appId: '', lines: ['Reading your description…', 'Finding stories…', 'Drafting wordings…', 'done - stories ready'], gen: { projectId: m[1], applied: false } } } };
    return { json: { jobId } };
  }
  if ((m = path.match(/^\/api\/projects\/([^/]+)\/stories\/([^/]+)\/wordings\/submit$/))) {
    const jobId = uid('g');
    S = { ...S, jobs: { ...S.jobs, [jobId]: { start: Date.now(), appId: '', lines: ['Writing wordings…', 'done - wordings ready'], gen: { projectId: m[1], applied: true } } } };
    return { json: { jobId } };
  }
  if ((m = path.match(/^\/api\/projects\/jobs\/([^/]+)\/progress$/))) {
    const j = S.jobs[m[1]];
    if (!j) return { status: 404, json: {} };
    const n = Math.min(j.lines.length, Math.floor((Date.now() - j.start) / 600));
    const done = n >= j.lines.length;
    if (done && j.gen && !j.gen.applied) {
      j.gen.applied = true;
      const item = S.projects.find((x) => x.id === j.gen!.projectId);
      if (item) draft(item.id, SEEDS[S.field].bullets[item.name] ?? GENERIC[S.field]);
      emit();
    }
    return { json: { status: done ? 'DONE' : 'RUNNING', lines: j.lines.slice(0, n), appId: '' } };
  }
  if ((m = path.match(/^\/api\/projects\/([^/]+)\/bullets$/)) && method === 'POST') {
    draft(m[1], [body.text]);
    emit();
    return { json: S.bullets[S.bullets.length - 1] };
  }
  if ((m = path.match(/^\/api\/projects\/([^/]+)\/stories$/))) return { json: { cap: 12, stories: S.stories.filter((s) => s.projectId === m![1]) } };
  if ((m = path.match(/^\/api\/projects\/([^/]+)\/bullets$/))) return { json: S.bullets.filter((b) => b.projectId === m![1]) };
  if ((m = path.match(/^\/api\/bullets\/([^/]+)\/status$/))) {
    S = { ...S, bullets: S.bullets.map((b) => (b.id === m![1] ? { ...b, status: body.status, updatedAt: now() } : b)) };
    emit();
    return { json: S.bullets.find((b) => b.id === m![1]) };
  }
  if ((m = path.match(/^\/api\/bullets\/([^/]+)$/)) && method === 'PUT') {
    S = { ...S, bullets: S.bullets.map((b) => (b.id === m![1] ? { ...b, ...body } : b)) };
    emit();
    return { json: S.bullets.find((b) => b.id === m![1]) };
  }
  if (path === '/api/bullets/preview') return { blob: fakePdf() };
  if (path === '/api/applications') return { json: S.apps.map(({ id, company, role, outcome, createdAt, fitScore, recruiterScore }) => ({ id, company, role, outcome, createdAt, fitScore, recruiterScore })) };
  if (path === '/api/applications/submit') {
    const id = createApp(body);
    const jobId = uid('j');
    S = { ...S, apps: S.apps.map((a) => a), jobs: { ...S.jobs, [jobId]: { start: Date.now(), appId: id, lines: JOB_LINES(S.apps[0].company ?? '') } } };
    // The app exists at once in the store but the list should not show it until the job finishes.
    return { json: { jobId } };
  }
  if ((m = path.match(/^\/api\/applications\/jobs\/([^/]+)\/progress$/))) {
    const j = S.jobs[m[1]];
    if (!j) return { status: 404, json: {} };
    const n = Math.min(j.lines.length, Math.floor((Date.now() - j.start) / 450));
    const done = n >= j.lines.length;
    if (done) emit();
    return { json: { status: done ? 'DONE' : 'RUNNING', lines: j.lines.slice(0, n), appId: j.appId } };
  }
  if (path === '/api/applications/selection-check') return { json: [] };
  if ((m = path.match(/^\/api\/applications\/([^/]+)\/pdf$/))) { S = { ...S, pdfViewed: true }; emit(); return { blob: fakePdf() }; }
  if ((m = path.match(/^\/api\/applications\/([^/]+)$/))) {
    const a = S.apps.find((x) => x.id === m![1]);
    if (!a) return { status: 404, json: { message: 'Not found' } };
    if (method === 'PATCH') {
      S = { ...S, apps: S.apps.map((x) => (x.id === a.id ? { ...x, ...body } : x)), tracked: S.tracked || !!body.outcome };
      emit();
      return { json: { ...a, ...body } };
    }
    return { json: a };
  }
  return { status: 404, json: { message: `No fake route for ${method} ${path}` } };
}

/** Patch window.fetch for the lab's lifetime. Returns the restore function. */
export function installFakeApi(): () => void {
  const real = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    if (!url.startsWith(API_BASE)) return real(input, init);
    const u = new URL(url);
    let body: unknown;
    try { body = init?.body ? JSON.parse(String(init.body)) : undefined; } catch { body = undefined; }
    await new Promise((r) => setTimeout(r, 90));
    const r = route((init?.method ?? 'GET').toUpperCase(), u.pathname, u.searchParams, body);
    if (r.blob) return new Response(r.blob, { status: r.status ?? 200, headers: { 'content-type': 'application/pdf' } });
    if (r.status === 204) return new Response(null, { status: 204 });
    return new Response(JSON.stringify(r.json ?? {}), { status: r.status ?? 200, headers: { 'content-type': 'application/json' } });
  };
  return () => { window.fetch = real; };
}
