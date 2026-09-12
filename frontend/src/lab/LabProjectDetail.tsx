import { useMemo, useState } from 'react';
import { CATEGORIES, type Bullet, type Project } from '../lib/api';
import {
  charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit, type Fit,
} from '../lib/bulletLength';
import { parseExtract, type ExtractField } from '../lib/parseExtract';
import { LAB_BULLETS, LAB_CFG, LAB_MAX_LINES, LAB_PROJECTS } from './fixtures';
import { LabChrome, RichText } from './LabChrome';

type Tab = 'bullets' | 'generate' | 'info';
type StatusTab = 'bank' | 'approved';
type SortMode = 'category' | 'date';

const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));

// Display-only grouping: the internal 8 categories still drive generation and sort, but the
// Generate tab's overview strip shows the bank sorted into 5 market-legible names instead —
// a label, never a picker (nothing here is clickable).
const DISPLAY_GROUPS = ['Backend', 'ML', 'DevOps', 'Infra', 'Frontend'] as const;
const CATEGORY_TO_DISPLAY: Record<string, typeof DISPLAY_GROUPS[number]> = {
  backend: 'Backend', data: 'Backend', security: 'Backend', comms: 'Backend',
  'ai-ml': 'ML',
  devops: 'DevOps',
  systems: 'Infra',
  frontend: 'Frontend',
};

// Generic fallback content for when a bucket is picked directly, with no specific dynamic
// lens behind it — the same role the original fixed-category templates used to play.
const BUCKET_TEMPLATES: Record<typeof DISPLAY_GROUPS[number], { text: string; category: string }> = {
  Backend: { text: 'Refactored the request pipeline, dropping p95 latency **120ms**.', category: 'backend' },
  ML: { text: 'Built a retrieval pipeline that cut irrelevant results **35%** using reranked embeddings.', category: 'ai-ml' },
  DevOps: { text: 'Cut CI runtime **9 minutes** by parallelizing the test matrix.', category: 'devops' },
  Infra: { text: 'Added backpressure to the event queue, eliminating dropped messages under load.', category: 'systems' },
  Frontend: { text: 'Rebuilt the dashboard shell, cutting first paint **400ms**.', category: 'frontend' },
};

const DEMO_EXPERIENCE = LAB_PROJECTS.find(p => p.kind === 'EXPERIENCE')!;
const DEMO_PROJECT = LAB_PROJECTS.find(p => p.kind === 'PROJECT')!;

// A couple of not-yet-approved bullets per demo record, so the Bank/Approved split has
// something to show — every fixture bullet elsewhere in the lab is pre-approved.
const EXTRA_PENDING: Bullet[] = [
  {
    id: 'pending-1', projectId: DEMO_EXPERIENCE.id,
    text: 'Explored replacing the nightly batch job with a streaming pipeline; parked after scoping.',
    tags: ['systems'], category: 'systems', status: 'PENDING',
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    id: 'pending-2', projectId: DEMO_EXPERIENCE.id,
    text: 'Drafted an on-call rotation doc for the pricing service.',
    tags: ['docs'], category: 'devops', status: 'PENDING',
    createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
  },
  {
    id: 'pending-3', projectId: DEMO_PROJECT.id,
    text: 'Prototyped a second embedding model; benchmarks inconclusive so far.',
    tags: ['ai-ml'], category: 'ai-ml', status: 'PENDING',
    createdAt: '2026-08-20T00:00:00Z', updatedAt: '2026-08-20T00:00:00Z',
  },
];

function seedBank(projectId: string): Bullet[] {
  return [...LAB_BULLETS, ...EXTRA_PENDING].filter(b => b.projectId === projectId);
}

// Dynamic lenses replace the fixed 8-category picker: one lens per technology this project
// actually names, plus one per narrative context field this project has actually filled in.
// A different project gets a different lens list — nothing here is a guessed taxonomy.
type Lens = { slug: string; label: string; blurb: string; kind: 'tech' | 'narrative' };

const TECH_CATEGORY: Record<string, string> = {
  postgres: 'backend', mysql: 'backend', openapi: 'backend', go: 'backend', http: 'backend',
  redis: 'systems', kafka: 'systems', grpc: 'systems', rust: 'systems',
  kubernetes: 'devops', docker: 'devops', terraform: 'devops',
  oauth: 'security', jwt: 'security', hipaa: 'security', encryption: 'security',
  websocket: 'comms', react: 'frontend', typescript: 'frontend', graphql: 'frontend',
  pgvector: 'ai-ml', python: 'ai-ml',
};

const TECH_TEMPLATES: Record<string, string> = {
  postgres: 'Optimized a hot Postgres query path, cutting p95 read latency **140ms**.',
  redis: 'Introduced a Redis-backed cache in front of the pricing lookup, cutting DB load **30%**.',
  kubernetes: 'Migrated the service onto Kubernetes, enabling zero-downtime rolling deploys.',
  pgvector: 'Tuned pgvector index parameters, halving semantic search latency.',
  go: 'Rewrote the ingest worker in Go, cutting memory footprint **3x**.',
  openapi: 'Generated client SDKs straight from the OpenAPI spec, removing a manual sync step.',
  http: 'Added conditional GET support to the crawler, cutting outbound bandwidth **70%**.',
  encryption: 'Rotated token encryption to AES-256-GCM with per-tenant keys.',
};

const NARRATIVE_LABEL: Record<string, string> = {
  yourRole: 'Your role', ownership: 'Ownership', scaleImpact: 'Scale & impact',
  hardestProblem: 'Hardest problem', technicalDecisions: 'Technical decisions',
  userImpact: 'Who it served', securityPosture: 'Security & compliance',
};

const NARRATIVE_CATEGORY: Record<string, string> = {
  yourRole: 'backend', ownership: 'systems', scaleImpact: 'backend',
  hardestProblem: 'systems', technicalDecisions: 'systems', userImpact: 'frontend', securityPosture: 'security',
};

function firstClause(text: string): string {
  return text.split(/[.;\n]/)[0].trim();
}

function narrativeBullet(slug: string, project: Project): string {
  const value = (project as unknown as Record<string, string | null | undefined>)[slug] ?? '';
  const clause = firstClause(value);
  switch (slug) {
    case 'scaleImpact': return `Delivered against real scale: **${clause}**.`;
    case 'hardestProblem': return `Solved the hardest problem on the project — ${clause}.`;
    case 'technicalDecisions': return `Made a deliberate tradeoff here: ${clause}.`;
    case 'securityPosture': return `Hardened the system: ${clause}.`;
    case 'ownership': return `Owned end-to-end: ${clause}.`;
    case 'userImpact': return `Built for the people who actually used it: ${clause}.`;
    case 'yourRole': return `${clause} — shipped independently from spec to production.`;
    default: return clause;
  }
}

/** Splits a comma/slash-separated tech-stack string into individual lens candidates. */
function techTokens(techStack: string | null | undefined): string[] {
  if (!techStack) return [];
  return techStack.split(/[,/]/).map(t => t.trim()).filter(t => t.length > 1);
}

function deriveLenses(project: Project): Lens[] {
  const tech: Lens[] = techTokens(project.techStack).map(token => ({
    slug: token.toLowerCase(),
    label: token,
    blurb: `Bullets that specifically demonstrate ${token}`,
    kind: 'tech',
  }));

  const narrative: Lens[] = (Object.keys(NARRATIVE_LABEL) as (keyof typeof NARRATIVE_LABEL)[])
    .filter(key => Boolean((project as unknown as Record<string, string | null | undefined>)[key]))
    .map(key => ({
      slug: key,
      label: NARRATIVE_LABEL[key],
      blurb: firstClause((project as unknown as Record<string, string>)[key]),
      kind: 'narrative' as const,
    }));

  return [...tech, ...narrative];
}

function mockRefitText(text: string, fit: Fit): string {
  if (fit === 'TOO_LONG' || fit === 'DEAD_ZONE') {
    const words = text.replace(/\.$/, '').split(' ');
    return words.slice(0, Math.max(8, Math.floor(words.length * 0.6))).join(' ') + '.';
  }
  if (fit === 'TOO_SHORT') {
    return text.replace(/\.$/, '') + ', cutting manual follow-up significantly.';
  }
  return text;
}

export function LabProjectDetail() {
  // The real page reads ?id= from the route; the lab always shows one of two fixture
  // records, switchable below so both the Experience and Project variants are reachable.
  const [project, setProject] = useState<Project>(DEMO_EXPERIENCE);
  const isExperience = project.kind === 'EXPERIENCE';

  return (
    <LabChrome
      title="Projects & Experiences — bank"
      note={
        <>
          Full pass: approve/reject, add, edit, delete, refit, sort, and a page-budget PDF
          preview — the same jobs the real bullet bank does — rebuilt with the fixes from the
          Applications rework. Delete lives in a menu with an in-place confirm instead of a bare
          DELETE button; approve/reject is a labelled toggle instead of an underline; each
          bullet's line-fit band is a real badge, and REFIT shows which bullets it will touch
          before you run it.
        </>
      }
    >
      <div className="row row--between row--centered" style={{ marginBottom: 8 }}>
        <div className="eyebrow">← {isExperience ? 'All experiences' : 'All projects'}</div>
        <div className="filterset" title="Lab-only — switches which fixture record this page demos">
          <button className={isExperience ? 'is-on' : ''} onClick={() => setProject(DEMO_EXPERIENCE)}>
            Viewing: Experience
          </button>
          <button className={!isExperience ? 'is-on' : ''} onClick={() => setProject(DEMO_PROJECT)}>
            Viewing: Project
          </button>
        </div>
      </div>

      <ProjectWorkspace key={project.id} project={project} isExperience={isExperience} />
    </LabChrome>
  );
}

// ---------------------------------------------------------------- workspace (per project)

function ProjectWorkspace({ project, isExperience }: { project: Project; isExperience: boolean }) {
  const [tab, setTab] = useState<Tab>('bullets');
  const [bank, setBank] = useState<Bullet[]>(() => seedBank(project.id));

  const [statusTab, setStatusTab] = useState<StatusTab>('bank');
  const [sortMode, setSortMode] = useState<SortMode>('category');
  const [filterCat, setFilterCat] = useState<string | null>(null);
  const [closedGroups, setClosedGroups] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [refitting, setRefitting] = useState(false);
  const [refitMsg, setRefitMsg] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewBusy, setPreviewBusy] = useState(false);

  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [pickedBuckets, setPickedBuckets] = useState<Set<string>>(new Set());
  const [generating, setGenerating] = useState(false);
  const [genPct, setGenPct] = useState(0);
  const [customText, setCustomText] = useState('');
  const [customAdding, setCustomAdding] = useState(false);
  const [customMsg, setCustomMsg] = useState<string | null>(null);

  const approvedCount = bank.filter(b => b.status === 'APPROVED').length;
  const bankCount = bank.length - approvedCount;

  const displayed = useMemo(
    () => bank.filter(b => (statusTab === 'approved' ? b.status === 'APPROVED' : b.status !== 'APPROVED')),
    [bank, statusTab],
  );

  const byCategory = useMemo(() => {
    const groups = new Map<string, Bullet[]>();
    for (const b of displayed) {
      const list = groups.get(b.category) ?? [];
      list.push(b);
      groups.set(b.category, list);
    }
    return [...groups.entries()].map(([slug, rows]) => ({
      slug, label: CATEGORY_MAP[slug]?.label ?? slug, blurb: CATEGORY_MAP[slug]?.blurb, rows,
    }));
  }, [displayed]);

  const visible = filterCat ? byCategory.filter(g => g.slug === filterCat) : byCategory;
  const flatByDate = useMemo(
    () => [...displayed].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
    [displayed],
  );

  const displayGroupCount = useMemo(() => {
    const c: Record<string, number> = {};
    for (const b of bank) {
      const group = CATEGORY_TO_DISPLAY[b.category] ?? 'Backend';
      c[group] = (c[group] ?? 0) + 1;
    }
    return c;
  }, [bank]);

  // Lenses derived from this specific project — one per named technology, one per filled-in
  // narrative field. A different project gets a different list; nothing here is fixed.
  const lenses = useMemo(() => deriveLenses(project), [project]);
  const lensCount = useMemo(() => {
    const c: Record<string, number> = {};
    for (const lens of lenses) {
      if (lens.kind !== 'tech') continue;
      c[lens.slug] = bank.filter(b => b.text.toLowerCase().includes(lens.slug) || b.tags.some(t => t.toLowerCase().includes(lens.slug))).length;
    }
    return c;
  }, [lenses, bank]);

  // The single least-covered lens, used only to nudge the custom-bullet line — never rendered
  // as a picker of its own, just a one-line "here's a gap, if you want it" suggestion.
  const suggestedLens = useMemo(() => {
    if (lenses.length === 0) return null;
    const coverage = (lens: Lens) => lens.kind === 'tech'
      ? (lensCount[lens.slug] ?? 0)
      : bank.filter(b => b.tags.some(t => t.toLowerCase() === lens.slug.toLowerCase())).length;
    const zero = lenses.find(l => coverage(l) === 0);
    return zero ?? lenses.reduce((min, l) => (coverage(l) < coverage(min) ? l : min), lenses[0]);
  }, [lenses, lensCount, bank]);

  const offBand = useMemo(
    () => bank.filter(b => needsRefit(fitOf(b.text, LAB_CFG))),
    [bank],
  );

  // Approved bullets are priority, not a gate — anything in the bank can land on the page,
  // approved just goes first when the budget is tight.
  const pageBullets = useMemo(
    () => [...bank].sort((a, b) => (b.status === 'APPROVED' ? 1 : 0) - (a.status === 'APPROVED' ? 1 : 0)),
    [bank],
  );
  const pageLines = useMemo(
    () => bank.reduce((n, b) => n + estimatedLines(b.text), 0),
    [bank],
  );
  const overBudget = pageLines > LAB_MAX_LINES;

  const avgLines = bank.length === 0 ? 0
    : Math.round((bank.reduce((n, b) => n + estimatedLines(b.text), 0) / bank.length) * 10) / 10;

  const filledCount = [project.techStack, project.yourRole, project.ownership, project.scaleImpact,
    project.hardestProblem, project.technicalDecisions, project.userImpact, project.securityPosture]
    .filter(Boolean).length;

  function toggleGroup(slug: string) {
    setClosedGroups(s => {
      const next = new Set(s);
      next.has(slug) ? next.delete(slug) : next.add(slug);
      return next;
    });
  }

  function toggleApprove(id: string) {
    setBank(bs => bs.map(b => (b.id === id ? { ...b, status: b.status === 'APPROVED' ? 'PENDING' : 'APPROVED' } : b)));
  }
  function deleteBullet(id: string) {
    setBank(bs => bs.filter(b => b.id !== id));
  }
  function addBullet(text: string, tags: string[], category: string) {
    setBank(bs => [{
      id: `new-${Date.now()}`, projectId: project.id, text, tags, category, status: 'PENDING',
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    }, ...bs]);
    setAdding(false);
  }
  function saveEdit(id: string, text: string, tags: string[]) {
    setBank(bs => bs.map(b => (b.id === id ? { ...b, text, tags, updatedAt: new Date().toISOString() } : b)));
    setEditingId(null);
  }

  function refit() {
    if (offBand.length === 0) return;
    setRefitting(true);
    setRefitMsg(null);
    window.setTimeout(() => {
      setBank(bs => bs.map(b => {
        const fit = fitOf(b.text, LAB_CFG);
        return needsRefit(fit) ? { ...b, text: mockRefitText(b.text, fit), updatedAt: new Date().toISOString() } : b;
      }));
      setRefitting(false);
      setRefitMsg(`Rewrote ${offBand.length} bullet${offBand.length === 1 ? '' : 's'} to fit their band.`);
    }, 1100);
  }

  function togglePick(slug: string) {
    setPicked(s => {
      const next = new Set(s);
      next.has(slug) ? next.delete(slug) : next.add(slug);
      return next;
    });
  }

  function toggleBucket(group: string) {
    setPickedBuckets(s => {
      const next = new Set(s);
      next.has(group) ? next.delete(group) : next.add(group);
      return next;
    });
  }

  function addCustomBullet() {
    const text = customText.trim();
    if (!text) return;
    setCustomAdding(true);
    setCustomMsg(null);
    window.setTimeout(() => {
      const guessed = Object.keys(TECH_CATEGORY).find(k => text.toLowerCase().includes(k));
      setBank(bs => [{
        id: `custom-${Date.now()}`, projectId: project.id,
        text: /[.!?]$/.test(text) ? text : `${text}.`,
        tags: ['custom'], category: guessed ? TECH_CATEGORY[guessed] : 'backend', status: 'PENDING',
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      }, ...bs]);
      setCustomAdding(false);
      setCustomText('');
      setCustomMsg('Added to the Bank.');
    }, 400);
  }

  function generateBank() {
    // The 5 buckets are an explicit ask, always run if checked. The dynamic lens list keeps
    // its own default: nothing picked there means run all of them, independent of the buckets.
    const dynTargets = picked.size > 0 ? [...picked] : lenses.map(l => l.slug);
    const bucketTargets = [...pickedBuckets];
    const total = dynTargets.length + bucketTargets.length;
    if (total === 0) return;
    setGenerating(true);
    setGenPct(0);
    const started = Date.now();
    const duration = Math.max(700, total * 700);
    const timer = window.setInterval(() => {
      const pct = Math.min(100, Math.round(((Date.now() - started) / duration) * 100));
      setGenPct(pct);
      if (pct >= 100) {
        window.clearInterval(timer);
        setBank(bs => [
          ...bucketTargets.map((group, i) => ({
            id: `gen-bucket-${Date.now()}-${i}`, projectId: project.id,
            text: BUCKET_TEMPLATES[group as typeof DISPLAY_GROUPS[number]].text,
            tags: [group.toLowerCase()],
            category: BUCKET_TEMPLATES[group as typeof DISPLAY_GROUPS[number]].category,
            status: 'PENDING' as const,
            createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
          })),
          ...dynTargets.map((slug, i) => {
            const lens = lenses.find(l => l.slug === slug);
            const isTech = lens?.kind === 'tech';
            return {
              id: `gen-${Date.now()}-${i}`, projectId: project.id,
              text: isTech
                ? (TECH_TEMPLATES[slug] ?? `Delivered a concrete win using ${lens?.label ?? slug}.`)
                : narrativeBullet(slug, project),
              tags: [slug],
              category: isTech ? (TECH_CATEGORY[slug] ?? 'backend') : (NARRATIVE_CATEGORY[slug] ?? 'backend'),
              status: 'PENDING' as const,
              createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
            };
          }),
          ...bs,
        ]);
        setGenerating(false);
        setPicked(new Set());
        setPickedBuckets(new Set());
        setStatusTab('bank');
        setTab('bullets');
      }
    }, 120);
  }

  return (
    <>
      <div style={{ marginBottom: 18 }}>
        <h2 className="display" style={{ fontSize: 40, lineHeight: 1, margin: '0 0 6px' }}>
          {isExperience ? (project.title || project.name) : project.name}
        </h2>
        <div className="editorial" style={{ fontSize: 16, color: 'var(--muted)' }}>
          {isExperience
            ? [project.company, project.location, project.dates].filter(Boolean).join(' · ')
            : project.description}
        </div>
      </div>

      <div className="statrow">
        <div className={overBudget ? 'stat stat--alert' : 'stat'}>
          <div className="stat__label">Page budget</div>
          <div className="stat__value">{pageLines}<small>/{LAB_MAX_LINES} lines</small></div>
          <div className="meter" style={{ marginTop: 8 }}>
            <div className={`meter__fill ${overBudget ? 'meter__fill--over' : 'meter__fill--ok'}`}
              style={{ width: `${Math.min(100, Math.round((pageLines / LAB_MAX_LINES) * 100))}%` }} />
          </div>
          <div className="stat__caption">Whole bank counts toward the page — approved goes first.</div>
        </div>

        <div className="stat">
          <div className="stat__label">Approved</div>
          <div className="stat__value">{approvedCount}<small>/{bank.length} bullets</small></div>
          <div className="stat__caption">{bankCount} more still pullable, just lower priority.</div>
        </div>

        <div className={offBand.length > 0 ? 'stat stat--alert' : 'stat'}>
          <div className="stat__label">Off length band</div>
          <div className="stat__value">{offBand.length}<small>/{bank.length} bullets</small></div>
          <div className="stat__caption">{offBand.length === 0 ? 'Everything fits its band.' : 'REFIT on the Bullets tab fixes these.'}</div>
        </div>

        <div className={filledCount === 0 ? 'stat stat--alert' : 'stat'}>
          <div className="stat__label">Context</div>
          <div className="stat__value">{filledCount}<small>/5 fields</small></div>
          <div className="stat__caption">
            {filledCount === 0
              ? 'Empty — bullets generate generic without it.'
              : `${lenses.length} lens${lenses.length === 1 ? '' : 'es'} available for this project, avg ${avgLines}L/bullet.`}
          </div>
        </div>
      </div>

      <div className="tabs" style={{ marginTop: 22, marginBottom: 0 }}>
        <button className={tab === 'bullets' ? 'is-on' : ''} onClick={() => setTab('bullets')}>
          Bullets <span className="tabs__badge">{bank.length}</span>
        </button>
        <button className={tab === 'generate' ? 'is-on' : ''} onClick={() => setTab('generate')}>
          Generate
        </button>
        <button className={tab === 'info' ? 'is-on' : ''} onClick={() => setTab('info')}>
          Info & context
          {filledCount === 0 && <span className="tabs__badge">!</span>}
        </button>
      </div>

      <div className="tabpane">
        {tab === 'bullets' && (
          <div>
            <div className="row row--between row--centered" style={{ marginBottom: 14, flexWrap: 'wrap', gap: 10 }}>
              <div className="filterset">
                <button className={statusTab === 'bank' ? 'is-on' : ''} onClick={() => setStatusTab('bank')}>
                  Bank <span className="filterset__count">{bankCount}</span>
                </button>
                <button className={statusTab === 'approved' ? 'is-on' : ''} onClick={() => setStatusTab('approved')}>
                  Approved <span className="filterset__count">{approvedCount}</span>
                </button>
              </div>

              <div className="row" style={{ gap: 8 }}>
                <div className="filterset">
                  <button className={sortMode === 'category' ? 'is-on' : ''} onClick={() => setSortMode('category')}>By category</button>
                  <button className={sortMode === 'date' ? 'is-on' : ''} onClick={() => setSortMode('date')}>By date</button>
                </div>
                <button
                  className="minibtn"
                  onClick={refit}
                  disabled={refitting || offBand.length === 0}
                  title="Rewrite bullets whose length misses the page bands"
                >
                  {refitting ? 'Refitting…' : offBand.length === 0 ? 'All fit' : `Refit ${offBand.length}`}
                </button>
                <button className="minibtn" onClick={() => { setAdding(a => !a); setEditingId(null); }}>
                  {adding ? '✕ Cancel' : '+ Add bullet'}
                </button>
                <button
                  className="btn btn--sm"
                  style={{ background: 'var(--acid)', borderWidth: 2 }}
                  onClick={() => { setPreviewBusy(true); setPreviewOpen(true); window.setTimeout(() => setPreviewBusy(false), 600); }}
                  disabled={bank.length === 0}
                >
                  {previewBusy ? 'Rendering…' : `Render PDF (~${pageLines}L)`}
                </button>
              </div>
            </div>

            {refitMsg && <div className="label muted" style={{ marginBottom: 10 }}>{refitMsg}</div>}

            {previewOpen && (
              <PagePreview
                bullets={pageBullets}
                lines={pageLines}
                over={overBudget}
                onClose={() => setPreviewOpen(false)}
              />
            )}

            {adding && <AddBulletForm onSave={addBullet} onCancel={() => setAdding(false)} />}

            {byCategory.length > 1 && sortMode === 'category' && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 18 }}>
                <button
                  className="btn btn--sm"
                  onClick={() => setFilterCat(null)}
                  style={{ background: filterCat === null ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)' }}
                >ALL</button>
                {byCategory.map(g => (
                  <button
                    key={g.slug}
                    className="btn btn--sm"
                    onClick={() => setFilterCat(c => c === g.slug ? null : g.slug)}
                    style={{
                      background: filterCat === g.slug ? 'var(--acid)' : 'var(--paper)',
                      color: 'var(--ink)', borderColor: 'var(--ink)',
                    }}
                  >
                    {g.label} <span style={{ opacity: 0.6, marginLeft: 4 }}>{g.rows.length}</span>
                  </button>
                ))}
              </div>
            )}

            {sortMode === 'category' ? visible.map(g => {
              const open = !closedGroups.has(g.slug);
              return (
                <div key={g.slug} style={{ marginBottom: 28 }}>
                  <div
                    className="grouphead"
                    role="button"
                    tabIndex={0}
                    aria-expanded={open}
                    onClick={() => toggleGroup(g.slug)}
                    onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleGroup(g.slug); } }}
                    style={{ marginBottom: open ? 12 : 0, paddingBottom: 6, borderBottom: 'var(--rule-thick)', cursor: 'pointer' }}
                  >
                    <div className="row row--between row--centered">
                      <span className="label">{open ? '▾' : '▸'} {g.label}</span>
                      <span className="label muted">{g.rows.length}</span>
                    </div>
                  </div>
                  {open && g.rows.map(b => editingId === b.id ? (
                    <EditBulletForm key={b.id} bullet={b} onSave={(t, tg) => saveEdit(b.id, t, tg)} onCancel={() => setEditingId(null)} />
                  ) : (
                    <ManagedBulletRow
                      key={b.id} bullet={b}
                      onEdit={() => { setEditingId(b.id); setAdding(false); }}
                      onDelete={() => deleteBullet(b.id)}
                      onToggleApprove={() => toggleApprove(b.id)}
                    />
                  ))}
                </div>
              );
            }) : (
              <div>
                {flatByDate.map(b => editingId === b.id ? (
                  <EditBulletForm key={b.id} bullet={b} onSave={(t, tg) => saveEdit(b.id, t, tg)} onCancel={() => setEditingId(null)} />
                ) : (
                  <ManagedBulletRow
                    key={b.id} bullet={b} categoryLabel={CATEGORY_MAP[b.category]}
                    onEdit={() => { setEditingId(b.id); setAdding(false); }}
                    onDelete={() => deleteBullet(b.id)}
                    onToggleApprove={() => toggleApprove(b.id)}
                  />
                ))}
              </div>
            )}

            {displayed.length === 0 && !adding && (
              <div className="editorial muted" style={{ padding: '40px 0' }}>
                {statusTab === 'approved' ? 'No approved bullets yet.' : 'Bank is empty. Generate more, or add one above.'}
              </div>
            )}
          </div>
        )}

        {tab === 'generate' && (
          <div>
            {filledCount === 0 && (
              <div className="callout" style={{ marginBottom: 16 }}>
                <div className="callout__head">Context is empty</div>
                Nothing in Tech stack, Scale &amp; impact or the other context fields yet — there
                are no lenses to derive until something's filled in.{' '}
                <button className="minibtn" onClick={() => setTab('info')}>Fill context →</button>
              </div>
            )}

            {generating ? (
              <div style={{ padding: '24px 0' }}>
                <div className="eyebrow" style={{ marginBottom: 10 }}>
                  Generating {(picked.size || lenses.length) + pickedBuckets.size} bullet{((picked.size || lenses.length) + pickedBuckets.size) === 1 ? '' : 's'}…
                </div>
                <div className="meter meter--thick">
                  <div className="meter__fill meter__fill--ok" style={{ width: `${genPct}%` }} />
                </div>
                <div className="label muted" style={{ marginTop: 8 }}>{genPct}% — new bullets land in the Bank tab as they finish.</div>
              </div>
            ) : (
              <>
                <div className="eyebrow" style={{ marginBottom: 10 }}>Pick a focus</div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 8, marginBottom: 24 }}>
                  {DISPLAY_GROUPS.map(group => {
                    const on = pickedBuckets.has(group);
                    const count = displayGroupCount[group] ?? 0;
                    return (
                      <button
                        type="button"
                        key={group}
                        onClick={() => toggleBucket(group)}
                        className="btn btn--sm"
                        style={{
                          justifyContent: 'space-between',
                          padding: '12px 14px',
                          background: on ? 'var(--ink)' : 'var(--paper)',
                          color: on ? 'var(--paper)' : 'var(--ink)',
                          borderColor: 'var(--ink)',
                        }}
                        title={`${count} bullet${count === 1 ? '' : 's'} already in ${group}`}
                      >
                        <span style={{ fontSize: 12, letterSpacing: '0.14em' }}>{on ? '✓' : '○'} {group.toUpperCase()}</span>
                        <span style={{ fontFamily: 'var(--mono)', fontSize: 11, opacity: 0.7 }}>{count}</span>
                      </button>
                    );
                  })}
                </div>

                <div className="row row--between row--centered" style={{ marginBottom: 4 }}>
                  <div className="eyebrow">Lenses for {isExperience ? (project.title || project.name) : project.name}</div>
                  {lenses.length > 0 && (
                    <div className="row" style={{ gap: 8 }}>
                      <button className="minibtn" onClick={() => setPicked(new Set(lenses.map(l => l.slug)))}>Select all</button>
                      <button className="minibtn" onClick={() => setPicked(new Set())} disabled={picked.size === 0}>Clear</button>
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 10, maxWidth: 560 }}>
                  Derived from this project's own tech stack and context — a different project
                  gets a different list. Leave everything unpicked and Generate runs all of them.
                </div>

                {lenses.length === 0 ? (
                  <div className="editorial muted" style={{ padding: '20px 0' }}>
                    Nothing to derive lenses from yet. Fill in Tech stack or another context field
                    on the Info tab.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
                    {lenses.map(lens => {
                      const on = picked.has(lens.slug);
                      const existing = lensCount[lens.slug];
                      return (
                        <button
                          type="button"
                          key={lens.slug}
                          onClick={() => togglePick(lens.slug)}
                          className="btn btn--sm"
                          style={{
                            padding: '5px 10px',
                            background: on ? 'var(--ink)' : 'var(--paper)',
                            color: on ? 'var(--paper)' : 'var(--ink)',
                            borderColor: 'var(--ink)',
                          }}
                          title={lens.blurb}
                        >
                          <span style={{ fontSize: 10.5, letterSpacing: '0.08em' }}>
                            {on ? '✓' : '○'} {lens.label}
                          </span>
                          {existing !== undefined && (
                            <span style={{ fontFamily: 'var(--mono)', fontSize: 9.5, marginLeft: 6, opacity: existing === 0 ? 0.4 : 0.75 }}>
                              {existing}
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}

                <div className="row row--between row--centered" style={{ marginBottom: 24 }}>
                  <span className="label muted">
                    {(() => {
                      const dynCount = picked.size || lenses.length;
                      const total = dynCount + pickedBuckets.size;
                      return total === 0 ? 'NOTHING TO GENERATE YET' : `${total} TARGET${total === 1 ? '' : 'S'} · ~${total}–${total * 2} NEW BULLETS`;
                    })()}
                  </span>
                  <button className="btn btn--acid" disabled={lenses.length === 0 && pickedBuckets.size === 0} onClick={generateBank}>↻ GENERATE BANK</button>
                </div>

                <div className="panel panel--inset stack-sm">
                  <div className="label">OR DESCRIBE ONE YOURSELF</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                    Not on the list? Type the moment and get one bullet for it — no lens required.
                  </div>

                  {suggestedLens && !customText && (
                    <div style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
                      fontSize: 11.5, color: 'var(--muted)', padding: '6px 8px', border: '1px dashed var(--soft)',
                    }}>
                      <span>
                        Nothing covers <b style={{ color: 'var(--ink)' }}>{suggestedLens.label}</b> yet —
                        describe something specific to it, or pick it above.
                      </span>
                      <button
                        className="minibtn"
                        onClick={() => setCustomText(`The time I worked on ${suggestedLens.label}: `)}
                      >
                        Use this
                      </button>
                    </div>
                  )}

                  <textarea
                    className="field__textarea"
                    value={customText}
                    onChange={e => setCustomText(e.target.value)}
                    style={{ minHeight: 60 }}
                    placeholder="e.g. the time I migrated the primary DB under live traffic with no downtime"
                  />
                  <div className="row">
                    <button className="btn btn--sm" onClick={addCustomBullet} disabled={!customText.trim() || customAdding}>
                      {customAdding ? 'Writing…' : 'Generate this one'}
                    </button>
                  </div>
                  {customMsg && <div className="label muted">{customMsg}</div>}
                </div>
              </>
            )}
          </div>
        )}

        {tab === 'info' && (
          isExperience
            ? <ExperienceContextForm project={project} />
            : <ProjectContextForm project={project} />
        )}
      </div>

      <div className="lab-note" style={{ marginTop: 24 }}>
        Config used for the line-fit badges above:{' '}
        <code>{LAB_CFG.singleLineLow}–{LAB_CFG.doubleLineHigh} words</code>, same bands the real
        editor filters by.
      </div>
    </>
  );
}

// ---------------------------------------------------------------- bullet row + forms

function ManagedBulletRow({ bullet, categoryLabel, onEdit, onDelete, onToggleApprove }: {
  bullet: Bullet;
  categoryLabel?: { label: string; blurb: string };
  onEdit: () => void;
  onDelete: () => void;
  onToggleApprove: () => void;
}) {
  const approved = bullet.status === 'APPROVED';
  const fit = fitOf(bullet.text, LAB_CFG);
  const bad = needsRefit(fit);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);

  return (
    <div className={`brow ${approved ? 'is-in' : ''}`}>
      <button className="brow__toggle" onClick={onToggleApprove} aria-pressed={approved}>
        <span className={`brow__state ${approved ? 'brow__state--in' : 'brow__state--out'}`}>
          {approved ? '✓ APPROVED' : 'NORMAL'}
        </span>
        <span className="brow__rank">{estimatedLines(bullet.text)}L</span>
      </button>

      <div>
        <div className="brow__text"><RichText text={bullet.text} /></div>

        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginTop: 5 }}>
          {fit !== 'OFF' && (
            <span
              title={fitHint(bullet.text, LAB_CFG)}
              style={{
                fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.1em',
                color: bad ? 'var(--rust)' : 'var(--muted)',
              }}
            >
              {FIT_LABEL[fit]} · {charCount(bullet.text)}c
            </span>
          )}

          {categoryLabel && (
            <span style={{ fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--muted)', letterSpacing: '0.1em', textTransform: 'uppercase' }}>
              {categoryLabel.label}
            </span>
          )}

          {bullet.tags.map(t => (
            <span key={t} style={{
              fontFamily: 'var(--mono)', fontSize: 9.5, color: 'var(--muted)', letterSpacing: '0.03em',
              border: '1px solid var(--soft)', padding: '1px 5px',
            }}>
              {t}
            </span>
          ))}
        </div>

        <div className="brow__under">
          <div className="brow__actions" style={{ marginLeft: 0 }} onMouseLeave={() => { setMenuOpen(false); setConfirming(false); }}>
            <button className="minibtn" onClick={onEdit}>Edit</button>
            <div className="rowmenu" style={{ display: 'inline-block' }}>
              <button className="rowmenu__btn" aria-label="Bullet actions" aria-expanded={menuOpen} onClick={() => setMenuOpen(o => !o)}>⋯</button>
              {menuOpen && (
                <div className="rowmenu__pop">
                  {confirming ? (
                    <button className="is-danger" onClick={() => { onDelete(); setMenuOpen(false); setConfirming(false); }}>
                      Really delete?
                    </button>
                  ) : (
                    <button className="is-danger" onClick={() => setConfirming(true)}>Delete</button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AddBulletForm({ onSave, onCancel }: {
  onSave: (text: string, tags: string[], category: string) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState('');
  const [tagsStr, setTagsStr] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0].slug);
  const [err, setErr] = useState<string | null>(null);

  function submit() {
    if (!text.trim()) { setErr('Text is required.'); return; }
    onSave(text.trim(), tagsStr.split(',').map(s => s.trim()).filter(Boolean), category);
  }

  return (
    <div className="panel panel--inset stack-sm" style={{ marginBottom: 20 }}>
      <div className="label">NEW BULLET</div>
      <textarea className="field__textarea" value={text} autoFocus style={{ minHeight: 70 }}
        onChange={e => { setText(e.target.value); setErr(null); }}
        placeholder="Reduced latency by 47ms by rewriting the query planner." />
      <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)}
        placeholder="backend, performance (optional)" />
      <select className="field__input" value={category} onChange={e => setCategory(e.target.value)}>
        {CATEGORIES.map(c => <option key={c.slug} value={c.slug}>{c.label} — {c.blurb}</option>)}
      </select>
      {err && <div className="err">{err}</div>}
      <div className="row">
        <button className="btn btn--acid btn--sm" onClick={submit}>Save</button>
        <button className="btn btn--ghost btn--sm" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}

function EditBulletForm({ bullet, onSave, onCancel }: {
  bullet: Bullet;
  onSave: (text: string, tags: string[]) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(bullet.text);
  const [tagsStr, setTagsStr] = useState(bullet.tags.join(', '));
  const fit = fitOf(text, LAB_CFG);
  const hint = fitHint(text, LAB_CFG);
  return (
    <div className="panel panel--inset stack-sm" style={{ marginBottom: 12 }}>
      <div className="label">EDIT BULLET</div>
      <textarea className="field__textarea" value={text} onChange={e => setText(e.target.value)} style={{ minHeight: 70 }} autoFocus />
      {fit !== 'OFF' && (
        <div style={{ fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.1em', color: needsRefit(fit) ? 'var(--rust)' : 'var(--muted)' }}>
          {FIT_LABEL[fit]} · {charCount(text)}c{hint ? ` — ${hint}` : ''}
        </div>
      )}
      <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)} placeholder="backend, ai-ml" />
      <div className="row">
        <button className="btn btn--acid btn--sm" onClick={() => onSave(text, tagsStr.split(',').map(s => s.trim()).filter(Boolean))}>Save</button>
        <button className="btn btn--ghost btn--sm" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}

function PagePreview({ bullets, lines, over, onClose }: {
  bullets: Bullet[]; lines: number; over: boolean; onClose: () => void;
}) {
  const groups = new Map<string, Bullet[]>();
  for (const b of bullets) {
    const list = groups.get(b.category) ?? [];
    list.push(b);
    groups.set(b.category, list);
  }
  return (
    <div style={{ marginBottom: 20 }}>
      <div className="row row--between row--centered" style={{ background: 'var(--acid)', color: 'var(--ink)', padding: '6px 10px', border: '2px solid var(--ink)', borderBottom: 'none' }}>
        <span className="label" style={{ fontWeight: 700 }}>
          PREVIEW · {bullets.length} BULLETS ({bullets.filter(b => b.status === 'APPROVED').length} APPROVED) · NOT SAVED
        </span>
        <button className="btn btn--ghost btn--sm" style={{ fontSize: 10, padding: '2px 6px' }} onClick={onClose}>✕ CLOSE</button>
      </div>
      <div style={{ background: '#fff', border: '2px solid var(--ink)', padding: '24px 26px', maxHeight: 480, overflow: 'auto' }}>
        {[...groups.entries()].map(([slug, rows]) => (
          <div key={slug} style={{ marginBottom: 14 }}>
            <div style={{ fontFamily: 'var(--serif)', fontWeight: 700, fontSize: 13, borderBottom: '1px solid #000', paddingBottom: 2, marginBottom: 5 }}>
              {CATEGORY_MAP[slug]?.label ?? slug}
            </div>
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              {rows.map(b => (
                <li key={b.id} style={{ fontSize: 11.5, lineHeight: 1.45, marginBottom: 3, opacity: b.status === 'APPROVED' ? 1 : 0.75 }}>
                  <RichText text={b.text} />
                  {b.status !== 'APPROVED' && (
                    <span style={{ fontFamily: 'var(--mono)', fontSize: 8.5, color: 'var(--muted)', marginLeft: 6 }}>
                      not yet approved
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
        {bullets.length === 0 && <div className="editorial muted">Nothing in the bank yet.</div>}
        {over && (
          <div style={{ borderTop: '2px dashed var(--rust)', color: 'var(--rust)', fontFamily: 'var(--mono)', fontSize: 10, fontWeight: 700, letterSpacing: '0.14em', paddingTop: 6, marginTop: 10 }}>
            PAGE 1 ENDS HERE — {lines - LAB_MAX_LINES} LINE(S) SPILL ONTO PAGE 2
          </div>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- info & context

/** The 8 context fields plus description — identical set for Projects and Experiences,
 *  fed by the same paste extractor either way. Only the header fields above them differ. */
function useContextFields(project: Project) {
  const [techStack, setTechStack] = useState(project.techStack ?? '');
  const [yourRole, setYourRole] = useState(project.yourRole ?? '');
  const [ownership, setOwnership] = useState(project.ownership ?? '');
  const [scaleImpact, setScaleImpact] = useState(project.scaleImpact ?? '');
  const [hardestProblem, setHardestProblem] = useState(project.hardestProblem ?? '');
  const [technicalDecisions, setTechnicalDecisions] = useState(project.technicalDecisions ?? '');
  const [userImpact, setUserImpact] = useState(project.userImpact ?? '');
  const [securityPosture, setSecurityPosture] = useState(project.securityPosture ?? '');
  const [description, setDescription] = useState(project.description ?? '');

  const setters: Record<ExtractField, (v: string) => void> = {
    techStack: setTechStack, yourRole: setYourRole, ownership: setOwnership,
    scaleImpact: setScaleImpact, hardestProblem: setHardestProblem,
    technicalDecisions: setTechnicalDecisions, userImpact: setUserImpact,
    securityPosture: setSecurityPosture, description: setDescription,
  };

  return {
    techStack, setTechStack, yourRole, setYourRole, ownership, setOwnership,
    scaleImpact, setScaleImpact, hardestProblem, setHardestProblem,
    technicalDecisions, setTechnicalDecisions, userImpact, setUserImpact,
    securityPosture, setSecurityPosture, description, setDescription,
    setters,
  };
}

function PasteSidebar({ setters }: { setters: Record<ExtractField, (v: string) => void> }) {
  const [pasteOpen, setPasteOpen] = useState(true);
  const [pasteText, setPasteText] = useState('');
  const [pasteMsg, setPasteMsg] = useState<string | null>(null);

  function parseAndFill() {
    setPasteMsg(null);
    const fields = parseExtract(pasteText);
    const keys = Object.keys(fields) as ExtractField[];
    if (keys.length === 0) {
      setPasteMsg('No recognized sections found. Paste the full extractor output.');
      return;
    }
    for (const k of keys) {
      const v = fields[k];
      if (v !== undefined) setters[k](v);
    }
    setPasteMsg(`Filled ${keys.length} field${keys.length === 1 ? '' : 's'} — review, then save.`);
    setPasteText('');
  }

  return (
    <div className="panel panel--inset stack-sm" style={{ position: 'sticky', top: 16, alignSelf: 'start' }}>
      <button
        type="button"
        style={{ all: 'unset', cursor: 'pointer', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        onClick={() => setPasteOpen(o => !o)}
      >
        <span className="label">PASTE EXTRACT</span>
        <span className="label muted">{pasteOpen ? '▲ COLLAPSE' : '▼ AUTO-FILL'}</span>
      </button>
      {!pasteOpen && (
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.05em' }}>
          Paste the Project Context Extractor output to auto-fill every field.
        </div>
      )}
      {pasteOpen && (
        <div className="stack-sm" style={{ marginTop: 8 }}>
          <textarea
            className="field__textarea"
            value={pasteText}
            onChange={e => setPasteText(e.target.value)}
            style={{ minHeight: 220 }}
            placeholder={"Paste the full extractor output here, e.g.\n\n## Tech Stack\nReact, PostgreSQL, AES-256-GCM…\n\n## Your Role\n…"}
          />
          <div className="row">
            <button type="button" className="btn btn--acid btn--sm" onClick={parseAndFill} disabled={!pasteText.trim()}>
              PARSE &amp; FILL
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setPasteText('')} disabled={!pasteText}>
              CLEAR
            </button>
          </div>
        </div>
      )}
      {pasteMsg && <div className="label muted" style={{ marginTop: 4 }}>{pasteMsg}</div>}
    </div>
  );
}

function ContextFieldset(f: ReturnType<typeof useContextFields>) {
  return (
    <>
      <label className="field">
        <div className="field__label">Tech stack</div>
        <input className="field__input" value={f.techStack} onChange={e => f.setTechStack(e.target.value)}
          placeholder="React, PostgreSQL, FastAPI, Redis, Docker…" />
      </label>
      <label className="field">
        <div className="field__label">Your role</div>
        <input className="field__input" value={f.yourRole} onChange={e => f.setYourRole(e.target.value)}
          placeholder="Solo / Lead / Contributor — e.g. 'Led backend, solo on infra'" />
      </label>
      <label className="field">
        <div className="field__label">What you owned end-to-end</div>
        <textarea className="field__textarea" value={f.ownership} onChange={e => f.setOwnership(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="I built the auth system, designed the DB schema, owned the data pipeline…" />
      </label>
      <label className="field">
        <div className="field__label">Scale & impact</div>
        <input className="field__input" value={f.scaleImpact} onChange={e => f.setScaleImpact(e.target.value)}
          placeholder="10k DAU, 200ms p99, reduced costs 40%, 3-person team…" />
      </label>
      <label className="field">
        <div className="field__label">Hardest problem solved</div>
        <textarea className="field__textarea" value={f.hardestProblem} onChange={e => f.setHardestProblem(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="Had to guarantee exactly-once delivery under network partitions…" />
      </label>
      <label className="field">
        <div className="field__label">Key technical decisions</div>
        <textarea className="field__textarea" value={f.technicalDecisions} onChange={e => f.setTechnicalDecisions(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="Chose Redis over Postgres pub/sub to cut write amplification…" />
      </label>
      <label className="field">
        <div className="field__label">Who it served &amp; why it mattered</div>
        <input className="field__input" value={f.userImpact} onChange={e => f.setUserImpact(e.target.value)}
          placeholder="40 brokerage tenants; an outage means lost listings…" />
      </label>
      <label className="field">
        <div className="field__label">Security &amp; compliance posture</div>
        <textarea className="field__textarea" value={f.securityPosture} onChange={e => f.setSecurityPosture(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="AES-256-GCM for tokens at rest; SOC 2 Type II…" />
      </label>
      <label className="field">
        <div className="field__label">Description / architecture overview</div>
        <textarea className="field__textarea" value={f.description} onChange={e => f.setDescription(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="3–5 sentences: lead each with one subsystem + its technique or number…" />
      </label>
    </>
  );
}

/** Experiences carry title/company/location/dates on top of the shared context set. */
function ExperienceContextForm({ project }: { project: Project }) {
  const f = useContextFields(project);
  return (
    <div className="labsplit">
      <PasteSidebar setters={f.setters} />
      <div className="stack">
        <div className="grid-2">
          <label className="field">
            <div className="field__label">Title</div>
            <input className="field__input" defaultValue={project.title ?? ''} placeholder="Software Engineer Intern" />
          </label>
          <label className="field">
            <div className="field__label">Company</div>
            <input className="field__input" defaultValue={project.company ?? ''} placeholder="Acme Corp" />
          </label>
          <label className="field">
            <div className="field__label">Location</div>
            <input className="field__input" defaultValue={project.location ?? ''} placeholder="Boston, MA" />
          </label>
          <label className="field">
            <div className="field__label">Dates</div>
            <input className="field__input" defaultValue={project.dates ?? ''} placeholder="Jun 2024 – Aug 2024" />
          </label>
        </div>
        <ContextFieldset {...f} />
        <div className="row">
          <button className="btn btn--acid">SAVE INFO</button>
        </div>
      </div>
    </div>
  );
}

/** Projects have no title/company/location/dates — just a name and an optional repo link. */
function ProjectContextForm({ project }: { project: Project }) {
  const f = useContextFields(project);
  return (
    <div className="labsplit">
      <PasteSidebar setters={f.setters} />
      <div className="stack">
        <label className="field">
          <div className="field__label">Name</div>
          <input className="field__input" defaultValue={project.name} placeholder="resume-pipeline" />
        </label>
        <label className="field">
          <div className="field__label">GitHub URL (optional — enriches AI context)</div>
          <input className="field__input" defaultValue={project.githubUrl ?? ''} placeholder="https://github.com/owner/repo" />
        </label>
        <ContextFieldset {...f} />
        <div className="row">
          <button className="btn btn--acid">SAVE INFO</button>
        </div>
      </div>
    </div>
  );
}
