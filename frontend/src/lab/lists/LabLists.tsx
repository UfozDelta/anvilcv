/* /lab/lists — the production Projects and Experiences ledgers, plus stack, bullet count and repo on each row. */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { NavStrip } from '../app/AppNav';
import { BulletBar, EXIT, PageTitle, RowMenu, UndoBar, ago } from '../../components/ledger/shared';
import { endKey, looksCurrent, parseDates, tenure } from '../../lib/dates';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { EXPERIENCES, PROJECTS, type ExpItem, type ProjectItem } from './data';
import './lists.css';

const OPEN = '/lab/story-flow';
const FULL_BANK = 12;
const SHOW_STACK = 3;

/** Local stand-in for useUndoDelete: drop at once, Undo for 5s, duplicate in place. */
function useLocalRows<T extends { id: string; name: string }>(init: T[]) {
  const [rows, setRows] = useState(init);
  const [removed, setRemoved] = useState<{ item: T; index: number } | null>(null);
  const timer = useRef<number>();
  useEffect(() => () => window.clearTimeout(timer.current), []);
  return {
    rows, removed,
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

export function LabLists() {
  return (
    <div className="ap-root">
      <NavStrip />
      <Projects />
      <Experiences />
    </div>
  );
}

type PSort = 'edited' | 'newest' | 'name';
const P_SORTS: { key: PSort; label: string }[] = [
  { key: 'edited', label: 'Most recently edited first' },
  { key: 'newest', label: 'Newest added first' },
  { key: 'name', label: 'A → Z' },
];

function Projects() {
  const list = useLocalRows<ProjectItem>(PROJECTS);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<PSort>('edited');
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

  return (
    <div className="shell ap-page">
      <PageTitle title="Projects" count={<><strong>{rows.length}</strong> project{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + r.bullets, 0)}</strong> bullets in your bank</>} />
      <div className="ap-tools">
        <input className="ap-search" placeholder="Search projects" aria-label="Search projects" value={q} onChange={e => setQ(e.target.value)} />
        <select className="la-sort" value={sort} aria-label="Sort projects" onChange={e => setSort(e.target.value as PSort)}>
          {P_SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>
      </div>
      <div className="ld-table" role="table" aria-label="Projects">
        <div className="ld-row pj-row pj-row--head ld-row--head" role="row">
          <span role="columnheader">Project</span>
          <span role="columnheader" className="ld-hide-sm">Stack</span>
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
              <span role="cell" className="ld-hide-sm"><Stack stack={p.stack} /></span>
              <Bullets n={p.bullets} />
              <span role="cell" className="ld-hide-sm"><Repo repo={p.repo} /></span>
              <span role="cell" className="ld-hide-sm ld-edited">{ago(p.updatedAt)}</span>
              <RowMenu label={p.name} onDelete={() => list.remove(p.id)} onDuplicate={() => list.dup(p.id)} />
            </motion.div>
          ))}
        </AnimatePresence>
        {shown.length === 0 && <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>}
      </div>
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

function Experiences() {
  const list = useLocalRows<ExpItem>(EXPERIENCES);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<XSort>('recent');
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

  return (
    <div className="shell ap-page">
      <PageTitle title="Experiences" count={<><strong>{rows.length}</strong> role{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + r.bullets, 0)}</strong> bullets in your bank</>} />
      <div className="ap-tools">
        <input className="ap-search" placeholder="Search role or company" aria-label="Search experiences" value={q} onChange={e => setQ(e.target.value)} />
        <select className="la-sort" value={sort} aria-label="Sort experiences" onChange={e => setSort(e.target.value as XSort)}>
          {X_SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>
      </div>
      <div className="ld-table" role="table" aria-label="Experiences">
        <div className="ld-row ls-xp-row ld-row--head" role="row">
          <span role="columnheader">Role</span>
          <span role="columnheader" className="ld-hide-sm">Stack</span>
          <span role="columnheader">Bullets</span>
          <span role="columnheader" className="ld-hide-sm">Repo</span>
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
                  <Inline stack={p.stack} repo={p.repo} />
                </span>
                <span role="cell" className="ld-hide-sm"><Stack stack={p.stack} /></span>
                <Bullets n={p.bullets} />
                <span role="cell" className="ld-hide-sm"><Repo repo={p.repo} /></span>
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
      <UndoBar name={list.removed?.item.name ?? null} onUndo={list.undo} />
    </div>
  );
}

/** First few terms; the rest as +N, all of them on hover. */
function Stack({ stack }: { stack: string[] }) {
  if (stack.length === 0) return <span className="pj-stack">—</span>;
  const more = stack.length - SHOW_STACK;
  return (
    <span className="pj-stack ls-stack" title={stack.join(', ')}>
      {stack.slice(0, SHOW_STACK).join(', ')}{more > 0 && <span className="ls-more"> +{more}</span>}
    </span>
  );
}

function Bullets({ n }: { n: number }) {
  return (
    <span className="ld-bullets" role="cell" title={`${n} bullet${n === 1 ? '' : 's'}`}>
      <span className={`ld-bullets__n${n === 0 ? ' ex-zero' : ''}`}>{n}</span>
      <BulletBar n={n} max={FULL_BANK} />
    </span>
  );
}

function Repo({ repo }: { repo: string | null }) {
  if (!repo) return <span className="ld-repo" data-state="none" title="No repo linked">—</span>;
  return <span className="ld-repo ls-repo" data-state="explored" title={`github.com/${repo}`}><GitHubMark />Repo</span>;
}

/** Phone only: the hidden stack and repo columns fold under the name. */
function Inline({ stack, repo }: { stack: string[]; repo: string | null }) {
  return (
    <span className="ls-inline">
      {repo && <span className="ls-repo" title={`github.com/${repo}`}><GitHubMark /></span>}
      <Stack stack={stack} />
    </span>
  );
}

function GitHubMark() {
  return (
    <svg className="ls-gh" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true" fill="currentColor">
      <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
    </svg>
  );
}
