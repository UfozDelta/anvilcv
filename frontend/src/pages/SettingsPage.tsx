import { useEffect, useState } from 'react';
import { useGenerationConfig } from '../hooks/useGenerationConfig';
import { GithubConnection } from '../components/github/GithubConnection';
import { PageTitle, useUnsavedGuard } from '../components/ledger/shared';
import type { GenerationConfig } from '../lib/api';

type RangeKey = 'singleLine' | 'doubleLine' | 'deadZone';
const RANGES: { key: RangeKey; label: string; min: number; max: number }[] = [
  { key: 'singleLine', label: '1-line range', min: 1, max: 50 },
  { key: 'doubleLine', label: '2-line range', min: 1, max: 100 },
  { key: 'deadZone', label: 'Dead zone (rejected)', min: 1, max: 100 },
];

const BOLD = [['NONE', 'None'], ['LIGHT', 'Light'], ['HEAVY', 'Heavy']] as const;
const TONE = [['CONSERVATIVE', 'Conservative'], ['NEUTRAL', 'Neutral'], ['AGGRESSIVE', 'Aggressive']] as const;
const VERB = [['TECHNICAL', 'Technical'], ['LEADERSHIP', 'Leadership'], ['IMPACT', 'Impact']] as const;

/** One setting: label and live value on top, the control under it, a rule between rows. */
function Setting({ label, value, children }: { label: string; value?: string; children: React.ReactNode }) {
  return (
    <div className="st-row">
      <div className="st-row__head">
        <span className="st-row__k">{label}</span>
        {value && <span className="st-row__v">{value}</span>}
      </div>
      <div className="st-row__ctl">{children}</div>
    </div>
  );
}

function Chips<T extends string>({ label, value, options, onChange }: { label: string; value: T; options: readonly (readonly [T, string])[]; onChange: (v: T) => void }) {
  return (
    <div className="na-lenses" role="group" aria-label={label}>
      {options.map(([k, text]) => (
        <button key={k} type="button" aria-pressed={value === k} onClick={() => onChange(k)}>{text}</button>
      ))}
    </div>
  );
}

export function SettingsPage() {
  const { cfg, set, loading, loaded, saving, savedAt, err, save } = useGenerationConfig();
  const [base, setBase] = useState<string | null>(null);
  const [justSaved, setJustSaved] = useState(false);
  const snap = JSON.stringify(cfg);

  // Baseline for "unsaved": what the server last gave us (first load, then each save).
  useEffect(() => { if (!loading && base === null) setBase(snap); }, [loading, base, snap]);
  useEffect(() => {
    if (!savedAt) return;
    setBase(JSON.stringify(cfg));
    setJustSaved(true);
    const t = window.setTimeout(() => setJustSaved(false), 2000);
    return () => window.clearTimeout(t);
  }, [savedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  const dirty = base !== null && snap !== base;
  useUnsavedGuard(dirty);

  if (loading) return <div className="shell ap-page pf-page"><span className="spinner">LOADING</span></div>;
  // Never show editable defaults after a failed load: Save would write them over the real settings.
  if (!loaded) return <div className="shell ap-page pf-page"><div className="err" role="alert">{err || 'Failed to load settings'}</div></div>;

  const num = (k: keyof GenerationConfig) => cfg[k] as number;
  /** Low and high can touch but never cross. */
  const setRange = (key: RangeKey, end: 'Low' | 'High', v: number) => {
    const lo = num(`${key}Low`), hi = num(`${key}High`);
    set(`${key}${end}` as keyof GenerationConfig, (end === 'Low' ? Math.min(v, hi) : Math.max(v, lo)) as never);
  };

  return (
    <div className="shell ap-page pf-page">
      <PageTitle title="Settings" />

      <form onSubmit={(e) => { e.preventDefault(); void save(); }}>
        <section className="pf-sec">
          <h2 className="ap-label">Word filter</h2>
          <label className="st-switch">
            <input type="checkbox" checked={cfg.wordFilterEnabled} onChange={(e) => set('wordFilterEnabled', e.target.checked)} />
            <span>{cfg.wordFilterEnabled ? 'On — bullets outside the ranges are dropped' : 'Off — every bullet passes through'}</span>
          </label>
          <fieldset className="st-group" disabled={!cfg.wordFilterEnabled}>
            <legend className="sr-only">Word ranges</legend>
            {RANGES.map((r) => {
              const lo = num(`${r.key}Low`), hi = num(`${r.key}High`);
              return (
                <Setting key={r.key} label={r.label} value={`${lo}–${hi} words`}>
                  <div className="st-pair">
                    <label className="st-end"><span className="st-cap">Min</span>
                      <input type="range" className="st-range" min={r.min} max={r.max} value={lo} aria-label={`${r.label} minimum`} onChange={(e) => setRange(r.key, 'Low', +e.target.value)} />
                    </label>
                    <label className="st-end"><span className="st-cap">Max</span>
                      <input type="range" className="st-range" min={r.min} max={r.max} value={hi} aria-label={`${r.label} maximum`} onChange={(e) => setRange(r.key, 'High', +e.target.value)} />
                    </label>
                  </div>
                </Setting>
              );
            })}
            <Setting label="Min word floor" value={`${cfg.minWordFloor} words`}>
              <input type="range" className="st-range" min={1} max={50} value={cfg.minWordFloor} aria-label="Minimum word floor" onChange={(e) => set('minWordFloor', +e.target.value)} />
            </Setting>
          </fieldset>
        </section>

        <section className="pf-sec">
          <h2 className="ap-label">Generation tuning</h2>
          <div className="st-group">
            <Setting label="Temperature" value={cfg.temperature.toFixed(2)}>
              <input type="range" className="st-range" min={0} max={2} step={0.05} value={cfg.temperature} aria-label="Temperature" onChange={(e) => set('temperature', +e.target.value)} />
            </Setting>
            <Setting label="Bold density"><Chips label="Bold density" value={cfg.boldDensity} options={BOLD} onChange={(v) => set('boldDensity', v)} /></Setting>
            <Setting label="Tone"><Chips label="Tone" value={cfg.tone} options={TONE} onChange={(v) => set('tone', v)} /></Setting>
            <Setting label="Action verb style"><Chips label="Action verb style" value={cfg.actionVerbStyle} options={VERB} onChange={(v) => set('actionVerbStyle', v)} /></Setting>
          </div>
        </section>

        <GithubConnection />

        {(dirty || justSaved || err) && (
          <div className="pf-save" role={err ? 'alert' : 'status'}>
            <span>{err ?? (dirty ? 'Unsaved changes' : 'Saved')}</span>
            {dirty && <button type="submit" className="ap-btn ap-btn--acid" disabled={saving}>{saving ? 'Saving…' : 'Save settings'}</button>}
          </div>
        )}
      </form>
    </div>
  );
}
