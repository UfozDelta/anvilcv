import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { api, type ApplicationSummary } from '../lib/api';
import { BulletBar, EXIT, EmptyState, PageTitle, RowMenu, UndoBar, ago, useUndoDelete } from '../components/ledger/shared';
import { usePrefersReducedMotion } from '../components/landing/useHeroLoop';

const OUTCOMES = ['applied', 'oa', 'interview', 'offer', 'rejected', 'ghosted'] as const;
const outcomeLabel = (o: string) => (o === 'oa' ? 'OA' : o);
const TABS: { key: string; label: string }[] = [{ key: '', label: 'All' }, ...OUTCOMES.map((o) => ({ key: o, label: outcomeLabel(o) }))];

type Sort = 'newest' | 'oldest' | 'fit' | 'page';

const SORTS: { key: Sort; label: string }[] = [
  { key: 'newest', label: 'Newest first' },
  { key: 'oldest', label: 'Oldest first' },
  { key: 'fit', label: 'Best fit first' },
  { key: 'page', label: 'Best page first' },
];

/** One labelled 0–100 bar. A bare number never says which score it is. */
function Score({ k, v, lead }: { k: string; v: number | null; lead?: boolean }) {
  return (
    <span className={`la-score${lead ? ' la-score--lead' : ''}`}>
      <span className="la-score__k">{k}</span>
      <span className="la-score__n">{v ?? '—'}</span>
      <BulletBar n={v ?? 0} max={100} label={`${k} ${v ?? 'not scored'}`} />
    </span>
  );
}

export function Applications() {
  const [rows, setRows] = useState<ApplicationSummary[]>([]);
  const [outcome, setOutcome] = useState('');
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('newest');
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const reduced = usePrefersReducedMotion();

  const del = useUndoDelete(rows, setRows, (a) => `/api/applications/${a.id}`, (a, e) => setErr(`Could not delete ${a.company || 'application'}: ${(e as Error).message}`), () => { api.get<ApplicationSummary[]>('/api/applications').then(setRows).catch(() => {}); });

  // The whole set is fetched once and filtered here, so the tab counts describe
  // everything you have rather than whatever the last filter left behind.
  useEffect(() => {
    api.get<ApplicationSummary[]>('/api/applications')
      .then(setRows)
      .catch((e: Error) => setErr(`Could not load applications: ${e.message}`))
      .finally(() => setLoading(false));
  }, []);

  const counts = useMemo(() => {
    const c: Record<string, number> = { '': rows.length };
    for (const o of OUTCOMES) c[o] = rows.filter((r) => r.outcome === o).length;
    return c;
  }, [rows]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    let out = rows.filter((r) => !outcome || r.outcome === outcome);
    if (needle) {
      out = out.filter((r) =>
        (r.company ?? '').toLowerCase().includes(needle) ||
        (r.role ?? '').toLowerCase().includes(needle));
    }
    const by: Record<Sort, (a: ApplicationSummary, b: ApplicationSummary) => number> = {
      newest: (a, b) => b.createdAt.localeCompare(a.createdAt),
      oldest: (a, b) => a.createdAt.localeCompare(b.createdAt),
      fit: (a, b) => (b.fitScore ?? -1) - (a.fitScore ?? -1),
      page: (a, b) => (b.recruiterScore ?? -1) - (a.recruiterScore ?? -1),
    };
    return [...out].sort(by[sort]);
  }, [rows, outcome, q, sort]);

  /** Optimistic: the row reads back the server's value, and rolls back if the PATCH fails. */
  async function setRowOutcome(a: ApplicationSummary, next: string) {
    const prev = a.outcome;
    setRows((rs) => rs.map((r) => (r.id === a.id ? { ...r, outcome: next } : r)));
    setSavingId(a.id);
    setErr(null);
    try {
      const updated = await api.patch<{ outcome: string }>(`/api/applications/${a.id}`, { outcome: next });
      setRows((rs) => rs.map((r) => (r.id === a.id ? { ...r, outcome: updated.outcome } : r)));
    } catch (e) {
      setRows((rs) => rs.map((r) => (r.id === a.id ? { ...r, outcome: prev } : r)));
      setErr(`Could not update ${a.company || 'application'}: ${(e as Error).message}`);
    } finally { setSavingId(null); }
  }

  const has = rows.length > 0;
  const n = (o: string) => counts[o] ?? 0;

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Applications"
        count={has && <><strong>{rows.length}</strong> sent · <strong>{n('interview')}</strong> interviewing · <strong>{n('offer')}</strong> offer{n('offer') === 1 ? '' : 's'}</>}
        actions={has && <Link to="/new" className="ap-btn ap-btn--acid">+ New application</Link>}
      />

      {err && <div className="err ap-err" role="alert">{err}</div>}

      {loading ? <span className="spinner">LOADING</span> : !has ? (
        <EmptyState title="No applications yet." sub="Paste a job post and we tailor a one-page résumé from your bank.">
          <div className="ap-empty__paths">
            <Link to="/new" className="ap-path ap-path--primary">
              <span className="ap-path__num">01</span>
              <strong>Paste a job link</strong>
              <span>We read the post, rank your bullets, render the PDF.</span>
            </Link>
            <Link to="/jobs" className="ap-path">
              <span className="ap-path__num">02</span>
              <strong>Browse open jobs</strong>
              <span>Pick a posting from the feed and tailor in one click.</span>
            </Link>
          </div>
        </EmptyState>
      ) : (
        <>
          <div className="la-tabs" role="group" aria-label="Filter by outcome">
            {TABS.map(({ key, label }) => (
              <button key={key || 'all'} type="button" aria-pressed={outcome === key} onClick={() => setOutcome(key)}>
                {label} <span className="la-tabs__n">{n(key)}</span>
              </button>
            ))}
          </div>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search company or role" aria-label="Search applications" value={q} onChange={(e) => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort applications" onChange={(e) => setSort(e.target.value as Sort)}>
              {SORTS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
            <span className="ap-tools__hint"><b>FIT</b> you vs. the job · <b>PAGE</b> how the résumé reads</span>
          </div>
          <div className="ld-table" role="table" aria-label="Applications">
            <div className="ld-row la-row la-row--head ld-row--head" role="row">
              <span role="columnheader">Company</span>
              <span role="columnheader" className="ld-hide-sm">Fit / Page</span>
              <span role="columnheader" className="ld-hide-sm">Sent</span>
              <span role="columnheader">Status</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map((a) => (
                <motion.div
                  key={a.id}
                  className="ld-row la-row"
                  role="row"
                  layout={reduced ? false : 'position'}
                  initial={reduced ? false : { opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, transition: EXIT }}
                  transition={EXIT}
                >
                  <span className="ld-name" role="cell">
                    <strong><Link to={`/applications/${a.id}`} className="ld-link">{a.company || 'Untitled'}</Link></strong>
                    <span className="ld-desc">
                      {a.role || 'No role recorded'}
                      {a.fitScore !== null && <span className="la-fit-inline"> · Fit {a.fitScore}</span>}
                    </span>
                  </span>
                  <span className="la-scores ld-hide-sm" role="cell">
                    <Score k="FIT" v={a.fitScore} lead />
                    <Score k="PAGE" v={a.recruiterScore} />
                  </span>
                  <span role="cell" className="ld-hide-sm ld-edited">{ago(a.createdAt)}</span>
                  <span role="cell" className="ld-ctl">
                    <select
                      className="la-status"
                      data-outcome={a.outcome}
                      value={a.outcome}
                      disabled={savingId === a.id}
                      aria-label={`Status for ${a.company || 'application'}`}
                      onChange={(e) => setRowOutcome(a, e.target.value)}
                    >
                      {/* An outcome the backend set but this list does not offer still renders. */}
                      {!(OUTCOMES as readonly string[]).includes(a.outcome) && <option value={a.outcome}>{a.outcome}</option>}
                      {OUTCOMES.map((o) => <option key={o} value={o}>{outcomeLabel(o)}</option>)}
                    </select>
                  </span>
                  <RowMenu label={a.company || 'application'} onDelete={() => del.remove(a.id)} />
                </motion.div>
              ))}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">
                {q ? <>Nothing matches “{q}”.</> : <>No applications marked {outcomeLabel(outcome)}.</>}{' '}
                <button type="button" onClick={() => { setQ(''); setOutcome(''); }}>Clear</button>
              </div>
            )}
          </div>
        </>
      )}
      <UndoBar name={del.removed ? (del.removed.company || 'application') : null} more={del.more} onUndo={del.undo} />
    </div>
  );
}
