import { useEffect, useRef, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { SETTINGS, type DemoSettings } from './appData';
import { PageTitle, useDemoPage } from './shared';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

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

function GithubSection() {
  const [connected, setConnected] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const timer = useRef<number>();
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const askDisconnect = () => {
    if (confirming) { setConnected(false); setConfirming(false); return; }
    setConfirming(true);
    timer.current = window.setTimeout(() => setConfirming(false), 4000);
  };

  return (
    <section className="pf-sec">
      <h2 className="ap-label">GitHub</h2>
      <div className="st-gh">
        {connected ? (
          <>
            <span className="st-gh__who"><span className="st-gh__dot" aria-hidden="true" />Connected as <strong>maks</strong></span>
            <span className="st-gh__btns">
              <a href="#" className="ap-btn ap-btn--ghost st-btn" onClick={(e) => e.preventDefault()}>Choose repos ↗</a>
              <button type="button" className={`ap-btn ap-btn--ghost st-btn${confirming ? ' st-btn--danger' : ''}`} onClick={askDisconnect}>
                {confirming ? 'Confirm disconnect' : 'Disconnect'}
              </button>
            </span>
          </>
        ) : (
          <>
            <span className="st-gh__who">Not connected</span>
            <button type="button" className="ap-btn ap-btn--acid st-btn" onClick={() => setConnected(true)}>Connect GitHub</button>
          </>
        )}
      </div>
    </section>
  );
}

function SettingsLedger() {
  const [cfg, setCfg] = useState<DemoSettings>(SETTINGS);
  const [saved, setSaved] = useState(SETTINGS);
  const [justSaved, setJustSaved] = useState(false);
  const dirty = JSON.stringify(cfg) !== JSON.stringify(saved);

  useEffect(() => {
    if (!justSaved) return;
    const t = window.setTimeout(() => setJustSaved(false), 2000);
    return () => window.clearTimeout(t);
  }, [justSaved]);

  const set = <K extends keyof DemoSettings>(k: K, v: DemoSettings[K]) => setCfg((c) => ({ ...c, [k]: v }));
  /** Low and high can touch but never cross. */
  const setRange = (key: RangeKey, end: 'Low' | 'High', v: number) =>
    setCfg((c) => {
      const lo = c[`${key}Low` as const], hi = c[`${key}High` as const];
      return { ...c, [`${key}${end}`]: end === 'Low' ? Math.min(v, hi) : Math.max(v, lo) };
    });
  const save = (e: React.FormEvent) => { e.preventDefault(); setSaved(cfg); setJustSaved(true); };

  return (
    <div className="shell ap-page pf-page">
      <PageTitle title="Settings" />

      <form onSubmit={save}>
        <section className="pf-sec">
          <h2 className="ap-label">Word filter</h2>
          <label className="st-switch">
            <input type="checkbox" checked={cfg.wordFilterEnabled} onChange={(e) => set('wordFilterEnabled', e.target.checked)} />
            <span>{cfg.wordFilterEnabled ? 'On — bullets outside the ranges are dropped' : 'Off — every bullet passes through'}</span>
          </label>
          <fieldset className="st-group" disabled={!cfg.wordFilterEnabled}>
            <legend className="sr-only">Word ranges</legend>
            {RANGES.map((r) => {
              const lo = cfg[`${r.key}Low` as const], hi = cfg[`${r.key}High` as const];
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

        <GithubSection />

        {(dirty || justSaved) && (
          <div className="pf-save" role="status">
            <span>{dirty ? 'Unsaved changes' : 'Saved'}</span>
            {dirty && <button type="submit" className="ap-btn ap-btn--acid">Save settings</button>}
          </div>
        )}
      </form>
    </div>
  );
}

/** /lab/settings — Generation settings + GitHub in the Ledger style, under the Strip nav. R replays. */
export function LabSettings() {
  const { mountKey, replay } = useDemoPage(false);
  return (
    <div className="ap-root">
      <NavStrip />
      <SettingsLedger key={mountKey} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
