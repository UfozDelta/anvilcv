import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, type ApplicationResponse, type Bullet, type RankedBullet } from '../lib/api';
import { Section } from '../components/Section';
import { useEventLog } from '../lib/useEventLog';
import { EventLog } from '../components/EventLog';

export function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const [app, setApp] = useState<ApplicationResponse | null>(null);
  const [bullets, setBullets] = useState<Record<string, Bullet>>({});
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [rerendering, setRerendering] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const { stream, state: logState, reset: resetLog } = useEventLog();

  async function load() {
    if (!id) return;
    const a = await api.get<ApplicationResponse>(`/api/applications/${id}`);
    setApp(a);
    setSelectedIds(new Set(a.selectedBulletIds));

    // Pull all bullets referenced in the ranking so we can display text
    const ranking = parseRanking(a.bulletRanking);
    const ids = ranking.map(r => r.bulletId);
    if (ids.length > 0) {
      // No batch endpoint; pull all bullets per project. Easier: pull all projects then their bullets.
      const projects = await api.get<{ id: string }[]>(`/api/projects`);
      const all: Bullet[] = [];
      for (const p of projects) {
        const bs = await api.get<Bullet[]>(`/api/projects/${p.id}/bullets`);
        all.push(...bs);
      }
      const map: Record<string, Bullet> = {};
      all.forEach(b => { map[b.id] = b; });
      setBullets(map);
    }
  }
  useEffect(() => { load(); }, [id]);

  const ranking: RankedBullet[] = useMemo(() => {
    if (!app) return [];
    return parseRanking(app.bulletRanking).sort((a, b) => a.rank - b.rank);
  }, [app]);

  async function setOutcome(o: string) {
    if (!app) return;
    setBusy(true);
    try {
      const updated = await api.patch<ApplicationResponse>(`/api/applications/${app.id}`, { outcome: o });
      setApp(updated);
    } finally { setBusy(false); }
  }

  function toggleBullet(bid: string) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(bid)) next.delete(bid); else next.add(bid);
      return next;
    });
  }

  async function rerender() {
    if (!app) return;
    setErr(null);
    resetLog();
    setRerendering(true);
    try {
      // SSE endpoint streams LaTeX render + tectonic compile progress in real time.
      await stream(
        `/api/applications/${app.id}/rerender/stream`,
        { selectedBulletIds: Array.from(selectedIds) }
      );
      // Reload app state after pipeline completes so PDF link is fresh.
      await load();
    } catch (e: any) {
      setErr(e?.message || 'Re-render failed');
    } finally {
      setRerendering(false);
    }
  }

  if (!app) return <div className="shell"><span className="spinner">LOADING</span></div>;

  const pdfUrl = api.pdfUrl(`/api/applications/${app.id}/pdf`);
  const ogSelection = new Set(app.selectedBulletIds);
  const dirty = !setsEqual(selectedIds, ogSelection);

  return (
    <div className="shell">
      <div className="row row--between row--centered" style={{ marginBottom: 8 }}>
        <Link to="/applications" className="label muted" style={{ textDecoration: 'none' }}>← ALL APPLICATIONS</Link>
        <span className={`outcome outcome--${app.outcome}`}>{app.outcome}</span>
      </div>

      <h1 className="display" style={{ fontSize: 64, margin: '8px 0 4px', lineHeight: 0.95 }}>
        {app.company || 'Untitled'}
      </h1>
      <div className="editorial muted" style={{ fontSize: 20, marginBottom: 24 }}>
        {app.role || 'role'} · emphasis: <em>{app.roleEmphasis}</em>
      </div>

      <div className="row" style={{ gap: 8, marginBottom: 28, flexWrap: 'wrap' }}>
        {['applied', 'interview', 'offer', 'rejected'].map(o => (
          <button
            key={o}
            className={`btn btn--sm ${app.outcome === o ? '' : 'btn--ghost'}`}
            style={app.outcome === o ? outcomeStyle(o) : { border: '2px solid var(--ink)' }}
            disabled={busy}
            onClick={() => setOutcome(o)}
          >MARK {o.toUpperCase()}</button>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.05fr', gap: 32, alignItems: 'start' }}>

        {/* LEFT: ranked bullets */}
        <div>
          <Section num="04.A" title="Ranked Bullets" count={ranking.length} />
          <div className="label muted" style={{ marginBottom: 10 }}>
            CLICK A RANK TO TOGGLE INCLUSION · {selectedIds.size} SELECTED
          </div>

          <div>
            {ranking.map(r => {
              const b = bullets[r.bulletId];
              const isSel = selectedIds.has(r.bulletId);
              return (
                <div key={r.bulletId} className="bullet">
                  <div
                    className={`bullet__rank ${isSel ? 'bullet__rank--selected' : ''}`}
                    onClick={() => toggleBullet(r.bulletId)}
                    title={isSel ? 'Click to exclude' : 'Click to include'}
                  >
                    #{String(r.rank).padStart(2, '0')}
                  </div>
                  <div>
                    <div className="bullet__text">{b?.text || <em className="muted">— bullet missing —</em>}</div>
                    <div className="bullet__why">{r.why}</div>
                    {b && (
                      <div className="bullet__tags">
                        {b.tags.map(t => <span key={t} className="tag">{t}</span>)}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {err && <div className="err" style={{ marginTop: 12 }}>{err}</div>}

          <div className="row row--between row--centered" style={{ marginTop: 20, position: 'sticky', bottom: 16, background: 'var(--paper)', padding: '12px 0', borderTop: '2px solid var(--ink)' }}>
            <span className="label muted">
              {dirty ? 'SELECTION CHANGED · RE-RENDER PDF' : 'NO CHANGES'}
            </span>
            <button className="btn btn--acid" disabled={!dirty || rerendering} onClick={rerender}>
              {rerendering ? <span className="spinner">RENDERING</span> : <>RE-RENDER PDF &nbsp;→</>}
            </button>
          {/* Live log panel — shows render + compile events in real time */}
          <EventLog state={logState} />
          </div>
        </div>

        {/* RIGHT: PDF preview + cover + ATS */}
        <div className="stack">
          <Section num="04.B" title="PDF" />
          <div style={{ border: '2px solid var(--ink)', height: 720, background: '#fff' }}>
            {app.pdfAvailable ? (
              <iframe src={pdfUrl} title="resume PDF" style={{ width: '100%', height: '100%', border: 'none' }} />
            ) : (
              <div className="center-page" style={{ height: '100%' }}>
                <div>
                  <div className="err">tectonic failed to produce a PDF.</div>
                  <pre style={{ fontSize: 11, color: 'var(--muted)', whiteSpace: 'pre-wrap', maxHeight: 300, overflow: 'auto', marginTop: 12 }}>
                    {app.tectonicLog?.slice(0, 1500)}
                  </pre>
                </div>
              </div>
            )}
          </div>
          <div>
            <a href={pdfUrl} className="btn btn--sm" target="_blank" rel="noreferrer">↓ DOWNLOAD PDF</a>
          </div>

          <Section num="04.C" title="Cover Letter" />
          <div className="panel panel--inset editorial" style={{ whiteSpace: 'pre-wrap', fontSize: 14, lineHeight: 1.55 }}>
            {app.coverLetter || <span className="muted">No cover letter.</span>}
          </div>

          <Section num="04.D" title="ATS" />
          <div>
            <div className="label muted" style={{ marginBottom: 6 }}>MATCHED</div>
            <div style={{ marginBottom: 12 }}>
              {app.atsMatched.map(k => <span key={k} className="tag tag--acid">{k}</span>)}
              {app.atsMatched.length === 0 && <span className="muted">—</span>}
            </div>
            <div className="label muted" style={{ marginBottom: 6 }}>MISSING</div>
            <div>
              {app.atsMissing.map(k => <span key={k} className="tag tag--rust">{k}</span>)}
              {app.atsMissing.length === 0 && <span className="muted">—</span>}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function parseRanking(raw: string | null | undefined): RankedBullet[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed as RankedBullet[];
    return [];
  } catch { return []; }
}

function setsEqual(a: Set<string>, b: Set<string>) {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}

function outcomeStyle(o: string): React.CSSProperties {
  switch (o) {
    case 'interview': return { background: 'var(--acid)', color: 'var(--ink)' };
    case 'offer':     return { background: 'var(--ink)',  color: 'var(--paper)' };
    case 'rejected':  return { background: 'var(--rust)', color: 'var(--paper)', borderColor: 'var(--rust)' };
    default:          return { background: 'var(--ink)',  color: 'var(--paper)' };
  }
}
