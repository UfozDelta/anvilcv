import { useMemo, useState } from 'react';
import type { BulletVerdict } from '../lib/api';
import { estimatedLines } from '../lib/bulletLength';
import { groupRankedByProject } from '../lib/groupBullets';
import {
  LAB_APP, LAB_BULLET_MAP, LAB_MAX_LINES, LAB_PROJECT_MAP, LAB_RANKING,
} from './fixtures';
import { LabChrome, RichText, Stat } from './LabChrome';
import { BulletRow } from './LabBulletRow';

const OUTCOMES = ['applied', 'interview', 'offer', 'rejected'] as const;
type Tab = 'pdf' | 'cover' | 'review' | 'ats';

export function LabDetail() {
  const app = LAB_APP;
  const [selected, setSelected] = useState<Set<string>>(new Set(app.selectedBulletIds));
  const [locked, setLocked] = useState<Set<string>>(new Set(app.lockedBulletIds));
  const [openWhy, setOpenWhy] = useState<Set<string>>(new Set());
  const [closedGroups, setClosedGroups] = useState<Set<string>>(new Set());
  const [outcome, setOutcome] = useState(app.outcome);
  const [tab, setTab] = useState<Tab>('pdf');

  const verdicts = useMemo(
    () => Object.fromEntries(app.recruiterBulletVerdicts.map(v => [v.bulletId, v])) as Record<string, BulletVerdict>,
    [app.recruiterBulletVerdicts],
  );

  const grouped = useMemo(
    () => groupRankedByProject(LAB_RANKING, LAB_BULLET_MAP, LAB_PROJECT_MAP),
    [],
  );

  const lines = useMemo(
    () => [...selected].reduce((n, id) => n + estimatedLines(LAB_BULLET_MAP[id]?.text ?? ''), 0),
    [selected],
  );

  const original = useMemo(() => new Set(app.selectedBulletIds), [app.selectedBulletIds]);
  const dirty = selected.size !== original.size || [...selected].some(id => !original.has(id));
  const over = lines > LAB_MAX_LINES;
  const pct = Math.min(100, Math.round((lines / LAB_MAX_LINES) * 100));

  const atsTotal = app.atsMatched.length + app.atsMissing.length;
  const coverage = atsTotal === 0 ? 0 : Math.round((app.atsMatched.length / atsTotal) * 100);

  // Anything the recruiter pass flagged that is still on the page — the actionable count.
  const flaggedIn = app.recruiterBulletVerdicts.filter(
    v => v.verdict !== 'keep' && selected.has(v.bulletId),
  );

  function toggle(setter: React.Dispatch<React.SetStateAction<Set<string>>>, id: string) {
    setter(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  return (
    <LabChrome
      title="Applications — detail"
      note={
        <>
          The audit found five stacked panels in one scrolling column, four health numbers in
          three different places, and include/exclude hidden behind a click on a rank number
          with 45% opacity as the only feedback. Here: one health strip up top, the viewer
          behind tabs, and every bullet says <b style={{ color: 'var(--ink)' }}>IN</b> or{' '}
          <b style={{ color: 'var(--ink)' }}>OUT</b> in words. Toggling bullets updates the
          budget, the page preview and the review tab live.
        </>
      }
    >
      {/* ---------- header ---------- */}
      <div style={{ marginBottom: 18 }}>
        <div className="eyebrow" style={{ marginBottom: 8 }}>← All applications</div>
        <h2 className="display" style={{ fontSize: 40, lineHeight: 1, margin: '0 0 6px' }}>
          {app.company}
        </h2>
        <div className="editorial" style={{ fontSize: 16, color: 'var(--muted)' }}>
          {app.role} · tuned for <em>{app.roleEmphasis}</em> ·{' '}
          {new Date(app.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
        </div>
      </div>

      {/* One segmented control instead of four MARK buttons that look like four actions. */}
      <div style={{ marginBottom: 16 }}>
        <div className="eyebrow" style={{ marginBottom: 6 }}>Status</div>
        <div className="filterset">
          {OUTCOMES.map(o => (
            <button key={o} className={outcome === o ? 'is-on' : ''} onClick={() => setOutcome(o)}>
              {o}
            </button>
          ))}
        </div>
      </div>

      {/* ---------- health strip: every number this page knows, once, labelled ---------- */}
      <div className="statrow">
        <div className={over ? 'stat stat--alert' : 'stat'}>
          <div className="stat__label">Page budget</div>
          <div className="stat__value">{lines}<small>/{LAB_MAX_LINES} lines</small></div>
          <div className="meter" style={{ marginTop: 8 }}>
            <div
              className={`meter__fill ${over ? 'meter__fill--over' : 'meter__fill--ok'}`}
              style={{ width: `${pct}%` }}
            />
          </div>
          <div className="stat__caption">
            {over ? `${lines - LAB_MAX_LINES} lines over — cut something.` : 'Fits on one page.'}
          </div>
        </div>

        <Stat
          label="Compiled length"
          value={app.pageCount ?? '—'}
          unit={app.pageCount === 1 ? ' page' : ' pages'}
          tone={app.pageCount === 1 ? 'good' : 'alert'}
          caption={app.pageCount === 1 ? 'Last render fit.' : 'Last render spilled over.'}
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
            {selected.size} of {LAB_RANKING.length} included · {locked.size} locked ·{' '}
            {flaggedIn.length > 0
              ? `${flaggedIn.length} flagged by the recruiter pass`
              : 'nothing flagged'}
          </div>

          {[
            { heading: 'Experience', groups: grouped.experience },
            { heading: 'Projects', groups: grouped.projects },
          ].map(sec => sec.groups.length === 0 ? null : (
            <div key={sec.heading} style={{ marginBottom: 8 }}>
              <div className="eyebrow" style={{ marginTop: 18, marginBottom: 2 }}>{sec.heading}</div>
              {sec.groups.map(g => {
                const open = !closedGroups.has(g.key);
                const inGroup = g.items.filter(r => selected.has(r.bulletId));
                const gLines = inGroup.reduce(
                  (n, r) => n + estimatedLines(LAB_BULLET_MAP[r.bulletId]?.text ?? ''), 0);
                return (
                  <div key={g.key}>
                    <div
                      className="grouphead"
                      role="button"
                      tabIndex={0}
                      aria-expanded={open}
                      onClick={() => toggle(setClosedGroups, g.key)}
                      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') toggle(setClosedGroups, g.key); }}
                    >
                      <div>
                        <div className="grouphead__name">
                          {open ? '▾' : '▸'} {g.project?.name ?? 'Other'}
                        </div>
                        {/* Spelled out. "~4L · 3/7" needed a decoder ring. */}
                        <div className="grouphead__sub">
                          {inGroup.length} of {g.items.length} included · about {gLines} line{gLines === 1 ? '' : 's'}
                        </div>
                      </div>
                      <div className="grouphead__right" onClick={e => e.stopPropagation()}>
                        <button className="minibtn" disabled={inGroup.length === 0}>
                          Preview just this
                        </button>
                        <button className="minibtn">Edit heading</button>
                      </div>
                    </div>

                    {open && g.items.map(r => (
                      <BulletRow
                        key={r.bulletId}
                        r={r}
                        bullet={LAB_BULLET_MAP[r.bulletId]}
                        isIn={selected.has(r.bulletId)}
                        isLocked={locked.has(r.bulletId)}
                        verdict={verdicts[r.bulletId]}
                        whyOpen={openWhy.has(r.bulletId)}
                        onToggleIn={() => toggle(setSelected, r.bulletId)}
                        onToggleLock={() => toggle(setLocked, r.bulletId)}
                        onToggleWhy={() => toggle(setOpenWhy, r.bulletId)}
                      />
                    ))}
                  </div>
                );
              })}
            </div>
          ))}

          {/* One bar: what state you are in, and the one action that resolves it. */}
          <div className="actionbar">
            <div className="actionbar__budget">
              <div className="actionbar__line">
                <span>{lines} of {LAB_MAX_LINES} lines used</span>
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
              <button className="minibtn" style={{ padding: '9px 12px' }} title="Re-pick from your whole bullet bank, keeping locked bullets pinned">
                Auto-pick again
              </button>
              <button
                className="btn btn--sm"
                style={{ background: dirty ? 'var(--acid)' : 'var(--paper)', borderWidth: 2 }}
              >
                Rebuild PDF →
              </button>
            </div>
          </div>
        </div>

        {/* ================= RIGHT: one viewer, four tabs ================= */}
        <div>
          <div className="tabs">
            <button className={tab === 'pdf' ? 'is-on' : ''} onClick={() => setTab('pdf')}>Page</button>
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
            {tab === 'pdf' && <PagePane selected={selected} over={over} lines={lines} />}
            {tab === 'cover' && <CoverPane />}
            {tab === 'review' && <ReviewPane selected={selected} onJump={() => setTab('pdf')} />}
            {tab === 'ats' && <AtsPane />}
          </div>
        </div>
      </div>
    </LabChrome>
  );
}

// ---------------------------------------------------------------- panes

/** Stand-in for the tectonic PDF: the same selection, laid out as a sheet. */
function PagePane({ selected, over, lines }: { selected: Set<string>; over: boolean; lines: number }) {
  const grouped = groupRankedByProject(LAB_RANKING, LAB_BULLET_MAP, LAB_PROJECT_MAP);
  const all = [...grouped.experience, ...grouped.projects];
  return (
    <div>
      <div className="eyebrow" style={{ marginBottom: 8 }}>
        Live approximation — the real page renders through tectonic
      </div>
      <div style={{
        background: '#fff',
        border: '2px solid var(--ink)',
        padding: '26px 28px',
        maxHeight: 560,
        overflow: 'auto',
        position: 'relative',
      }}>
        <div style={{ fontFamily: 'var(--serif)', fontSize: 22, fontWeight: 700, marginBottom: 2 }}>
          Placeholder Candidate
        </div>
        <div style={{ fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--muted)', marginBottom: 16 }}>
          placeholder@example.com · github.com/placeholder · Boston, MA
        </div>
        {all.map(g => {
          const rows = g.items.filter(r => selected.has(r.bulletId));
          if (rows.length === 0) return null;
          return (
            <div key={g.key} style={{ marginBottom: 14 }}>
              <div style={{
                fontFamily: 'var(--serif)', fontWeight: 700, fontSize: 13,
                borderBottom: '1px solid #000', paddingBottom: 2, marginBottom: 5,
              }}>
                {g.project?.name ?? 'Other'}
                <span style={{ float: 'right', fontFamily: 'var(--mono)', fontSize: 9.5, fontWeight: 400 }}>
                  {g.project?.dates ?? ''}
                </span>
              </div>
              <ul style={{ margin: 0, paddingLeft: 16 }}>
                {rows.map(r => (
                  <li key={r.bulletId} style={{ fontSize: 11.5, lineHeight: 1.45, marginBottom: 3 }}>
                    <RichText text={LAB_BULLET_MAP[r.bulletId]?.text ?? ''} />
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
        {over && (
          <div style={{
            borderTop: '2px dashed var(--rust)',
            color: 'var(--rust)',
            fontFamily: 'var(--mono)',
            fontSize: 10,
            fontWeight: 700,
            letterSpacing: '0.16em',
            paddingTop: 6,
            marginTop: 10,
          }}>
            PAGE 1 ENDS HERE — {lines - LAB_MAX_LINES} LINE(S) SPILL ONTO PAGE 2
          </div>
        )}
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <button className="minibtn" style={{ padding: '8px 10px' }}>↓ Download PDF</button>
        <button className="minibtn" style={{ padding: '8px 10px' }}>↓ Download .tex</button>
      </div>
    </div>
  );
}

function CoverPane() {
  const app = LAB_APP;
  return (
    <div>
      {app.coverLetterFlags.length > 0 && (
        <div className="callout">
          <div className="callout__head">Unverified figures</div>
          <b>{app.coverLetterFlags.join(', ')}</b> — this number is in neither your selected
          bullets nor the job description. Check it before sending.
        </div>
      )}
      <div style={{
        whiteSpace: 'pre-wrap',
        fontSize: 13.5,
        lineHeight: 1.6,
        maxHeight: 460,
        overflow: 'auto',
        background: 'var(--paper-2)',
        padding: 14,
        border: '1px solid var(--soft)',
      }}>
        {app.coverLetter}
      </div>
      <button className="minibtn" style={{ marginTop: 10, padding: '8px 10px' }}>Copy letter</button>
    </div>
  );
}

function ReviewPane({ selected, onJump }: { selected: Set<string>; onJump: () => void }) {
  const app = LAB_APP;
  const live = app.recruiterBulletVerdicts.filter(v => v.verdict !== 'keep' && selected.has(v.bulletId));
  const resolved = app.recruiterBulletVerdicts.filter(v => v.verdict !== 'keep' && !selected.has(v.bulletId));
  return (
    <div>
      {app.recruiterStale && (
        <div className="callout">
          <div className="callout__head">Out of date</div>
          This review scored an earlier selection. Rebuild the PDF to re-score.
        </div>
      )}

      <div className="eyebrow" style={{ marginBottom: 6 }}>Biggest hole</div>
      <p style={{ margin: '0 0 16px', fontSize: 13.5, lineHeight: 1.55 }}>
        {app.recruiterThinnestRequirement}
      </p>

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
                <RichText text={LAB_BULLET_MAP[v.bulletId]?.text ?? v.bulletId} />
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
                ✓ {LAB_BULLET_MAP[v.bulletId]?.text.slice(0, 70)}…
              </div>
            ))}
          </div>
        </>
      )}

      <div className="eyebrow" style={{ marginBottom: 6 }}>Objections a recruiter would raise</div>
      <ul style={{ margin: '0 0 16px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
        {app.recruiterWeaknesses.map((w, i) => <li key={i} style={{ marginBottom: 4 }}>{w}</li>)}
      </ul>

      <div className="eyebrow" style={{ marginBottom: 6 }}>Where you are strong</div>
      <ul style={{ margin: '0 0 16px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
        {app.fitStrengths.map(t => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}
      </ul>

      <div className="eyebrow" style={{ marginBottom: 6 }}>Where you are short</div>
      <ul style={{ margin: '0 0 12px', paddingLeft: 18, fontSize: 13, lineHeight: 1.55 }}>
        {app.fitGaps.map(t => <li key={t} style={{ marginBottom: 4 }}>{t}</li>)}
      </ul>

      <button className="minibtn" style={{ padding: '8px 10px' }} onClick={onJump}>
        Back to the page
      </button>
    </div>
  );
}

function AtsPane() {
  const app = LAB_APP;
  const total = app.atsMatched.length + app.atsMissing.length;
  return (
    <div>
      <div className="eyebrow" style={{ marginBottom: 8 }}>
        Keywords pulled from the job description, checked against your rendered page
      </div>
      <div className="meter meter--thick" style={{ marginBottom: 14 }}>
        <div className="meter__fill meter__fill--ok"
             style={{ width: `${Math.round((app.atsMatched.length / total) * 100)}%` }} />
      </div>

      <div className="eyebrow" style={{ marginBottom: 6 }}>On the page ({app.atsMatched.length})</div>
      <div className="kwgrid" style={{ marginBottom: 16 }}>
        {app.atsMatched.map(k => <span key={k} className="kw kw--hit">{k}</span>)}
      </div>

      <div className="eyebrow" style={{ marginBottom: 6 }}>Missing ({app.atsMissing.length})</div>
      <div className="kwgrid">
        {app.atsMissing.map(k => <span key={k} className="kw kw--miss">{k}</span>)}
      </div>
      <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 12, lineHeight: 1.5 }}>
        Missing is not automatically bad — only add a keyword if you can back it with real work.
      </p>
    </div>
  );
}
