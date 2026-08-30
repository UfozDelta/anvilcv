import { useParams, Link } from 'react-router-dom';
import { api } from '../lib/api';
import { Section } from '../components/Section';
import { EventStream } from '../components/EventStream';
import { setsEqual } from '../lib/ranking';
import { useApplicationDetail } from '../hooks/useApplicationDetail';
import { RankedBulletRow } from '../components/ApplicationDetail/RankedBulletRow';
import { BulletGroupSection } from '../components/ApplicationDetail/BulletGroupSection';

export function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const s = useApplicationDetail(id);

  if (!s.app) return <div className="shell"><span className="spinner">LOADING</span></div>;

  const app = s.app;
  const pdfUrl = api.pdfUrl(`/api/applications/${app.id}/pdf`);
  const texUrl = api.pdfUrl(`/api/applications/${app.id}/tex`);
  const ogSelection = new Set(app.selectedBulletIds);
  const dirty = !setsEqual(s.selectedIds, ogSelection);

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
            disabled={s.busy}
            onClick={() => s.setOutcome(o)}
          >MARK {o.toUpperCase()}</button>
        ))}
      </div>

      <div className="split">

        {/* LEFT: ranked bullets */}
        <div>
          <Section num="03.A" title="Ranked Bullets" count={s.ranking.length} />
          <div className="label muted" style={{ marginBottom: 10 }}>
            CLICK RANK TO TOGGLE · {s.selectedIds.size} INCLUDED
          </div>

          {!s.bulletsReady ? (
            // Fallback while bullets/projects load — flat list, no project grouping yet.
            <div>
              {s.ranking.slice(0, s.showTail ? s.ranking.length : s.TOP_N).map(r => (
                <RankedBulletRow
                  key={r.bulletId}
                  r={r}
                  bullet={s.bullets[r.bulletId]}
                  isSelected={s.selectedIds.has(r.bulletId)}
                  whyOpen={s.expandedWhys.has(r.bulletId)}
                  onToggleSelect={() => s.toggleBullet(r.bulletId)}
                  onToggleWhy={() => s.toggleWhy(r.bulletId)}
                />
              ))}
            </div>
          ) : (
            <div>
              {s.grouped.experience.length > 0 && (
                <div style={{ marginBottom: 18 }}>
                  <div className="label muted" style={{ marginBottom: 8, letterSpacing: 1 }}>EXPERIENCE</div>
                  {s.grouped.experience.map(g => (
                    <BulletGroupSection
                      key={g.key}
                      g={g}
                      open={s.expandedGroups.has(g.key)}
                      selectedIds={s.selectedIds}
                      expandedWhys={s.expandedWhys}
                      bullets={s.bullets}
                      previewing={s.previewKey === g.key}
                      previewBusy={s.previewBusy}
                      onToggleOpen={() => s.toggleGroup(g.key)}
                      onToggleSelect={s.toggleBullet}
                      onToggleWhy={s.toggleWhy}
                      onPreview={ids => s.previewGroup(g.key, ids)}
                    />
                  ))}
                </div>
              )}
              {s.grouped.projects.length > 0 && (
                <div>
                  <div className="label muted" style={{ marginBottom: 8, letterSpacing: 1 }}>PROJECTS</div>
                  {s.grouped.projects.map(g => (
                    <BulletGroupSection
                      key={g.key}
                      g={g}
                      open={s.expandedGroups.has(g.key)}
                      selectedIds={s.selectedIds}
                      expandedWhys={s.expandedWhys}
                      bullets={s.bullets}
                      previewing={s.previewKey === g.key}
                      previewBusy={s.previewBusy}
                      onToggleOpen={() => s.toggleGroup(g.key)}
                      onToggleSelect={s.toggleBullet}
                      onToggleWhy={s.toggleWhy}
                      onPreview={ids => s.previewGroup(g.key, ids)}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="row row--between row--centered" style={{ marginTop: 20, position: 'sticky', bottom: 16, background: 'var(--paper)', padding: '12px 0', borderTop: '2px solid var(--ink)' }}>
            <span className="label muted">
              <span style={s.selectedLines > s.MAX_TOTAL_LINES ? { color: 'var(--rust)', fontWeight: 700 } : undefined}>
                ~{s.selectedLines}/{s.MAX_TOTAL_LINES} LINES
              </span>
              {' · '}
              {dirty ? 'SELECTION CHANGED · RE-RENDER PDF' : 'NO CHANGES'}
            </span>
            <button className="btn btn--acid" disabled={!dirty} onClick={() => s.setRerenderStreaming(true)}>
              RE-RENDER PDF &nbsp;→
            </button>
          </div>
        </div>

        {/* RIGHT: PDF preview + cover + ATS */}
        <div className="stack">
          <Section num="03.B" title="PDF" />
          {s.previewErr && (
            <div className="err" style={{ marginBottom: 8 }}>{s.previewErr}</div>
          )}
          {s.previewUrl && (
            <div className="row row--between row--centered" style={{ background: 'var(--acid)', color: 'var(--ink)', padding: '6px 10px', border: '2px solid var(--ink)', borderBottom: 'none' }}>
              <span className="label" style={{ fontWeight: 700 }}>
                PREVIEW · {previewName(s)} · NOT SAVED
              </span>
              <button className="btn btn--ghost btn--sm" style={{ fontSize: 10, padding: '2px 6px' }} onClick={s.closePreview}>✕ BACK TO SAVED</button>
            </div>
          )}
          <div style={{ border: '2px solid var(--ink)', height: 'min(720px, 80vh)', background: '#fff' }}>
            {s.previewUrl ? (
              <iframe src={s.previewUrl} title="bullet preview" style={{ width: '100%', height: '100%', border: 'none' }} />
            ) : app.pdfAvailable ? (
              <iframe src={s.pdfBlobUrl ?? undefined} title="resume PDF" style={{ width: '100%', height: '100%', border: 'none' }} />
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
          <div style={{ display: 'flex', gap: 8 }}>
            <a href={pdfUrl} className="btn btn--sm" target="_blank" rel="noreferrer">↓ DOWNLOAD PDF</a>
            <a href={texUrl} className="btn btn--sm">↓ DOWNLOAD .TEX</a>
          </div>

          <Section num="03.C" title="Cover Letter" />
          {app.coverLetterFlags.length > 0 && (
            <div className="err" style={{ marginBottom: 8 }}>
              UNVERIFIED FIGURES: {app.coverLetterFlags.join(', ')} — not in your selected
              bullets or the job description. Check before sending.
            </div>
          )}
          <div className="panel panel--inset editorial" style={{ whiteSpace: 'pre-wrap', fontSize: 14, lineHeight: 1.55 }}>
            {app.coverLetter || <span className="muted">No cover letter.</span>}
          </div>

          <Section num="03.D" title="ATS" />
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

      {s.rerenderStreaming && (
        <EventStream
          submitUrl={`/api/applications/${app.id}/rerender/submit`}
          submitBody={{ selectedBulletIds: Array.from(s.selectedIds) }}
          pollUrl={jobId => `/api/applications/jobs/${jobId}/progress`}
          onDone={async () => { await s.load(); s.setPdfVersion(v => v + 1); s.setRerenderStreaming(false); }}
          onClose={() => s.setRerenderStreaming(false)}
          title="RE-RENDERING PDF..."
          doneLabel="DONE →"
        />
      )}
    </div>
  );
}

/** Project name behind the open preview, for the banner. */
function previewName(s: ReturnType<typeof useApplicationDetail>): string {
  const all = [...s.grouped.experience, ...s.grouped.projects];
  return all.find(g => g.key === s.previewKey)?.project?.name ?? 'Other';
}

function outcomeStyle(o: string): React.CSSProperties {
  switch (o) {
    case 'interview': return { background: 'var(--acid)', color: 'var(--ink)' };
    case 'offer':     return { background: 'var(--ink)',  color: 'var(--paper)' };
    case 'rejected':  return { background: 'var(--rust)', color: 'var(--paper)', borderColor: 'var(--rust)' };
    default:          return { background: 'var(--ink)',  color: 'var(--paper)' };
  }
}
