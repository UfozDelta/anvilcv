import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { APPLICATIONS, OUTCOMES, applied, type Outcome } from './appData';
import { BulletBar, EXIT, PageTitle, RowMenu, UndoBar, inert, useDemoPage, useUndoRows } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

type Sort = 'newest' | 'fit' | 'page';
const SORTS: { key: Sort; label: string }[] = [
  { key: 'newest', label: 'Newest first' },
  { key: 'fit', label: 'Best fit first' },
  { key: 'page', label: 'Best page first' },
];
const TABS: { key: Outcome | ''; label: string }[] = [{ key: '', label: 'All' }, ...OUTCOMES.map((o) => ({ key: o, label: o }))];

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

/** First run: two ways to start one, nothing else. */
function EmptyApps() {
  return (
    <section className="ap-empty">
      <h2 className="lp-display ap-empty__title">No applications yet.</h2>
      <p className="ap-empty__sub">Paste a job post and we tailor a one-page résumé from your bank.</p>
      <div className="ap-empty__paths">
        <button type="button" className="ap-path ap-path--primary">
          <span className="ap-path__num">01</span>
          <strong>Paste a job link</strong>
          <span>We read the post, rank your bullets, render the PDF.</span>
        </button>
        <button type="button" className="ap-path">
          <span className="ap-path__num">02</span>
          <strong>Browse open jobs</strong>
          <span>Pick a posting from the feed and tailor in one click.</span>
        </button>
      </div>
    </section>
  );
}

function ApplicationsLedger({ empty }: { empty: boolean }) {
  const { rows, setRows, remove, removed, more, undo } = useUndoRows(empty ? [] : APPLICATIONS);
  const reduced = usePrefersReducedMotion();
  const [q, setQ] = useState('');
  const [tab, setTab] = useState<Outcome | ''>('');
  const [sort, setSort] = useState<Sort>('newest');

  const setOutcome = (id: string, outcome: Outcome) => setRows((rs) => rs.map((r) => (r.id === id ? { ...r, outcome } : r)));
  const count = (o: Outcome) => rows.filter((r) => r.outcome === o).length;
  const counts: Record<string, number> = { '': rows.length, ...Object.fromEntries(OUTCOMES.map((o) => [o, count(o)])) };

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const by = {
      newest: (a: (typeof rows)[number], b: (typeof rows)[number]) => a.daysAgo - b.daysAgo,
      fit: (a: (typeof rows)[number], b: (typeof rows)[number]) => (b.fit ?? -1) - (a.fit ?? -1),
      page: (a: (typeof rows)[number], b: (typeof rows)[number]) => (b.page ?? -1) - (a.page ?? -1),
    }[sort];
    return rows
      .filter((r) => (!tab || r.outcome === tab) && (r.company + ' ' + r.role).toLowerCase().includes(needle))
      .sort(by);
  }, [rows, q, tab, sort]);

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Applications"
        count={rows.length > 0 && <><strong>{rows.length}</strong> sent · <strong>{count('interview')}</strong> interviewing · <strong>{count('offer')}</strong> offer{count('offer') === 1 ? '' : 's'}</>}
        actions={rows.length > 0 && <button type="button" className="ap-btn ap-btn--acid">+ New application</button>}
      />
      {rows.length === 0 ? <EmptyApps /> : (
        <>
          <div className="la-tabs" role="group" aria-label="Filter by outcome">
            {TABS.map(({ key, label }) => (
              <button key={key || 'all'} type="button" aria-pressed={tab === key} onClick={() => setTab(key)}>
                {label} <span className="la-tabs__n">{counts[key]}</span>
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
                    <strong><a href="#" onClick={inert} className="ld-link">{a.company}</a></strong>
                    <span className="ld-desc">
                      {a.role}
                      {a.fit !== null && <span className="la-fit-inline"> · Fit {a.fit}</span>}
                    </span>
                  </span>
                  <span className="la-scores ld-hide-sm" role="cell">
                    <Score k="FIT" v={a.fit} lead />
                    <Score k="PAGE" v={a.page} />
                  </span>
                  <span role="cell" className="ld-hide-sm ld-edited">{applied(a.daysAgo)}</span>
                  <span role="cell" className="ld-ctl">
                    <select
                      className="la-status"
                      data-outcome={a.outcome}
                      value={a.outcome}
                      aria-label={`Status for ${a.company}`}
                      onChange={(e) => setOutcome(a.id, e.target.value as Outcome)}
                    >
                      {OUTCOMES.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </span>
                  <RowMenu label={a.company} onDelete={() => remove(a.id)} />
                </motion.div>
              ))}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">
                {q ? <>Nothing matches “{q}”.</> : <>No applications marked {tab}.</>}{' '}
                <button type="button" onClick={() => { setQ(''); setTab(''); }}>Clear</button>
              </div>
            )}
          </div>
        </>
      )}
      <UndoBar name={removed?.company ?? null} more={more} onUndo={undo} />
    </div>
  );
}

/** /lab/applications — Applications home in the Ledger style, under the Strip nav. */
export function LabApplications() {
  const { empty, mountKey, replay } = useDemoPage();
  return (
    <div className="ap-root">
      <NavStrip />
      <ApplicationsLedger key={`${mountKey}-${empty}`} empty={empty} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
