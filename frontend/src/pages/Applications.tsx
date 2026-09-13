import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type ApplicationSummary } from '../lib/api';
import { Section } from '../components/Section';

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

/** Delete lives here, not in the row, so it can never be hit while aiming for the link. */
function RowMenu({ onDelete }: { onDelete: () => void }) {
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

export function Applications() {
  const [rows, setRows] = useState<ApplicationSummary[]>([]);
  const [outcome, setOutcome] = useState('');
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('newest');
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [deletingIds, setDeletingIds] = useState<Set<string>>(new Set());

  // The whole set is fetched once and filtered here, so the tab counts describe
  // everything you have rather than whatever the last filter left behind.
  async function load() {
    setLoading(true);
    try {
      setRows(await api.get<ApplicationSummary[]>('/api/applications'));
    } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

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

  /** Optimistic: the row reads back the server's value, and rolls back if the PATCH fails. */
  async function setRowOutcome(a: ApplicationSummary, next: string) {
    const prev = a.outcome;
    setRows(rs => rs.map(r => (r.id === a.id ? { ...r, outcome: next } : r)));
    setSavingId(a.id);
    setErr(null);
    try {
      const updated = await api.patch<{ outcome: string }>(`/api/applications/${a.id}`, { outcome: next });
      setRows(rs => rs.map(r => (r.id === a.id ? { ...r, outcome: updated.outcome } : r)));
    } catch (e) {
      setRows(rs => rs.map(r => (r.id === a.id ? { ...r, outcome: prev } : r)));
      setErr(`Could not update ${a.company || 'application'}: ${(e as Error).message}`);
    } finally { setSavingId(null); }
  }

  async function deleteApp(a: ApplicationSummary) {
    setErr(null);
    setDeletingIds(s => new Set(s).add(a.id));
    await new Promise(r => setTimeout(r, 450));
    try {
      await api.del(`/api/applications/${a.id}`);
      setRows(rs => rs.filter(r => r.id !== a.id));
    } catch (e) {
      setErr(`Could not delete ${a.company || 'application'}: ${(e as Error).message}`);
    } finally {
      setDeletingIds(s => { const n = new Set(s); n.delete(a.id); return n; });
    }
  }

  return (
    <div className="shell">
      <Section num="03" title="Applications" count={rows.length} />

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
          aria-label="Sort applications"
          onChange={e => setSort(e.target.value as Sort)}
        >
          {SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>
      </div>

      {/* Two 0-100 numbers that measure different things need one sentence each. */}
      <div className="legend" style={{ marginBottom: 16 }}>
        <b>FIT</b> = how well you match the job. <b>PAGE</b> = how well the rendered resume
        sells you. They move independently — a strong candidate can have a weak page.
      </div>

      {err && <div className="err" style={{ marginBottom: 12 }}>{err}</div>}

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
              <div className={`approw${deletingIds.has(a.id) ? ' approw--removing' : ''}`} key={a.id}>
                {/* One stretched link covers the row; real controls sit above it. */}
                <Link className="approw__link" to={`/applications/${a.id}`}>{a.company || 'Untitled'}</Link>

                <div>
                  <h3 className="approw__title">{a.company || 'Untitled'}</h3>
                  <div className="approw__role">{a.role || 'No role recorded'}</div>
                </div>

                <div className="scorepair approw__scores">
                  <ScoreBar k="FIT" v={a.fitScore} />
                  <ScoreBar k="PAGE" v={a.recruiterScore} />
                </div>

                <div className="approw__date">{fmtDate(a.createdAt)}</div>

                <select
                  className={`approw__status approw__status--${a.outcome}`}
                  value={a.outcome}
                  disabled={savingId === a.id}
                  aria-label={`Status for ${a.company || 'application'}`}
                  onChange={e => setRowOutcome(a, e.target.value)}
                >
                  {/* An outcome the backend set but this list does not offer still renders. */}
                  {!OUTCOMES.includes(a.outcome as typeof OUTCOMES[number]) && (
                    <option value={a.outcome}>{a.outcome}</option>
                  )}
                  {OUTCOMES.map(o => <option key={o} value={o}>{o}</option>)}
                </select>

                <RowMenu onDelete={() => deleteApp(a)} />
              </div>
            ))}

        {!loading && shown.length === 0 && (
          <div style={{ padding: '44px 0', textAlign: 'center', borderBottom: 'var(--rule-thin)' }}>
            <div className="editorial" style={{ fontSize: 17, marginBottom: 6 }}>
              {rows.length === 0
                ? 'No applications yet. Start with 04 — NEW APPLICATION.'
                : q
                  ? `Nothing matches “${q}”.`
                  : `No applications marked ${outcome}.`}
            </div>
            {rows.length > 0 && (
              <button className="minibtn" onClick={() => { setQ(''); setOutcome(''); }}>
                Clear filters
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
