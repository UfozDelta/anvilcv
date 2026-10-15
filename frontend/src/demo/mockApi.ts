/**
 * An in-memory stand-in for the backend, installed only when the app is opened with `?__demo=1`
 * (the /lab/walkthrough iframe). The real pages run unchanged against it, so the walkthrough shows
 * the shipped UI, not a copy. Nothing here reaches the network or survives a reload.
 */
import { CATEGORIES } from '../lib/api';

interface Bul { id: string; projectId: string; text: string; tags: string[]; category: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string; updatedAt: string; storyId: string; angle: string | null; judgeNote: string | null }
interface Proj { id: string; kind: 'PROJECT' | 'EXPERIENCE'; name: string; description: string; title?: string | null; company?: string | null; location?: string | null; dates?: string | null; techStack?: string | null; techTerms?: string[]; createdAt: string }
interface Story { id: string; projectId: string; title: string; evidence: string[]; lenses: string[]; createdAt: string }

const now = () => new Date().toISOString();
let seq = 0;
const uid = (p: string) => `${p}-${++seq}`;

const PARSED = {
  experiences: [{
    name: 'Northwind Robotics — Backend Intern', title: 'Backend Engineering Intern', company: 'Northwind Robotics',
    location: 'Austin, TX', dates: 'Jun 2025 – Aug 2025',
    bullets: [
      'Built a Kafka-fed telemetry ingestion service in Spring Boot, sustaining 4,000 messages per second across 12 partitions',
      'Reworked Postgres indexes behind the fleet dashboard, cutting p95 query latency from 1.8 seconds to 240 milliseconds',
      'Wrote the team on-call runbook covering 14 alerts, used to resolve two overnight incidents',
    ],
    get description() { return this.bullets.join('\n'); },
  }],
  projects: [{
    name: 'Anvil Pipeline', dates: '2025',
    bullets: [
      'Built a Go test sharder that splits suites by recorded runtime across containers, cutting a 22 minute CI run to 6 minutes',
      'Added flaky-test detection that reruns failures and quarantines repeat offenders',
    ],
    get description() { return this.bullets.join('\n'); },
  }],
};

const BANK: { proj: 'exp' | 'proj'; title: string; evidence: string[]; lenses: string[]; words: { c: string; t: string }[] }[] = [
  { proj: 'exp', title: 'Telemetry ingestion pipeline', lenses: ['backend', 'data'], evidence: ['Kafka consumer group, 12 partitions', 'Batch insert path in TelemetryWriter'],
    words: [
      { c: 'backend', t: 'Built a **Kafka**-fed telemetry ingestion service in **Spring Boot**, sustaining 4,000 messages per second across 12 partitions with batched Postgres writes.' },
      { c: 'data', t: 'Designed an ingestion path that batches and deduplicates robot telemetry, cutting storage writes by 38% while keeping every sensor reading queryable.' },
    ] },
  { proj: 'exp', title: 'Query latency rework', lenses: ['backend'], evidence: ['Composite index on (robot_id, ts)', 'EXPLAIN before and after'],
    words: [
      { c: 'backend', t: 'Reworked **Postgres** indexes behind the fleet dashboard, dropping p95 query latency from 1.8 seconds to 240 milliseconds on a 90 million row table.' },
      { c: 'general', t: 'Profiled the slowest dashboard queries, then shipped a composite index that made the fleet view load in under a quarter second.' },
    ] },
  { proj: 'exp', title: 'On-call runbook', lenses: ['general'], evidence: ['Runbook covers 14 alerts', 'Used in two incidents'],
    words: [
      { c: 'general', t: 'Wrote the team on-call runbook covering 14 alerts, which two engineers used to resolve overnight incidents without escalating to the owner.' },
    ] },
  { proj: 'proj', title: 'Test sharding', lenses: ['backend', 'general'], evidence: ['Shard planner in shard.go', 'Timing history in SQLite'],
    words: [
      { c: 'backend', t: 'Built a **Go** test sharder that splits suites by recorded runtime across containers, cutting a 22 minute CI run to 6 minutes.' },
    ] },
  { proj: 'proj', title: 'Flaky test detection', lenses: ['general'], evidence: ['Rerun policy in flaky.go'],
    words: [
      { c: 'general', t: 'Added flaky-test detection that reruns failures and quarantines repeat offenders, removing 31 false CI failures in the first month.' },
    ] },
];

const JOBS = [
  { id: 'j1', company: 'Northwind Robotics', title: 'Backend Engineer Intern', location: 'Austin, TX', role: 'Build the services that ingest telemetry from the robot fleet.', stack: ['Java', 'Spring Boot', 'Kafka', 'PostgreSQL'] },
  { id: 'j2', company: 'Lumen Health', title: 'Software Engineering Intern', location: 'Remote', role: 'Work on patient scheduling APIs.', stack: ['TypeScript', 'Node', 'PostgreSQL'] },
  { id: 'j3', company: 'Harbor Freight Tech', title: 'Platform Intern', location: 'Chicago, IL', role: 'Improve CI and deploy tooling.', stack: ['Go', 'Docker', 'Kubernetes'] },
  { id: 'j4', company: 'Cobalt Analytics', title: 'Data Engineering Intern', location: 'New York, NY', role: 'Own pieces of the ingestion pipeline.', stack: ['Python', 'Kafka', 'Airflow'] },
].map(j => ({ ...j, source: 'linkedin', posted: '2 days ago', spotted: null, url: `https://jobs.example.com/${j.id}`, companyUrl: null, receivedAt: new Date(Date.now() - 86_400_000).toISOString(), saved: false, matched: [] as string[] }));

const JOB_LINES = [
  'Fetching posting…',
  'Reading the job description: Backend Engineer Intern, Northwind Robotics',
  'Lens: backend (inferred from the posting)',
  'Ranking your approved bullets against 14 requirements…',
  'kept: Built a Kafka-fed telemetry ingestion service…',
  'kept: Reworked Postgres indexes behind the fleet dashboard…',
  'cut: Wrote the team on-call runbook… (over the one-page budget)',
  'Selection complete: 6 bullets, 28 of 31 lines',
  'Compiling one-page PDF…',
  'ATS matched: Kafka, Spring Boot, Postgres, Docker',
  'ATS missing: Kubernetes',
  'Done - saved application',
];

export function installMockApi() {
  let user: { username: string; email: string } | null = null;
  let field = 'software';
  let onboarded = false;
  let downloaded = false;
  const profile = { name: '', phone: '', email: '', linkedinHandle: '', githubHandle: '', portfolioUrl: null as string | null, education: [] as unknown[], skillsLanguages: '', skillsFrameworks: '', skillsDatabases: '', skillsDevops: '', skillsInterests: '' };
  const projects: Proj[] = [];
  const stories: Story[] = [];
  const bullets: Bul[] = [];
  const apps: { id: string; company: string; role: string; jdText: string; createdAt: string }[] = [];
  const jobs = new Map<string, { startedAt: number; appId: string }>();

  const preset = () => ({ field, name: field, lenses: CATEGORIES, skillLabels: [] as string[] });
  const fieldView = () => ({ field, preset: preset(), available: [] });
  const bulletCount = (id: string) => bullets.filter(b => b.projectId === id && b.status !== 'REJECTED').length;
  const withCount = (p: Proj) => ({ ...p, bulletCount: bulletCount(p.id) });

  /** An imported line is the user's own word, so it arrives approved, in a story of its own. */
  function importLines(p: Proj, lines: string[]) {
    for (const text of lines) {
      const s: Story = { id: uid('s'), projectId: p.id, title: text.split(' ').slice(0, 6).join(' '), evidence: [text], lenses: ['general'], createdAt: now() };
      stories.push(s);
      bullets.push({ id: uid('b'), projectId: p.id, text, tags: [], category: 'general', status: 'APPROVED', createdAt: now(), updatedAt: now(), storyId: s.id, angle: null, judgeNote: null });
    }
  }

  function importItems(items: (Proj & { bullets?: string[] })[]) {
    for (const it of items) {
      const { bullets: lines, ...rest } = it;
      const p: Proj = { ...rest, id: uid('p'), createdAt: now() };
      projects.push(p);
      if (lines) { importLines(p, lines); continue; }
      const kind = p.kind === 'EXPERIENCE' ? 'exp' : 'proj';
      for (const g of BANK.filter(b => b.proj === kind)) {
        const s: Story = { id: uid('s'), projectId: p.id, title: g.title, evidence: g.evidence, lenses: g.lenses, createdAt: now() };
        stories.push(s);
        for (const w of g.words) {
          bullets.push({ id: uid('b'), projectId: p.id, text: w.t, tags: [], category: w.c, status: 'PENDING', createdAt: now(), updatedAt: now(), storyId: s.id, angle: null, judgeNote: null });
        }
      }
    }
  }

  function application(id: string) {
    const a = apps.find(x => x.id === id)!;
    const live = bullets.filter(b => b.status === 'APPROVED');
    const ranking = live.map((b, i) => ({ bulletId: b.id, rank: i + 1, why: i < 2 ? 'Matches the Kafka and Postgres requirements.' : 'Relevant backend experience.' }));
    return {
      id: a.id, company: a.company, role: a.role, jdText: a.jdText, jdUrl: null, roleEmphasis: 'backend',
      bulletRanking: JSON.stringify(ranking), selectedBulletIds: live.map(b => b.id), lockedBulletIds: [],
      coverLetter: null, coverLetterFlags: [], atsMatched: ['Kafka', 'Spring Boot', 'Postgres', 'Docker'], atsMissing: ['Kubernetes'],
      fitScore: 84, fitVerdict: 'Strong fit. Kubernetes is the one gap.', fitDimensions: { skills: 86, experience: 80, impact: 85 },
      fitStrengths: ['Kafka ingestion at 4,000 msg/s', 'Postgres latency work with numbers'], fitGaps: ['No Kubernetes'],
      recruiterScore: 81, recruiterVerdict: 'Clean one page; the strongest bullets lead.', recruiterDimensions: { clarity: 82, impact: 80 },
      recruiterBulletVerdicts: [], recruiterWeaknesses: ['Only one internship'], recruiterThinnestRequirement: 'Kubernetes', recruiterWeakestBulletId: null,
      recruiterStale: false, recruiterPending: false, pageCount: 1, pdfAvailable: true, pdfStale: false, tectonicLog: null,
      outcome: 'APPLIED', createdAt: a.createdAt,
    };
  }

  function pdf(): Blob {
    const live = bullets.filter(b => b.status === 'APPROVED').slice(0, 6);
    const clean = (s: string) => s.replace(/\*\*/g, '').replace(/[^\x20-\x7e]/g, '-').replace(/([()\\])/g, '\\$1');
    const wrap = (s: string, n: number) => {
      const out: string[] = []; let line = '';
      for (const w of s.split(' ')) { if ((line + ' ' + w).trim().length > n) { out.push(line); line = w; } else line = (line + ' ' + w).trim(); }
      return line ? [...out, line] : out;
    };
    const ops: string[] = [];
    let y = 740;
    const text = (s: string, size: number, x = 54, font = 'F1') => { ops.push(`BT /${font} ${size} Tf ${x} ${y} Td (${clean(s)}) Tj ET`); y -= size + 5; };
    text(profile.name || 'Maya Chen', 20, 54, 'F2');
    text([profile.email, profile.phone, profile.githubHandle && `github.com/${profile.githubHandle}`].filter(Boolean).join('  |  ') || 'maya@example.com', 9);
    y -= 8; text('EXPERIENCE', 11, 54, 'F2');
    text('Backend Engineering Intern, Northwind Robotics   Jun 2025 - Aug 2025', 10);
    for (const b of live) for (const [i, l] of wrap(b.text, 92).entries()) text((i === 0 ? '- ' : '  ') + l, 9);
    y -= 8; text('SKILLS', 11, 54, 'F2');
    text('Java, Go, TypeScript | Spring Boot, React | PostgreSQL, Kafka | Docker, GitHub Actions', 9);
    const stream = ops.join('\n');
    const objs = [
      '<< /Type /Catalog /Pages 2 0 R >>',
      '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>',
      `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>',
    ];
    let out = '%PDF-1.4\n'; const off: number[] = [];
    objs.forEach((o, i) => { off.push(out.length); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
    const xref = out.length;
    out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${off.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
    return new Blob([out], { type: 'application/pdf' });
  }

  const reply = (body: unknown, status = 200) =>
    new Response(status === 204 ? null : JSON.stringify(body ?? {}), { status, headers: { 'Content-Type': 'application/json' } });

  function route(method: string, path: string, query: URLSearchParams, body: any): Response | Promise<Response> {
    const m = (re: RegExp) => path.match(re);
    let r: RegExpMatchArray | null;

    if (path === '/api/me') return user ? reply({ username: user.username, isAdmin: false }) : reply({}, 401);
    if (path === '/api/register' && method === 'POST') { user = { username: body.username, email: body.email }; return reply({ username: user.username, isAdmin: false }); }
    if (path === '/api/logout') { user = null; return reply(null, 204); }
    if (path === '/api/field') { if (method === 'PUT') field = body.field; return reply(fieldView()); }

    if (path === '/api/onboarding') return reply({ onboarded, entries: projects.length, bullets: bullets.length, approvedBullets: bullets.filter(b => b.status === 'APPROVED').length, applications: apps.length, downloaded });
    if (path === '/api/onboarding/complete') { onboarded = true; return reply(null, 204); }
    if (path === '/api/onboarding/downloaded') { downloaded = true; return reply(null, 204); }

    if (path === '/api/profile') { if (method === 'PUT') Object.assign(profile, body); return reply(profile); }
    if (path === '/api/config/generation') return reply({ wordFilterEnabled: true, singleLineLow: 22, singleLineHigh: 26, doubleLineLow: 42, doubleLineHigh: 50, deadZoneLow: 27, deadZoneHigh: 40, minWordFloor: 12, temperature: 1, boldDensity: 'LIGHT', tone: 'NEUTRAL', actionVerbStyle: 'TECHNICAL' });

    if (path === '/api/resume/parse') return reply(PARSED);
    if (path === '/api/resume/import') { importItems(body.items); return reply({ imported: body.items.length }); }

    if (path === '/api/projects') {
      const kind = query.get('kind');
      return reply(projects.filter(p => !kind || p.kind === kind).map(withCount));
    }
    if ((r = m(/^\/api\/projects\/([^/]+)$/))) {
      const p = projects.find(x => x.id === r![1]);
      if (!p) return reply({}, 404);
      if (method === 'PUT') Object.assign(p, body);
      return reply({ ...withCount(p), usableStories: stories.filter(s => s.projectId === p.id).length });
    }
    if ((r = m(/^\/api\/projects\/([^/]+)\/stories$/))) return reply({ cap: 8, stories: stories.filter(s => s.projectId === r![1]) });
    if ((r = m(/^\/api\/projects\/([^/]+)\/bullets$/))) return reply(bullets.filter(b => b.projectId === r![1]));
    if ((r = m(/^\/api\/bullets\/([^/]+)\/status$/))) {
      const b = bullets.find(x => x.id === r![1]);
      if (b) { b.status = body.status; b.updatedAt = now(); }
      return reply(b ?? {});
    }

    if (path === '/api/applications' && method === 'GET') return reply(apps.map(a => ({ id: a.id, company: a.company, role: a.role, outcome: 'APPLIED', createdAt: a.createdAt, fitScore: 84, recruiterScore: 81 })).reverse());
    if (path === '/api/applications/submit') {
      // React StrictMode mounts the progress dialog twice in dev, which would submit twice; one tailor is one application.
      const last = apps[apps.length - 1];
      if (last && Date.now() - Date.parse(last.createdAt) < 3000) {
        const [jobId] = [...jobs.keys()].slice(-1);
        return reply({ jobId });
      }
      const id = uid('a'), jobId = uid('job');
      jobs.set(jobId, { startedAt: Date.now(), appId: id });
      const job = JOBS.find(j => j.url === body.jdUrl);
      apps.push({ id, company: job?.company ?? 'Northwind Robotics', role: job?.title ?? 'Backend Engineer Intern', jdText: body.jdText ?? '', createdAt: now() });
      return reply({ jobId });
    }
    if ((r = m(/^\/api\/applications\/jobs\/([^/]+)\/progress$/))) {
      const j = jobs.get(r[1]);
      if (!j) return reply({ status: 'FAILED', error: 'Unknown job' });
      const n = Math.min(JOB_LINES.length, Math.floor((Date.now() - j.startedAt) / 650) + 1);
      return reply({ status: n >= JOB_LINES.length ? 'DONE' : 'RUNNING', lines: JOB_LINES.slice(0, n), appId: j.appId });
    }
    if (path === '/api/public/jobs') return reply({ jobs: JOBS, total: JOBS.length, counts: { linkedin: JOBS.length, indeed: 0, saved: 0 } });
    if (path === '/api/public/jobs/tags') return reply([]);
    if (path === '/api/applications/selection-check') return reply([]);
    if ((r = m(/^\/api\/applications\/([^/]+)\/pdf$/))) return new Response(pdf(), { status: 200, headers: { 'Content-Type': 'application/pdf' } });
    if ((r = m(/^\/api\/applications\/([^/]+)$/)) && apps.some(a => a.id === r![1])) return reply(application(r[1]));

    console.warn('[demo] unmocked', method, path);
    return reply({ message: 'Not mocked in the demo' }, 404);
  }

  const real = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const u = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, location.href);
    if (!u.pathname.startsWith('/api/')) return real(input, init);
    await new Promise(r => setTimeout(r, 120 + Math.random() * 160));
    let body: any;
    try { body = init?.body ? JSON.parse(String(init.body)) : undefined; } catch { body = undefined; }
    return route((init?.method ?? 'GET').toUpperCase(), u.pathname, u.searchParams, body);
  };
}
