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
  const verdicts = Object.fromEntries(app.recruiterBulletVerdicts.map(v => [v.bulletId, v]));
  // Coverage comes from the deterministic ATS pass, never from the LLM.
  const atsTotal = app.atsMatched.length + app.atsMissing.length;
  const coverage = atsTotal === 0 ? 0 : Math.round((app.atsMatched.length / atsTotal) * 100);
  // The recruiter names its own weakest bullet; fall back to the verdict list for
  // applications scored before that field was stored.
  const weakestId = app.recruiterWeakestBulletId;
  const weakLinks = [
    ...app.recruiterBulletVerdicts.filter(v => v.verdict === 'drop'),
    ...app.recruiterBulletVerdicts.filter(v => v.verdict === 'weak'),
  ];

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
                  verdict={verdicts[r.bulletId]}
                  editing={s.editingId === r.bulletId}
                  cfg={s.cfg}
                  locked={s.lockedIds.has(r.bulletId)}
                  onToggleSelect={() => s.toggleBullet(r.bulletId)}
                  onToggleWhy={() => s.toggleWhy(r.bulletId)}
                  onEdit={() => s.setEditingId(r.bulletId)}
                  onCancelEdit={() => s.setEditingId(null)}
                  onSaveBullet={(text, tags) => {
                    const b = s.bullets[r.bulletId];
                    if (b) s.saveBullet(b, text, tags);
                  }}
                  onToggleLock={() => s.toggleLock(r.bulletId)}
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
                      verdicts={verdicts}
                      previewing={s.previewKey === g.key}
                      previewBusy={s.previewBusy}
                      editingId={s.editingId}
                      cfg={s.cfg}
                      editingProjectId={s.editingProjectId}
                      lockedIds={s.lockedIds}
                      onToggleOpen={() => s.toggleGroup(g.key)}
                      onToggleSelect={s.toggleBullet}
                      onToggleWhy={s.toggleWhy}
                      onPreview={ids => s.previewGroup(g.key, ids)}
                      onEdit={bid => s.setEditingId(bid)}
                      onCancelEdit={() => s.setEditingId(null)}
                      onSaveBullet={(b, text, tags) => s.saveBullet(b, text, tags)}
                      onEditProject={pid => s.setEditingProjectId(pid)}
                      onCancelEditProject={() => s.setEditingProjectId(null)}
                      onSaveProject={(p, patch) => s.saveProject(p, patch)}
                      onToggleLock={bid => s.toggleLock(bid)}
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
                      verdicts={verdicts}
                      previewing={s.previewKey === g.key}
                      previewBusy={s.previewBusy}
                      editingId={s.editingId}
                      cfg={s.cfg}
                      editingProjectId={s.editingProjectId}
                      lockedIds={s.lockedIds}
                      onToggleOpen={() => s.toggleGroup(g.key)}
                      onToggleSelect={s.toggleBullet}
                      onToggleWhy={s.toggleWhy}
                      onPreview={ids => s.previewGroup(g.key, ids)}
                      onEdit={bid => s.setEditingId(bid)}
                      onCancelEdit={() => s.setEditingId(null)}
                      onSaveBullet={(b, text, tags) => s.saveBullet(b, text, tags)}
                      onEditProject={pid => s.setEditingProjectId(pid)}
                      onCancelEditProject={() => s.setEditingProjectId(null)}
                      onSaveProject={(p, patch) => s.saveProject(p, patch)}
                      onToggleLock={bid => s.toggleLock(bid)}
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
              {s.lockedIds.size > 0 && ` · ${s.lockedIds.size} LOCKED`}
            </span>
            <button
              className="btn btn--ghost btn--sm"
              disabled={s.refitting}
              title="Re-pick bullets from the bank, keeping locked ones pinned"
              onClick={() => s.refitSelection()}
              style={{ marginRight: 8 }}
            >
              {s.refitting ? 'REFITTING...' : 'REFIT SELECTION'}
            </button>
            <button className="btn btn--acid" onClick={() => s.setRerenderStreaming(true)}>
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

          {app.fitScore !== null && (
            <>
              <Section num="03.D" title="Fit" />
              <div style={{ marginBottom: 20 }}>
                <div style={{ marginBottom: 8 }}>
                  <span className="tag tag--acid">{app.fitScore}/100</span>
                  <span className="tag">{app.fitVerdict}</span>
                  <span className="muted">
                    &nbsp;technical {app.fitDimensions.technical ?? '—'} · experience {app.fitDimensions.experience ?? '—'}
                  </span>
                </div>
                {app.fitStrengths.length > 0 && (
                  <>
                    <div className="label muted" style={{ marginBottom: 6 }}>STRENGTHS</div>
                    <ul style={{ margin: '0 0 12px', paddingLeft: 18 }}>
                      {app.fitStrengths.map(t => <li key={t}>{t}</li>)}
                    </ul>
                  </>
                )}
                {app.fitGaps.length > 0 && (
                  <>
                    <div className="label muted" style={{ marginBottom: 6 }}>GAPS</div>
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      {app.fitGaps.map(t => <li key={t}>{t}</li>)}
                    </ul>
                  </>
                )}
              </div>
            </>
          )}

          {app.recruiterScore !== null && (
            <>
              <Section num="03.D2" title="Recruiter Pass" />
              <div style={{ marginBottom: 20 }}>
                <div className="label muted" style={{ marginBottom: 6 }}>
                  HOW THIS PAGE SELLS YOU — NOT WHETHER YOU FIT
                </div>
                {app.recruiterStale && (
                  <div className="err" style={{ marginBottom: 8 }}>
                    SCORED FOR THE PREVIOUS SELECTION — re-generate to re-score.
                  </div>
                )}

                {/* The critique leads: it is the actionable output, the score is context. */}
                {app.recruiterThinnestRequirement && (
                  <>
                    <div className="label muted" style={{ marginBottom: 6 }}>THINNEST SUPPORT</div>
                    <div style={{ marginBottom: 12 }}>{app.recruiterThinnestRequirement}</div>
                  </>
                )}

                {weakestId && (
                  <>
                    <div className="label muted" style={{ marginBottom: 6 }}>WEAKEST BULLET</div>
                    <div style={{ marginBottom: 12 }}>
                      <span className="tag tag--rust">WEAKEST</span>
                      &nbsp;{s.bullets[weakestId]?.text ?? weakestId}
                      {verdicts[weakestId] && (
                        <div className="muted" style={{ fontSize: 12 }}>{verdicts[weakestId].reason}</div>
                      )}
                    </div>
                  </>
                )}

                {app.recruiterWeaknesses.length > 0 && (
                  <>
                    <div className="label muted" style={{ marginBottom: 6 }}>OBJECTIONS</div>
                    <ul style={{ margin: '0 0 12px', paddingLeft: 18 }}>
                      {app.recruiterWeaknesses.map((w, i) => <li key={i}>{w}</li>)}
                    </ul>
                  </>
                )}

                <div className="label muted" style={{ marginBottom: 6 }}>WEAK OR DROPPABLE</div>
                {weakLinks.length > 0 ? (
                  <ul style={{ margin: '0 0 12px', paddingLeft: 18 }}>
                    {weakLinks.map(v => (
                      <li key={v.bulletId}>
                        <span className={`tag ${v.verdict === 'drop' ? 'tag--rust' : ''}`}>{v.verdict.toUpperCase()}</span>
                        &nbsp;{s.bullets[v.bulletId]?.text ?? v.bulletId}
                        <div className="muted" style={{ fontSize: 12 }}>{v.reason}</div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="muted" style={{ marginBottom: 12 }}>
                    Nothing on the page was marked weak or droppable.
                  </div>
                )}

                <div style={{ marginBottom: 8 }}>
                  <span className="tag tag--acid">PAGE {app.recruiterScore}/100</span>
                  <span className="tag">{app.recruiterVerdict}</span>
                </div>
                <div className="muted" style={{ marginBottom: 8, fontSize: 13 }}>
                  evidence {app.recruiterDimensions.evidenceStrength ?? '—'}
                  {' · '}relevance {app.recruiterDimensions.relevanceDensity ?? '—'}
                  {' · '}JD coverage {coverage}% ({app.atsMatched.length}/{atsTotal})
                </div>
                {app.pageCount !== null && (
                  <div style={app.pageCount === 1 ? undefined : { color: 'var(--rust)', fontWeight: 700 }}>
                    {app.pageCount} page{app.pageCount === 1 ? '' : 's'}
                    {app.pageCount === 1 ? '' : ' — trim the selection'}
                  </div>
                )}
              </div>
            </>
          )}

          <Section num="03.E" title="ATS" />
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
