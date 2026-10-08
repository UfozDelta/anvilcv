/* /lab/lists — the production Projects and Experiences ledgers, plus stack, bullet count and repo on each row. */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { NavStrip } from '../app/AppNav';
import { NewEntryForm } from '../projectsList/NewEntryForm';
import { BulletBar, EXIT, EmptyState, PageTitle, RowMenu, UndoBar, ago } from '../../components/ledger/shared';
import { endKey, looksCurrent, parseDates, tenure } from '../../lib/dates';
import { type Project } from '../../lib/api';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { GitHubMark } from '../../components/ledger/icons';
import { BULLET_BAR_MAX } from '../../lib/config';
import { EXPERIENCES, PROJECTS, type ExpItem, type ProjectItem } from './data';
import './lists.css';

const OPEN = '/lab/story-flow';
const SHOW_STACK = 4;
const slug = (url?: string | null) => (url ? url.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '') : null);

/** Lab-only: which load state the pages show. */
type Mode = 'live' | 'loading' | 'empty' | 'error';
const MODES: { key: Mode; label: string }[] = [
  { key: 'live', label: 'Loaded' },
  { key: 'loading', label: 'Loading' },
  { key: 'empty', label: 'Empty' },
  { key: 'error', label: 'Load error' },
];

/** Local stand-in for useUndoDelete: drop at once, Undo for 5s, duplicate in place. */
function useLocalRows<T extends { id: string; name: string }>(init: T[]) {
  const [rows, setRows] = useState(init);
  const [removed, setRemoved] = useState<{ item: T; index: number } | null>(null);
  const timer = useRef<number>();
  useEffect(() => () => window.clearTimeout(timer.current), []);
  return {
    rows, removed,
    add(item: T) { setRows(rs => [item, ...rs]); },
    remove(id: string) {
      const index = rows.findIndex(r => r.id === id);
      if (index < 0) return;
      setRemoved({ item: rows[index], index });
      setRows(rs => rs.filter(r => r.id !== id));
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => setRemoved(null), 5000);
    },
    undo() {
      if (!removed) return;
      setRows(rs => [...rs.slice(0, removed.index), removed.item, ...rs.slice(removed.index)]);
      setRemoved(null);
    },
    dup(id: string) {
      setRows(rs => rs.flatMap(r => (r.id === id ? [r, { ...r, id: `${r.id}-${Date.now()}`, name: `${r.name} (copy)`, updatedAt: new Date().toISOString() }] : [r])));
    },
  };
}

/** Lab-only: a pretend load that settles after a beat, or never in Loading mode. */
function useLoad(mode: Mode) {
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (mode === 'loading') return;
    const t = window.setTimeout(() => setLoading(false), 300);
    return () => window.clearTimeout(t);
  }, [mode]);
  return loading;
}

export function LabLists() {
  const [mode, setMode] = useState<Mode>('live');
  return (
    <div className="ap-root ls-page">
      <NavStrip />
      <div className="shell ls-lab">
        <label>
          Lab state
          <select className="la-sort" value={mode} onChange={e => setMode(e.target.value as Mode)}>
            {MODES.map(m => <option key={m.key} value={m.key}>{m.label}</option>)}
          </select>
        </label>
      </div>
      <Projects key={`p-${mode}`} mode={mode} />
      <Experiences key={`x-${mode}`} mode={mode} />
    </div>
  );
}

type PSort = 'edited' | 'newest' | 'name';
const P_SORTS: { key: PSort; label: string }[] = [
  { key: 'edited', label: 'Most recently edited first' },
  { key: 'newest', label: 'Newest added first' },
  { key: 'name', label: 'A → Z' },
];

function Projects({ mode }: { mode: Mode }) {
  const list = useLocalRows<ProjectItem>(mode === 'live' ? PROJECTS : []);
  const loading = useLoad(mode);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<PSort>('edited');
  const [showForm, setShowForm] = useState(false);
  const [importing, setImporting] = useState(false);
  const reduced = usePrefersReducedMotion();
  const { rows } = list;

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const out = needle ? rows.filter(r => r.name.toLowerCase().includes(needle)) : rows;
    const by: Record<PSort, (a: ProjectItem, b: ProjectItem) => number> = {
      edited: (a, b) => b.updatedAt.localeCompare(a.updatedAt),
      newest: (a, b) => b.createdAt.localeCompare(a.createdAt),
      name: (a, b) => a.name.localeCompare(b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, q, sort]);

  const has = rows.length > 0;
  const adding = showForm || importing;
  const openForm = () => { setImporting(false); setShowForm(true); };
  const openImport = () => { setShowForm(false); setImporting(true); };
  const close = () => { setShowForm(false); setImporting(false); };
  const create = (p: Project) => {
    const now = new Date().toISOString();
    list.add({ id: p.id, name: p.name, description: p.description ?? '', stack: [], bullets: 0, repo: slug(p.githubUrl), repoReady: false, createdAt: now, updatedAt: now });
    close();
  };

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Projects"
        count={has && <><strong>{rows.length}</strong> project{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + r.bullets, 0)}</strong> bullets in your bank</>}
        actions={has && !adding && (
          <>
            <button type="button" className="ap-btn ap-btn--ghost" onClick={openImport}>Import from GitHub</button>
            <button type="button" className="ap-btn ap-btn--acid" onClick={openForm}>+ New project</button>
          </>
        )}
      />

      {mode === 'error' && !loading && <div className="err ap-err" role="alert">Could not load projects</div>}

      {/* The lab has no GitHub API, so Import opens the same offline form (it takes a repo URL). */}
      {adding && <div className="ap-form"><NewEntryForm kind="PROJECT" onCreate={create} onCancel={close} /></div>}

      {loading ? <span className="spinner">LOADING</span> : !has ? (
        !adding && (
          <EmptyState title="No projects yet." sub="Every tailored résumé is built from your bank. Start with one project.">
            <div className="ap-empty__paths">
              <button type="button" className="ap-path ap-path--primary" onClick={openImport}>
                <span className="ap-path__num">01</span>
                <strong>Import a GitHub repo</strong>
                <span>We read the code and draft bullets for you.</span>
              </button>
              <button type="button" className="ap-path" onClick={openForm}>
                <span className="ap-path__num">02</span>
                <strong>Write one by hand</strong>
                <span>Name, a sentence, your role. Bullets come after.</span>
              </button>
            </div>
          </EmptyState>
        )
      ) : (
        <>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search projects" aria-label="Search projects" value={q} onChange={e => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort projects" onChange={e => setSort(e.target.value as PSort)}>
              {P_SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </div>
          <div className="ld-table" role="table" aria-label="Projects">
            <div className="ld-row pj-row pj-row--head ld-row--head" role="row">
              <span role="columnheader">Project</span>
              <span role="columnheader" className="ld-hide-sm ls-stack-col">Stack</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Repo</span>
              <span role="columnheader" className="ld-hide-sm">Edited</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map(p => (
                <motion.div key={p.id} className="ld-row pj-row" role="row" layout={reduced ? false : 'position'}
                  initial={reduced ? false : { opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, transition: EXIT }} transition={EXIT}>
                  <span className="ld-name" role="cell">
                    <strong><Link to={OPEN} className="ld-link">{p.name}</Link></strong>
                    <span className="ld-desc">{p.description}</span>
                    <Inline stack={p.stack} repo={p.repo} />
                  </span>
                  <span role="cell" className="ld-hide-sm ls-stack-col"><Stack stack={p.stack} /></span>
                  <Bullets n={p.bullets} />
                  <span role="cell" className="ld-hide-sm"><Repo repo={p.repo} ready={p.repoReady} /></span>
                  <span role="cell" className="ld-hide-sm ld-edited">{ago(p.updatedAt)}</span>
                  <RowMenu label={p.name} onDelete={() => list.remove(p.id)} onDuplicate={() => list.dup(p.id)} />
                </motion.div>
              ))}
            </AnimatePresence>
            {shown.length === 0 && <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>}
          </div>
        </>
      )}
      <UndoBar name={list.removed?.item.name ?? null} onUndo={list.undo} />
    </div>
  );
}

type XSort = 'recent' | 'name';
const X_SORTS: { key: XSort; label: string }[] = [
  { key: 'recent', label: 'Most recent first' },
  { key: 'name', label: 'A → Z' },
];
const isCurrent = (p: ExpItem) => p.current ?? looksCurrent(p.dates);
const endKeyOf = (p: ExpItem) => (isCurrent(p) ? Infinity : endKey(p.dates, p.createdAt));

function Experiences({ mode }: { mode: Mode }) {
  const list = useLocalRows<ExpItem>(mode === 'live' ? EXPERIENCES : []);
  const loading = useLoad(mode);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<XSort>('recent');
  const [showForm, setShowForm] = useState(false);
  const reduced = usePrefersReducedMotion();
  const { rows } = list;

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const out = needle ? rows.filter(r => r.name.toLowerCase().includes(needle) || r.company.toLowerCase().includes(needle)) : rows;
    const by: Record<XSort, (a: ExpItem, b: ExpItem) => number> = {
      recent: (a, b) => endKeyOf(b) - endKeyOf(a) || b.createdAt.localeCompare(a.createdAt),
      name: (a, b) => a.name.localeCompare(b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, q, sort]);

  const has = rows.length > 0;
  const create = (p: Project) => {
    const now = new Date().toISOString();
    list.add({ id: p.id, name: p.title || p.name, company: p.company ?? '', location: p.location ?? undefined, dates: p.dates ?? '', stack: [], bullets: 0, repo: null, createdAt: now, updatedAt: now });
    setShowForm(false);
  };

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Experiences"
        count={has && <><strong>{rows.length}</strong> role{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + r.bullets, 0)}</strong> bullets in your bank</>}
        actions={has && !showForm && <button type="button" className="ap-btn ap-btn--acid" onClick={() => setShowForm(true)}>+ New experience</button>}
      />

      {mode === 'error' && !loading && <div className="err ap-err" role="alert">Could not load experiences</div>}

      {showForm && <div className="ap-form"><NewEntryForm kind="EXPERIENCE" onCreate={create} onCancel={() => setShowForm(false)} /></div>}

      {loading ? <span className="spinner">LOADING</span> : !has ? (
        !showForm && (
          <EmptyState title="No experience yet." sub="Jobs, internships, research. Tailored résumés pull their work-history bullets from here.">
            <button type="button" className="ap-btn ap-btn--acid" onClick={() => setShowForm(true)}>+ New experience</button>
          </EmptyState>
        )
      ) : (
        <>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search role or company" aria-label="Search experiences" value={q} onChange={e => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort experiences" onChange={e => setSort(e.target.value as XSort)}>
              {X_SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </div>
          <div className="ld-table" role="table" aria-label="Experiences">
            <div className="ld-row ls-xp-row ld-row--head" role="row">
              <span role="columnheader">Role</span>
              <span role="columnheader" className="ld-hide-sm ls-stack-col">Stack</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Dates</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map(p => {
                const now = isCurrent(p);
                const span = parseDates(p.dates);
                const meta = [p.company, p.location].filter(Boolean).join(' · ');
                return (
                  <motion.div key={p.id} className="ld-row ls-xp-row" role="row" layout={reduced ? false : 'position'}
                    initial={reduced ? false : { opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, transition: EXIT }} transition={EXIT}>
                    <span className="ld-name" role="cell">
                      <strong>
                        <Link to={OPEN} className="ld-link">{p.name}</Link>
                        {now && <span className="ex-now">NOW</span>}
                      </strong>
                      <span className="ld-desc"><span className="ex-inline">{p.dates} · </span>{meta || '—'}</span>
                      {p.repo && <RepoSlug repo={p.repo} />}
                      <Inline stack={p.stack} repo={null} />
                    </span>
                    <span role="cell" className="ld-hide-sm ls-stack-col"><Stack stack={p.stack} /></span>
                    <Bullets n={p.bullets} />
                    <span role="cell" className="ld-hide-sm ex-dates">
                      <strong>{p.dates}</strong>
                      {span && <span>{tenure(span)}</span>}
                    </span>
                    <RowMenu label={p.name} onDelete={() => list.remove(p.id)} onDuplicate={() => list.dup(p.id)} />
                  </motion.div>
                );
              })}
            </AnimatePresence>
            {shown.length === 0 && <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>}
          </div>
        </>
      )}
      <UndoBar name={list.removed?.item.name ?? null} onUndo={list.undo} />
    </div>
  );
}

/** First few terms, wrapping to two lines at most; the rest as a plain +N. */
function Stack({ stack }: { stack: string[] }) {
  if (stack.length === 0) return <span className="pj-stack">—</span>;
  const more = stack.length - SHOW_STACK;
  return (
    <span className="pj-stack ls-stack">
      {stack.slice(0, SHOW_STACK).join(', ')}{more > 0 && <span className="ls-more"> +{more}</span>}
    </span>
  );
}

function Bullets({ n }: { n: number }) {
  return (
    <span className="ld-bullets" role="cell">
      <span className={`ld-bullets__n${n === 0 ? ' ex-zero' : ''}`}>{n}</span>
      <BulletBar n={n} max={BULLET_BAR_MAX} />
    </span>
  );
}

/** Repo cell: the slug once read, "Reading…" while it is being read, — when none. */
function Repo({ repo, ready }: { repo: string | null; ready?: boolean }) {
  if (!repo) return <span className="ld-repo" data-state="none">—<span className="sr-only">No repo</span></span>;
  if (ready === false) return <span className="ld-repo" data-state="exploring">Reading…</span>;
  return <span className="ld-repo ls-repo" data-state="explored"><RepoSlug repo={repo} /></span>;
}

function RepoSlug({ repo }: { repo: string }) {
  return (
    <span className="ls-slug">
      <GitHubMark className="ls-gh" /><span className="sr-only">Repo linked: </span><span className="ls-slug__t">{repo}</span>
    </span>
  );
}

/** Narrow screens: the hidden stack (≤1024px) and repo (≤760px) columns fold under the name. */
function Inline({ stack, repo }: { stack: string[]; repo: string | null }) {
  return (
    <span className="ls-inline">
      {repo && <span className="ls-inline__repo"><RepoSlug repo={repo} /></span>}
      <Stack stack={stack} />
    </span>
  );
}
