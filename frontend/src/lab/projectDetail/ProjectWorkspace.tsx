import { useMemo, useState } from 'react';
import { CATEGORIES, type Bullet, type Project } from '../../lib/api';
import { estimatedLines, fitOf, needsRefit, type Fit } from '../../lib/bulletLength';
import { LAB_BULLETS, LAB_CFG, LAB_MAX_LINES, LAB_PROJECTS } from '../fixtures';
import { BulletsTab } from './BulletsTab';
import { GenerateTab } from './GenerateTab';
import { ExperienceContextForm, ProjectContextForm } from './InfoTab';
import {
  BUCKET_TEMPLATES, CATEGORY_TO_DISPLAY, deriveLenses, DISPLAY_GROUPS,
  NARRATIVE_CATEGORY, narrativeBullet, TECH_CATEGORY, TECH_TEMPLATES, type Lens,
} from '../../lib/lensDerivation';

type Tab = 'bullets' | 'generate' | 'info';
type StatusTab = 'bank' | 'approved';
type SortMode = 'category' | 'date';

const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));

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

export function seedBank(projectId: string): Bullet[] {
  return [...LAB_BULLETS, ...EXTRA_PENDING].filter(b => b.projectId === projectId);
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

export function ProjectWorkspace({ project, isExperience }: { project: Project; isExperience: boolean }) {
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
  const suggestedLens: Lens | null = useMemo(() => {
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
          <BulletsTab
            statusTab={statusTab} setStatusTab={setStatusTab}
            bankCount={bankCount} approvedCount={approvedCount}
            sortMode={sortMode} setSortMode={setSortMode}
            offBand={offBand} refitting={refitting} refit={refit} refitMsg={refitMsg}
            adding={adding} setAdding={setAdding}
            editingId={editingId} setEditingId={setEditingId}
            previewOpen={previewOpen} setPreviewOpen={setPreviewOpen}
            previewBusy={previewBusy} setPreviewBusy={setPreviewBusy}
            pageLines={pageLines} overBudget={overBudget} pageBullets={pageBullets}
            byCategory={byCategory} visible={visible}
            filterCat={filterCat} setFilterCat={setFilterCat}
            closedGroups={closedGroups} toggleGroup={toggleGroup}
            flatByDate={flatByDate} displayed={displayed}
            addBullet={addBullet} saveEdit={saveEdit} deleteBullet={deleteBullet} toggleApprove={toggleApprove}
          />
        )}

        {tab === 'generate' && (
          <GenerateTab
            project={project} isExperience={isExperience}
            filledCount={filledCount} setTab={setTab}
            generating={generating} genPct={genPct}
            picked={picked} setPicked={setPicked} pickedBuckets={pickedBuckets}
            togglePick={togglePick} toggleBucket={toggleBucket}
            lenses={lenses} lensCount={lensCount} displayGroupCount={displayGroupCount}
            generateBank={generateBank}
            customText={customText} setCustomText={setCustomText}
            customAdding={customAdding} addCustomBullet={addCustomBullet} customMsg={customMsg}
            suggestedLens={suggestedLens}
          />
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
