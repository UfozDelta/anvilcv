import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, type ApplicationResponse, type BulletVerdict } from '../lib/api';
import { EventStream } from '../components/EventStream';
import { RichText } from '../components/RichText';
import { Stat } from '../components/Stat';
import { setsEqual } from '../lib/ranking';
import { useApplicationDetail } from '../hooks/useApplicationDetail';
import { RankedBulletRow } from '../components/ApplicationDetail/RankedBulletRow';
import { BulletGroupSection } from '../components/ApplicationDetail/BulletGroupSection';

const OUTCOMES = ['applied', 'interview', 'offer', 'rejected'] as const;

type Tab = 'page' | 'cover' | 'review' | 'ats';
type Detail = ReturnType<typeof useApplicationDetail>;

export function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const s = useApplicationDetail(id);
  const [tab, setTab] = useState<Tab>('page');

  if (!s.app) return <div className="shell"><span className="spinner">LOADING</span></div>;

  const app = s.app;
  const ogSelection = new Set(app.selectedBulletIds);
  const dirty = !setsEqual(s.selectedIds, ogSelection);
  const verdicts = Object.fromEntries(
    app.recruiterBulletVerdicts.map(v => [v.bulletId, v]),
  ) as Record<string, BulletVerdict>;

  // Coverage comes from the deterministic ATS pass, never from the LLM.
  const atsTotal = app.atsMatched.length + app.atsMissing.length;
  const coverage = atsTotal === 0 ? 0 : Math.round((app.atsMatched.length / atsTotal) * 100);

  const over = s.selectedLines > s.MAX_TOTAL_LINES;
  const pct = Math.min(100, Math.round((s.selectedLines / s.MAX_TOTAL_LINES) * 100));

  // Anything the recruiter pass flagged that is still on the page — the actionable count.
  const flaggedIn = app.recruiterBulletVerdicts.filter(
    v => v.verdict !== 'keep' && s.selectedIds.has(v.bulletId),
  );

  return (
    <div className="shell">
      <div className="row row--between row--centered" style={{ marginBottom: 8 }}>
        <Link to="/applications" className="eyebrow" style={{ textDecoration: 'none' }}>← All applications</Link>
      </div>

      <h1 className="display" style={{ fontSize: 56, margin: '4px 0 6px', lineHeight: 0.98 }}>
        {app.company || 'Untitled'}
      </h1>
      <div className="editorial" style={{ fontSize: 17, color: 'var(--muted)', marginBottom: 18 }}>
        {app.role || 'role'} · tuned for <em>{app.roleEmphasis}</em> ·{' '}
        {new Date(app.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
      </div>

      {/* One segmented control instead of four MARK buttons that look like four actions. */}
      <div style={{ marginBottom: 16 }}>
        <div className="eyebrow" style={{ marginBottom: 6 }}>Status</div>
        <div className="filterset">
          {OUTCOMES.map(o => (
            <button
              key={o}
              className={app.outcome === o ? 'is-on' : ''}
              disabled={s.busy}
              onClick={() => s.setOutcome(o)}
            >{o}</button>
          ))}
        </div>
      </div>

      {/* ---------- health strip: every number this page knows, once, labelled ---------- */}
      <div className="statrow">
        <div className={over ? 'stat stat--alert' : 'stat'}>
          <div className="stat__label">Page budget</div>
          <div className="stat__value">{s.selectedLines}<small>/{s.MAX_TOTAL_LINES} lines</small></div>
          <div className="meter" style={{ marginTop: 8 }}>
            <div
              className={`meter__fill ${over ? 'meter__fill--over' : 'meter__fill--ok'}`}
              style={{ width: `${pct}%` }}
            />
          </div>
          <div className="stat__caption">
            {over ? `${s.selectedLines - s.MAX_TOTAL_LINES} lines over — cut something.` : 'Fits on one page.'}
          </div>
        </div>

        <Stat
          label="Compiled length"
          value={app.pageCount ?? '—'}
          unit={app.pageCount === 1 ? ' page' : ' pages'}
          tone={app.pageCount === null ? undefined : app.pageCount === 1 ? 'good' : 'alert'}
          caption={
            app.pageCount === null
              ? 'Not rendered yet.'
              : app.pageCount === 1 ? 'Last render fit.' : 'Last render spilled over.'
          }
        />

        <Stat
          label="Fit score"
          value={app.fitScore ?? '—'}
          unit="/100"
          caption={`You vs the job. ${app.fitVerdict ?? ''}`}
        />

        <Stat
          label="Page score"
          value={app.recruiterScore ?? '—'}
          unit="/100"
          tone={app.recruiterStale ? 'alert' : undefined}
          caption={
            app.recruiterStale
              ? 'Stale — scored before your last edit.'
              : `How the resume sells you. ${app.recruiterVerdict ?? ''}`
          }
        />

        <Stat
          label="JD coverage"
          value={coverage}
          unit="%"
          caption={`${app.atsMatched.length} of ${atsTotal} keywords present.`}
        />
      </div>

      <div className="labsplit" style={{ marginTop: 26 }}>

        {/* ================= LEFT: the editor ================= */}
        <div>
          <div className="eyebrow" style={{ marginBottom: 4 }}>Bullets on the page</div>
          <div style={{ fontSize: 12.5, color: 'var(--muted)', marginBottom: 10 }}>
            {s.selectedIds.size} of {s.ranking.length} included · {s.lockedIds.size} locked ·{' '}
            {flaggedIn.length > 0
              ? `${flaggedIn.length} flagged by the recruiter pass`
              : 'nothing flagged'}
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
                  isNew={s.justAddedIds.has(r.bulletId)}
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
            [
              { heading: 'Experience', groups: s.grouped.experience },
              { heading: 'Projects', groups: s.grouped.projects },
            ].map(sec => sec.groups.length === 0 ? null : (
              <div key={sec.heading}>
                <div className="eyebrow" style={{ marginTop: 18, marginBottom: 2 }}>{sec.heading}</div>
                {sec.groups.map(g => (
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
                    refitBusy={s.refitTarget !== null}
                    editingId={s.editingId}
                    cfg={s.cfg}
                    editingProjectId={s.editingProjectId}
                    lockedIds={s.lockedIds}
                    newIds={s.justAddedIds}
                    onToggleOpen={() => s.toggleGroup(g.key)}
                    onRefit={pid => s.startRefit(pid)}
                    onToggleSelect={s.toggleBullet}
                    onToggleWhy={s.toggleWhy}
                    onPreview={ids => { s.previewGroup(g.key, ids); setTab('page'); }}
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
            ))
          )}

          {/* One bar: what state you are in, and the one action that resolves it. */}
          <div className="actionbar">
            <div className="actionbar__budget">
              <div className="actionbar__line">
                <span>{s.selectedLines} of {s.MAX_TOTAL_LINES} lines used</span>
                <span className={`savestate ${dirty ? 'savestate--dirty' : 'savestate--clean'}`}>
                  {dirty ? 'Unsaved changes' : 'Saved'}
                </span>
              </div>
              <div className="meter meter--thick">
                <div
                  className={`meter__fill ${over ? 'meter__fill--over' : 'meter__fill--ok'}`}
                  style={{ width: `${pct}%` }}
                />
              </div>
            </div>
            <div className="actionbar__acts">
              <button
                className="minibtn"
                style={{ padding: '9px 12px' }}
                disabled={s.refitTarget !== null}
                title="Re-pick every entry from your whole bullet bank, keeping locked bullets pinned"
                onClick={() => s.startRefit()}
              >
                Auto-pick whole page
              </button>
              <button
                className="btn btn--sm"
                style={{ background: dirty ? 'var(--acid)' : 'var(--paper)', borderWidth: 2 }}
                onClick={() => s.setRerenderStreaming(true)}
              >
                Rebuild PDF →
              </button>
            </div>
          </div>
        </div>

        {/* ================= RIGHT: one viewer, four tabs ================= */}
        <div>
          <div className="tabs">
            <button className={tab === 'page' ? 'is-on' : ''} onClick={() => setTab('page')}>Page</button>
            <button className={tab === 'cover' ? 'is-on' : ''} onClick={() => setTab('cover')}>
              Cover
              {app.coverLetterFlags.length > 0 && <span className="tabs__badge">{app.coverLetterFlags.length}</span>}
            </button>
            <button className={tab === 'review' ? 'is-on' : ''} onClick={() => setTab('review')}>
              Review
              {flaggedIn.length > 0 && <span className="tabs__badge">{flaggedIn.length}</span>}
            </button>
            <button className={tab === 'ats' ? 'is-on' : ''} onClick={() => setTab('ats')}>
              Keywords
              {app.atsMissing.length > 0 && <span className="tabs__badge">{app.atsMissing.length}</span>}
            </button>
          </div>

          <div className="tabpane">
            {tab === 'page' && <PagePane s={s} app={app} />}
            {tab === 'cover' && <CoverPane app={app} />}
            {tab === 'review' && <ReviewPane s={s} app={app} verdicts={verdicts} />}
            {tab === 'ats' && <AtsPane app={app} coverage={coverage} />}
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

      {s.refitTarget && (
        <EventStream
          submitUrl={`/api/applications/${app.id}/refit-selection/submit`}
          submitBody={{ projectId: s.refitTarget.projectId }}
          pollUrl={jobId => `/api/applications/jobs/${jobId}/progress`}
          onDone={() => s.finishRefit()}
          onClose={() => s.setRefitTarget(null)}
          title={
            s.refitTarget.projectId
              ? `REFITTING ${(s.projectById[s.refitTarget.projectId]?.name ?? 'ENTRY').toUpperCase()}...`
              : 'REFITTING WHOLE PAGE...'
          }
          doneLabel="DONE →"
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------- panes

function PagePane({ s, app }: { s: Detail; app: ApplicationResponse }) {
  const pdfUrl = api.pdfUrl(`/api/applications/${app.id}/pdf`);
  const texUrl = api.pdfUrl(`/api/applications/${app.id}/tex`);
  return (
    <div>
      {s.previewErr && <div className="err" style={{ marginBottom: 8 }}>{s.previewErr}</div>}

      {s.previewUrl && (
        <div
          className="row row--between row--centered"
          style={{ background: 'var(--acid)', padding: '6px 10px', border: '2px solid var(--ink)', borderBottom: 'none' }}
        >
          <span className="eyebrow" style={{ color: 'var(--ink)' }}>
            Preview · {previewName(s)} · not saved
          </span>
          <button className="minibtn" onClick={s.closePreview}>✕ Back to saved</button>
        </div>
      )}

      <div style={{ border: '2px solid var(--ink)', height: 'min(640px, 74vh)', background: '#fff' }}>
        {s.previewUrl ? (
          <iframe src={s.previewUrl} title="bullet preview" style={{ width: '100%', height: '100%', border: 'none' }} />
        ) : app.pdfAvailable ? (
          <iframe src={s.pdfBlobUrl ?? undefined} title="resume PDF" style={{ width: '100%', height: '100%', border: 'none' }} />
        ) : (
          <div className="center-page" style={{ height: '100%', minHeight: 0 }}>
            <div>
              <div className="err">tectonic failed to produce a PDF.</div>
              <pre style={{ fontSize: 11, color: 'var(--muted)', whiteSpace: 'pre-wrap', maxHeight: 300, overflow: 'auto', marginTop: 12 }}>
                {app.tectonicLog?.slice(0, 1500)}
              </pre>
            </div>
          </div>
        )}
      </div>

      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <a href={pdfUrl} className="minibtn" style={{ padding: '8px 10px', textDecoration: 'none' }} target="_blank" rel="noreferrer">
          ↓ Download PDF
        </a>
        <a href={texUrl} className="minibtn" style={{ padding: '8px 10px', textDecoration: 'none' }}>
          ↓ Download .tex
        </a>
      </div>
    </div>
  );
}

function CoverPane({ app }: { app: ApplicationResponse }) {
  const letter = app.coverLetter;
  return (
    <div>
      {app.coverLetterFlags.length > 0 && (
        <div className="callout">
          <div className="callout__head">Unverified figures</div>
          <b>{app.coverLetterFlags.join(', ')}</b> — not in your selected bullets nor the job
          description. Check before sending.
        </div>
      )}
      <div style={{
        whiteSpace: 'pre-wrap',
        fontSize: 13.5,
        lineHeight: 1.6,
        maxHeight: 520,
        overflow: 'auto',
        background: 'var(--paper-2)',
        padding: 14,
        border: '1px solid var(--soft)',
      }}>
        {letter || <span className="muted">No cover letter.</span>}
      </div>
      {letter && (
        <button
          className="minibtn"
          style={{ marginTop: 10, padding: '8px 10px' }}
          onClick={() => navigator.clipboard?.writeText(letter)}
        >
          Copy letter
        </button>
      )}
    </div>
  );
}

/** Fit and the recruiter pass merged into one tab, ordered by what to do about it. */
function ReviewPane({ s, app, verdicts }: {
  s: Detail;
  app: ApplicationResponse;
  verdicts: Record<string, BulletVerdict>;
}) {
  const flagged = app.recruiterBulletVerdicts.filter(v => v.verdict !== 'keep');
  const live = flagged.filter(v => s.selectedIds.has(v.bulletId));
  const resolved = flagged.filter(v => !s.selectedIds.has(v.bulletId));
  const weakestId = app.recruiterWeakestBulletId;

  if (app.fitScore === null && app.recruiterScore === null) {
    return <p className="muted" style={{ margin: 0, fontSize: 13 }}>This application has not been scored.</p>;
  }

  return (
    <div>
      {app.recruiterStale && (
        <div className="callout">
          <div className="callout__head">Out of date</div>
          This review scored an earlier selection. Rebuild the PDF to re-score.
        </div>
      )}

      {app.recruiterThinnestRequirement && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Biggest hole</div>
          <p style={{ margin: '0 0 16px', fontSize: 13.5, lineHeight: 1.55 }}>
            {app.recruiterThinnestRequirement}
          </p>
        </>
      )}

      {weakestId && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Weakest bullet on the page</div>
          <div style={{ marginBottom: 16, paddingLeft: 10, borderLeft: '3px solid var(--rust)' }}>
            <div style={{ fontSize: 13, lineHeight: 1.45, marginBottom: 3 }}>
              <RichText text={s.bullets[weakestId]?.text ?? weakestId} />
            </div>
            {verdicts[weakestId] && (
              <div style={{ fontSize: 12, color: 'var(--muted)', lineHeight: 1.45 }}>{verdicts[weakestId].reason}</div>
            )}
          </div>
        </>
      )}

      <div className="eyebrow" style={{ marginBottom: 6 }}>
        Still on the page and flagged ({live.length})
      </div>
      {live.length === 0 ? (
        <p style={{ margin: '0 0 16px', fontSize: 13, color: 'var(--muted)' }}>
          Nothing flagged is currently included.
        </p>
      ) : (
        <div style={{ marginBottom: 16 }}>
          {live.map(v => (
            <div key={v.bulletId} style={{ marginBottom: 10, paddingLeft: 10, borderLeft: '3px solid var(--rust)' }}>
              <span className={`vchip vchip--${v.verdict}`}>{v.verdict}</span>
              <div style={{ fontSize: 13, lineHeight: 1.45, margin: '5px 0 3px' }}>
                <RichText text={s.bullets[v.bulletId]?.text ?? v.bulletId} />
              </div>
              <div style={{ fontSize: 12, color: 'var(--muted)', lineHeight: 1.45 }}>{v.reason}</div>
            </div>
          ))}
        </div>
      )}

      {resolved.length > 0 && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Flagged and already removed ({resolved.length})</div>
          <div style={{ marginBottom: 16, fontSize: 12.5, color: 'var(--muted)', lineHeight: 1.5 }}>
            {resolved.map(v => (
              <div key={v.bulletId} style={{ marginBottom: 4 }}>
                ✓ {(s.bullets[v.bulletId]?.text ?? v.bulletId).slice(0, 70)}…
              </div>
            ))}
          </div>
        </>
      )}

      {app.recruiterWeaknesses.length > 0 && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Objections a recruiter would raise</div>
          <ul style={{ margin: '0 0 16px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
            {app.recruiterWeaknesses.map((w, i) => <li key={i} style={{ marginBottom: 4 }}>{w}</li>)}
          </ul>
        </>
      )}

      {app.fitStrengths.length > 0 && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Where you are strong</div>
          <ul style={{ margin: '0 0 16px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
            {app.fitStrengths.map(t => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}
          </ul>
        </>
      )}

      {app.fitGaps.length > 0 && (
        <>
          <div className="eyebrow" style={{ marginBottom: 6 }}>Where you are short</div>
          <ul style={{ margin: '0 0 16px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
            {app.fitGaps.map(t => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}
          </ul>
        </>
      )}

      <div className="eyebrow" style={{ marginBottom: 6 }}>Sub-scores</div>
      <div style={{ fontSize: 12.5, color: 'var(--muted)', lineHeight: 1.6 }}>
        technical {app.fitDimensions.technical ?? '—'} · experience {app.fitDimensions.experience ?? '—'}
        {' · '}evidence {app.recruiterDimensions.evidenceStrength ?? '—'}
        {' · '}relevance {app.recruiterDimensions.relevanceDensity ?? '—'}
      </div>
    </div>
  );
}

function AtsPane({ app, coverage }: { app: ApplicationResponse; coverage: number }) {
  return (
    <div>
      <div className="eyebrow" style={{ marginBottom: 8 }}>
        Keywords pulled from the job description, checked against your rendered page
      </div>
      <div className="meter meter--thick" style={{ marginBottom: 14 }}>
        <div className="meter__fill meter__fill--ok" style={{ width: `${coverage}%` }} />
      </div>

      <div className="eyebrow" style={{ marginBottom: 6 }}>On the page ({app.atsMatched.length})</div>
      <div className="kwgrid" style={{ marginBottom: 16 }}>
        {app.atsMatched.map(k => <span key={k} className="kw kw--hit">{k}</span>)}
        {app.atsMatched.length === 0 && <span className="muted">—</span>}
      </div>

      <div className="eyebrow" style={{ marginBottom: 6 }}>Missing ({app.atsMissing.length})</div>
      <div className="kwgrid">
        {app.atsMissing.map(k => <span key={k} className="kw kw--miss">{k}</span>)}
        {app.atsMissing.length === 0 && <span className="muted">—</span>}
      </div>
      <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 12, lineHeight: 1.5 }}>
        Missing is not automatically bad — only add a keyword if you can back it with real work.
      </p>
    </div>
  );
}

/** Project name behind the open preview, for the banner. */
function previewName(s: Detail): string {
  const all = [...s.grouped.experience, ...s.grouped.projects];
  return all.find(g => g.key === s.previewKey)?.project?.name ?? 'Other';
}
