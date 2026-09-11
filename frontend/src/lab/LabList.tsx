import { useMemo, useState } from 'react';
import type { ApplicationSummary } from '../lib/api';
import { LAB_SUMMARIES } from './fixtures';
import { LabChrome } from './LabChrome';

const OUTCOMES = ['applied', 'interview', 'offer', 'rejected'] as const;

type Sort = 'newest' | 'oldest' | 'fit' | 'page';

const SORTS: { key: Sort; label: string }[] = [
  { key: 'newest', label: 'Newest first' },
  { key: 'oldest', label: 'Oldest first' },
  { key: 'fit',    label: 'Best fit first' },
  { key: 'page',   label: 'Best page score first' },
];

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' });
}

/** Small labelled bar. A bare 0-100 number tells you nothing about which 0-100 it is. */
function ScoreBar({ k, v }: { k: string; v: number | null }) {
  return (
    <div className="scorepair__row">
      <span className="scorepair__key">{k}</span>
      <span className="scorepair__bar"><i style={{ width: `${v ?? 0}%` }} /></span>
      <span className="scorepair__num">{v ?? '—'}</span>
    </div>
  );
}

function RowMenu({ onDelete, onDuplicate }: { onDelete: () => void; onDuplicate: () => void }) {
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="rowmenu" onMouseLeave={() => { setOpen(false); setConfirming(false); }}>
      <button
        className="rowmenu__btn"
        aria-label="Row actions"
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
      >⋯</button>
      {open && (
        <div className="rowmenu__pop">
          <button onClick={() => { onDuplicate(); setOpen(false); }}>Duplicate</button>
          {confirming ? (
            <button className="is-danger" onClick={() => { onDelete(); setOpen(false); setConfirming(false); }}>
              Really delete?
            </button>
          ) : (
            <button className="is-danger" onClick={() => setConfirming(true)}>Delete</button>
          )}
        </div>
      )}
    </div>
  );
}

export function LabList() {
  const [rows, setRows] = useState<ApplicationSummary[]>(LAB_SUMMARIES);
  const [outcome, setOutcome] = useState<string>('');
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('newest');
  const [loading, setLoading] = useState(false);

  // Counts come from the whole set, so the tabs stay stable while you filter.
  const counts = useMemo(() => {
    const c: Record<string, number> = { '': rows.length };
    for (const o of OUTCOMES) c[o] = rows.filter(r => r.outcome === o).length;
    return c;
  }, [rows]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    let out = rows.filter(r => !outcome || r.outcome === outcome);
    if (needle) {
      out = out.filter(r =>
        (r.company ?? '').toLowerCase().includes(needle) ||
        (r.role ?? '').toLowerCase().includes(needle));
    }
    const by: Record<Sort, (a: ApplicationSummary, b: ApplicationSummary) => number> = {
      newest: (a, b) => b.createdAt.localeCompare(a.createdAt),
      oldest: (a, b) => a.createdAt.localeCompare(b.createdAt),
      fit:    (a, b) => (b.fitScore ?? -1) - (a.fitScore ?? -1),
      page:   (a, b) => (b.recruiterScore ?? -1) - (a.recruiterScore ?? -1),
    };
    return [...out].sort(by[sort]);
  }, [rows, outcome, q, sort]);

  function setRowOutcome(id: string, next: string) {
    setRows(rs => rs.map(r => (r.id === id ? { ...r, outcome: next } : r)));
  }

  return (
    <LabChrome
      title="Applications — list"
      note={
        <>
          Fixes from the audit: the two 0-100 scores now say what they measure and carry a
          legend; status is editable in place instead of only inside the detail page; the
          meaningless positional index is gone; delete moved out of the row link into a menu
          with an in-place confirm; filter tabs show counts from the whole set; search and
          sort exist. Every control below is live against placeholder state.
        </>
      }
    >
      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="Search company or role…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />

        <div className="filterset">
          <button className={outcome === '' ? 'is-on' : ''} onClick={() => setOutcome('')}>
            All <span className="filterset__count">{counts['']}</span>
          </button>
          {OUTCOMES.map(o => (
            <button key={o} className={outcome === o ? 'is-on' : ''} onClick={() => setOutcome(o)}>
              {o} <span className="filterset__count">{counts[o]}</span>
            </button>
          ))}
        </div>

        <select
          className="approw__status"
          style={{ width: 'auto' }}
          value={sort}
          onChange={e => setSort(e.target.value as Sort)}
        >
          {SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>

        <button
          className="minibtn"
          style={{ marginLeft: 'auto', padding: '8px 10px' }}
          onClick={() => { setLoading(true); window.setTimeout(() => setLoading(false), 1400); }}
        >
          Test loading state
        </button>
      </div>

      {/* The legend is the cheapest fix on this page: two numbers, one sentence each. */}
      <div className="lab-note" style={{ marginBottom: 16 }}>
        <b style={{ color: 'var(--ink)' }}>FIT</b> = how well you match the job.{' '}
        <b style={{ color: 'var(--ink)' }}>PAGE</b> = how well the rendered resume sells you.
        They move independently — a strong candidate can have a weak page.
      </div>

      <div className="applist">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="approw" aria-hidden>
                <div>
                  <div style={{ height: 17, width: '42%', background: 'var(--paper-2)', marginBottom: 6 }} />
                  <div style={{ height: 11, width: '28%', background: 'var(--paper-2)' }} />
                </div>
                <div style={{ height: 22, background: 'var(--paper-2)' }} />
                <div style={{ height: 11, background: 'var(--paper-2)' }} />
                <div style={{ height: 26, background: 'var(--paper-2)' }} />
                <div />
              </div>
            ))
          : shown.map(a => (
              <div className="approw" key={a.id}>
                {/* One stretched link covers the row; real buttons sit above it. */}
                <a className="approw__link" href={`/lab/detail?id=${a.id}`}>{a.company}</a>

                <div>
                  <h3 className="approw__title">{a.company ?? 'Untitled'}</h3>
                  <div className="approw__role">{a.role ?? 'No role recorded'}</div>
                </div>

                <div className="scorepair approw__scores">
                  <ScoreBar k="FIT" v={a.fitScore} />
                  <ScoreBar k="PAGE" v={a.recruiterScore} />
                </div>

                <div className="approw__date">{fmtDate(a.createdAt)}</div>

                <select
                  className={`approw__status approw__status--${a.outcome}`}
                  value={a.outcome}
                  aria-label={`Status for ${a.company}`}
                  onChange={e => setRowOutcome(a.id, e.target.value)}
                >
                  {OUTCOMES.map(o => <option key={o} value={o}>{o}</option>)}
                </select>

                <RowMenu
                  onDelete={() => setRows(rs => rs.filter(r => r.id !== a.id))}
                  onDuplicate={() => setRows(rs => [{ ...a, id: `${a.id}-copy`, createdAt: new Date().toISOString() }, ...rs])}
                />
              </div>
            ))}

        {!loading && shown.length === 0 && (
          <div style={{ padding: '44px 0', textAlign: 'center', borderBottom: 'var(--rule-thin)' }}>
            <div className="editorial" style={{ fontSize: 17, marginBottom: 6 }}>
              {q ? `Nothing matches “${q}”.` : `No applications marked ${outcome}.`}
            </div>
            <button className="minibtn" onClick={() => { setQ(''); setOutcome(''); }}>
              Clear filters
            </button>
          </div>
        )}
      </div>
    </LabChrome>
  );
}
