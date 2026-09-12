import type { Dispatch, SetStateAction } from 'react';
import type { Project } from '../../lib/api';
import { DISPLAY_GROUPS } from '../../lib/lensDerivation';
import type { Lens } from '../../lib/lensDerivation';

export function GenerateTab(props: {
  project: Project; isExperience: boolean;
  filledCount: number; setTab: (t: 'bullets' | 'generate' | 'info') => void;
  generating: boolean; genPct: number;
  picked: Set<string>; setPicked: Dispatch<SetStateAction<Set<string>>>;
  pickedBuckets: Set<string>; togglePick: (slug: string) => void; toggleBucket: (group: string) => void;
  lenses: Lens[]; lensCount: Record<string, number>; displayGroupCount: Record<string, number>;
  generateBank: () => void;
  customText: string; setCustomText: (v: string) => void;
  customAdding: boolean; addCustomBullet: () => void; customMsg: string | null;
  suggestedLens: Lens | null;
}) {
  const {
    project, isExperience, filledCount, setTab, generating, genPct,
    picked, setPicked, pickedBuckets, togglePick, toggleBucket,
    lenses, lensCount, displayGroupCount, generateBank,
    customText, setCustomText, customAdding, addCustomBullet, customMsg, suggestedLens,
  } = props;

  return (
    <div>
      {filledCount === 0 && (
        <div className="callout" style={{ marginBottom: 16 }}>
          <div className="callout__head">Context is empty</div>
          Nothing in Tech stack, Scale &amp; impact or the other context fields yet — there
          are no lenses to derive until something's filled in.{' '}
          <button className="minibtn" onClick={() => setTab('info')}>Fill context →</button>
        </div>
      )}

      {generating ? (
        <div style={{ padding: '24px 0' }}>
          <div className="eyebrow" style={{ marginBottom: 10 }}>
            Generating {(picked.size || lenses.length) + pickedBuckets.size} bullet{((picked.size || lenses.length) + pickedBuckets.size) === 1 ? '' : 's'}…
          </div>
          <div className="meter meter--thick">
            <div className="meter__fill meter__fill--ok" style={{ width: `${genPct}%` }} />
          </div>
          <div className="label muted" style={{ marginTop: 8 }}>{genPct}% — new bullets land in the Bank tab as they finish.</div>
        </div>
      ) : (
        <>
          <div className="eyebrow" style={{ marginBottom: 10 }}>Pick a focus</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 8, marginBottom: 24 }}>
            {DISPLAY_GROUPS.map(group => {
              const on = pickedBuckets.has(group);
              const count = displayGroupCount[group] ?? 0;
              return (
                <button
                  type="button"
                  key={group}
                  onClick={() => toggleBucket(group)}
                  className="btn btn--sm"
                  style={{
                    justifyContent: 'space-between',
                    padding: '12px 14px',
                    background: on ? 'var(--ink)' : 'var(--paper)',
                    color: on ? 'var(--paper)' : 'var(--ink)',
                    borderColor: 'var(--ink)',
                  }}
                  title={`${count} bullet${count === 1 ? '' : 's'} already in ${group}`}
                >
                  <span style={{ fontSize: 12, letterSpacing: '0.14em' }}>{on ? '✓' : '○'} {group.toUpperCase()}</span>
                  <span style={{ fontFamily: 'var(--mono)', fontSize: 11, opacity: 0.7 }}>{count}</span>
                </button>
              );
            })}
          </div>

          <div className="row row--between row--centered" style={{ marginBottom: 4 }}>
            <div className="eyebrow">Lenses for {isExperience ? (project.title || project.name) : project.name}</div>
            {lenses.length > 0 && (
              <div className="row" style={{ gap: 8 }}>
                <button className="minibtn" onClick={() => setPicked(new Set(lenses.map(l => l.slug)))}>Select all</button>
                <button className="minibtn" onClick={() => setPicked(new Set())} disabled={picked.size === 0}>Clear</button>
              </div>
            )}
          </div>
          <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 10, maxWidth: 560 }}>
            Derived from this project's own tech stack and context — a different project
            gets a different list. Leave everything unpicked and Generate runs all of them.
          </div>

          {lenses.length === 0 ? (
            <div className="editorial muted" style={{ padding: '20px 0' }}>
              Nothing to derive lenses from yet. Fill in Tech stack or another context field
              on the Info tab.
            </div>
          ) : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
              {lenses.map(lens => {
                const on = picked.has(lens.slug);
                const existing = lensCount[lens.slug];
                return (
                  <button
                    type="button"
                    key={lens.slug}
                    onClick={() => togglePick(lens.slug)}
                    className="btn btn--sm"
                    style={{
                      padding: '5px 10px',
                      background: on ? 'var(--ink)' : 'var(--paper)',
                      color: on ? 'var(--paper)' : 'var(--ink)',
                      borderColor: 'var(--ink)',
                    }}
                    title={lens.blurb}
                  >
                    <span style={{ fontSize: 10.5, letterSpacing: '0.08em' }}>
                      {on ? '✓' : '○'} {lens.label}
                    </span>
                    {existing !== undefined && (
                      <span style={{ fontFamily: 'var(--mono)', fontSize: 9.5, marginLeft: 6, opacity: existing === 0 ? 0.4 : 0.75 }}>
                        {existing}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}

          <div className="row row--between row--centered" style={{ marginBottom: 24 }}>
            <span className="label muted">
              {(() => {
                const dynCount = picked.size || lenses.length;
                const total = dynCount + pickedBuckets.size;
                return total === 0 ? 'NOTHING TO GENERATE YET' : `${total} TARGET${total === 1 ? '' : 'S'} · ~${total}–${total * 2} NEW BULLETS`;
              })()}
            </span>
            <button className="btn btn--acid" disabled={lenses.length === 0 && pickedBuckets.size === 0} onClick={generateBank}>↻ GENERATE BANK</button>
          </div>

          <div className="panel panel--inset stack-sm">
            <div className="label">OR DESCRIBE ONE YOURSELF</div>
            <div style={{ fontSize: 12, color: 'var(--muted)' }}>
              Not on the list? Type the moment and get one bullet for it — no lens required.
            </div>

            {suggestedLens && !customText && (
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
                fontSize: 11.5, color: 'var(--muted)', padding: '6px 8px', border: '1px dashed var(--soft)',
              }}>
                <span>
                  Nothing covers <b style={{ color: 'var(--ink)' }}>{suggestedLens.label}</b> yet —
                  describe something specific to it, or pick it above.
                </span>
                <button
                  className="minibtn"
                  onClick={() => setCustomText(`The time I worked on ${suggestedLens.label}: `)}
                >
                  Use this
                </button>
              </div>
            )}

            <textarea
              className="field__textarea"
              value={customText}
              onChange={e => setCustomText(e.target.value)}
              style={{ minHeight: 60 }}
              placeholder="e.g. the time I migrated the primary DB under live traffic with no downtime"
            />
            <div className="row">
              <button className="btn btn--sm" onClick={addCustomBullet} disabled={!customText.trim() || customAdding}>
                {customAdding ? 'Writing…' : 'Generate this one'}
              </button>
            </div>
            {customMsg && <div className="label muted">{customMsg}</div>}
          </div>
        </>
      )}
    </div>
  );
}
