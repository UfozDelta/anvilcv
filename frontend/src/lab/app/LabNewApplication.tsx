import { useEffect, useMemo, useRef, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { PageTitle, useDemoPage } from './shared';
import { CATEGORIES } from '../../lib/api';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

const LENSES = [...CATEGORIES.map((c) => ({ slug: c.slug, label: c.label })), { slug: 'generalist', label: 'Generalist' }];

const STAGES = [
  { name: 'Clean', ms: 1100 },
  { name: 'Rank', ms: 1800 },
  { name: 'Draft', ms: 1500 },
  { name: 'Render', ms: 1300 },
];
const COVER_LETTER = { name: 'Cover letter', ms: 1200 };

const looksLikeUrl = (s: string) => /^https?:\/\/\S+\.\S+/i.test(s.trim());

/** Run progress: -1 idle, 0..n-1 the active stage, n = done. A demo clock, not a real job. */
function useDemoRun(stageMs: number[]) {
  const [step, setStep] = useState(-1);
  const timer = useRef<number>();
  useEffect(() => {
    if (step < 0 || step >= stageMs.length) return;
    timer.current = window.setTimeout(() => setStep((s) => s + 1), stageMs[step]);
    return () => window.clearTimeout(timer.current);
  }, [step, stageMs]);
  return { step, start: () => setStep(0), reset: () => setStep(-1) };
}

function NewApplicationForm() {
  const [text, setText] = useState('');
  const [url, setUrl] = useState('');
  const [lens, setLens] = useState('backend');
  const [cover, setCover] = useState(false);
  const [touched, setTouched] = useState(false);

  const stages = useMemo(() => (cover ? [...STAGES, COVER_LETTER] : STAGES), [cover]);
  const stageMs = useMemo(() => stages.map((s) => s.ms), [stages]);
  const run = useDemoRun(stageMs);
  const running = run.step >= 0 && run.step < stages.length;
  const done = run.step >= stages.length;
  const locked = running || done;
  const urlBad = url.trim() !== '' && !looksLikeUrl(url);
  const ready = (text.trim().length > 0 || looksLikeUrl(url)) && !urlBad;
  const missing = touched && !ready;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (ready) run.start();
  };

  return (
    <div className="shell ap-page na-page">
      <PageTitle title="New application" />

      <form className="na-form" onSubmit={submit} aria-busy={running}>
        <fieldset className="na-sec" disabled={locked}>
          <legend className="sr-only">The job</legend>
          <textarea
            className="na-input na-text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste the full posting"
            aria-label="Job description"
            aria-invalid={missing || undefined}
            aria-describedby={missing || urlBad ? 'na-err' : undefined}
          />
          <input
            className="na-input na-url"
            inputMode="url"
            autoComplete="off"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="…or paste a job URL"
            aria-label="Job URL"
            aria-invalid={urlBad || missing || undefined}
            aria-describedby={missing || urlBad ? 'na-err' : undefined}
          />
          {(urlBad || missing) && (
            <p id="na-err" className="na-hint na-hint--err" role="alert">
              {urlBad ? 'URL needs to start with https://' : 'Add the posting text or a URL first.'}
            </p>
          )}
        </fieldset>

        <fieldset className="na-sec" disabled={locked}>
          <legend className="ap-label">Emphasis</legend>
          <div className="na-lenses" role="group" aria-label="Role emphasis">
            {LENSES.map((l) => (
              <button key={l.slug} type="button" aria-pressed={lens === l.slug} onClick={() => setLens(l.slug)}>{l.label}</button>
            ))}
          </div>
        </fieldset>

        <div className="na-submit">
          {locked ? (
            <div className="na-run">
              <span className="na-run__label" role="status">
                {done ? 'Ready' : <>{stages[run.step].name}… <span>{run.step + 1}/{stages.length}</span></>}
              </span>
              <span className="na-run__bar" role="progressbar" aria-label="Tailoring progress" aria-valuemin={0} aria-valuemax={stages.length} aria-valuenow={Math.min(run.step, stages.length)}>
                {done
                  ? <i style={{ transform: 'scaleX(1)' }} />
                  : <i key={run.step} style={{ animationDuration: `${stages[run.step].ms}ms` }} />}
              </span>
            </div>
          ) : (
            <label className="na-check">
              <input type="checkbox" checked={cover} onChange={(e) => setCover(e.target.checked)} />
              <span>Cover letter</span>
            </label>
          )}
          {done ? (
            <span className="na-done">
              <button type="button" className="ap-btn ap-btn--ghost" onClick={run.reset}>Another</button>
              <a href="#" className="ap-btn ap-btn--ink" onClick={(e) => e.preventDefault()}>Open →</a>
            </span>
          ) : (
            <button type="submit" className="ap-btn ap-btn--acid" disabled={running}>{running ? 'Tailoring…' : 'Run pipeline →'}</button>
          )}
        </div>
      </form>
    </div>
  );
}

/** /lab/new-application — New application in the Ledger style, under the Strip nav. R resets the demo run. */
export function LabNewApplication() {
  const { mountKey, replay } = useDemoPage(false);
  return (
    <div className="ap-root">
      <NavStrip />
      <NewApplicationForm key={mountKey} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
