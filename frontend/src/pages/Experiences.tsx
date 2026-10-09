import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { api, type Project } from '../lib/api';
import { BulletBar, EXIT, EmptyState, PageTitle, RowMenu, UndoBar, useUndoDelete } from '../components/ledger/shared';
import { endKey, looksCurrent, parseDates, tenure } from '../lib/dates';
import { usePrefersReducedMotion } from '../components/landing/useHeroLoop';
import { NewEntryForm } from '../components/ProjectsList/NewEntryForm';
import '../styles/story.css';

type Sort = 'recent' | 'name';

const SORTS: { key: Sort; label: string }[] = [
  { key: 'recent', label: 'Most recent first' },
  { key: 'name', label: 'A → Z' },
];

/** Bar scale for bullet counts (a full bank). */
const FULL_BANK = 12;

/** The explicit "I currently work here" flag; older rows fall back to what the dates text says. */
const isCurrent = (p: Project) => p.current ?? looksCurrent(p.dates);
const endKeyOf = (p: Project) => (isCurrent(p) ? Infinity : endKey(p.dates, p.createdAt));

export function Experiences() {
  const [rows, setRows] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('recent');
  const [showForm, setShowForm] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const reduced = usePrefersReducedMotion();

  const del = useUndoDelete(rows, setRows, (p) => `/api/projects/${p.id}`, (p, e) => setErr((e as Error)?.message || `Failed to delete "${p.title || p.name}"`), () => void load(true));

  async function load(quiet = false) {
    if (!quiet) setLoading(true);
    try { setRows((await api.get<Project[]>('/api/projects?kind=EXPERIENCE')).filter((p) => !del.isPending(p.id))); }
    catch (e) { setErr((e as Error).message || 'Could not load experiences'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const out = needle
      ? rows.filter((r) => (r.title ?? '').toLowerCase().includes(needle) || (r.company ?? '').toLowerCase().includes(needle))
      : rows;
    const by: Record<Sort, (a: Project, b: Project) => number> = {
      recent: (a, b) => endKeyOf(b) - endKeyOf(a) || (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
      name: (a, b) => (a.title || a.name).localeCompare(b.title || b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, q, sort]);

  async function dup(id: string) {
    setErr(null);
    try {
      await api.post(`/api/projects/${id}/duplicate`);
      await load(true);
    } catch (e) {
      setErr((e as Error)?.message || 'Failed to duplicate');
    }
  }

  function create(p: Project) {
    setRows((rs) => [p, ...rs]);
    setShowForm(false);
  }

  const has = rows.length > 0;

  return (
    <div className="shell ap-page pl-page">
      <PageTitle
        title="Experiences"
        count={has && <><strong>{rows.length}</strong> role{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + (r.bulletCount ?? 0), 0)}</strong> bullets in your bank</>}
        actions={has && !showForm && <button type="button" className="ap-btn ap-btn--acid" onClick={() => setShowForm(true)}>+ New experience</button>}
      />

      {err && <div className="err ap-err" role="alert">{err}</div>}

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
            <input className="ap-search" placeholder="Search role or company" aria-label="Search experiences" value={q} onChange={(e) => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort experiences" onChange={(e) => setSort(e.target.value as Sort)}>
              {SORTS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </div>
          <div className="ld-table" role="table" aria-label="Experiences">
            <div className="ld-row xp-row xp-row--head ld-row--head" role="row">
              <span role="columnheader">Role</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Dates</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map((p) => {
                const title = p.title || p.name;
                const bullets = p.bulletCount ?? 0;
                const now = isCurrent(p);
                const span = parseDates(now && !looksCurrent(p.dates) ? `${p.dates ?? ''} – Present` : p.dates);
                const meta = [p.company, p.location].filter(Boolean).join(' · ');
                return (
                  <motion.div
                    key={p.id}
                    className="ld-row xp-row"
                    role="row"
                    layout={reduced ? false : 'position'}
                    initial={reduced ? false : { opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0, transition: EXIT }}
                    transition={EXIT}
                  >
                    <span className="ld-name" role="cell">
                      <strong>
                        <Link to={`/experiences/${p.id}`} className="ld-link">{title}</Link>
                        {now && <span className="ex-now">NOW</span>}
                      </strong>
                      <span className="ld-desc">{p.dates && <span className="ex-inline">{p.dates} · </span>}{meta || '—'}</span>
                      {p.githubUrl && <span className="ld-desc ld-slug">{p.githubUrl.replace('https://github.com/', '')}</span>}
                    </span>
                    <span className="ld-bullets" role="cell">
                      <span className={`ld-bullets__n${bullets === 0 ? ' ex-zero' : ''}`}>{bullets}</span>
                      <BulletBar n={bullets} max={FULL_BANK} />
                    </span>
                    <span role="cell" className="ld-hide-sm ex-dates">
                      {p.dates ? <strong>{p.dates}</strong> : '—'}
                      {span && <span>{tenure(span)}</span>}
                    </span>
                    <RowMenu label={title} onDelete={() => del.remove(p.id)} onDuplicate={() => dup(p.id)} />
                  </motion.div>
                );
              })}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>
            )}
          </div>
        </>
      )}
      <UndoBar name={del.removed ? (del.removed.title || del.removed.name) : null} more={del.more} onUndo={del.undo} />
    </div>
  );
}
