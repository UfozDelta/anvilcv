import { useEffect, useRef, useState } from 'react';
import { useInView } from 'framer-motion';
import { StoryStage } from '../landing-v2/StoryStage';
import { STEPS } from '../../components/landing/PinnedSteps';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import '../landing-v2/landing-v2.css';

const LABELS = ['Bank', 'Paste', 'Score', 'PDF'];
const STEP_MS = 3600;

/**
 * No scroll mechanics: numbered step tabs over the stage. When it scrolls into view it
 * plays through once (each tab's bar fills as its time runs), then stops on the PDF.
 * Any click takes over for good. Caption sits directly under the stage.
 */
export function HowTour() {
  const reduced = usePrefersReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const seen = useInView(ref, { once: true, amount: 0.5 });
  const [step, setStep] = useState(1);
  const [auto, setAuto] = useState(true);
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const playing = auto && seen && !reduced && step < STEPS.length;

  useEffect(() => {
    if (!playing) return;
    const t = window.setTimeout(() => setStep((s) => s + 1), STEP_MS);
    return () => window.clearTimeout(t);
  }, [playing, step]);

  const go = (i: number, focus = false) => {
    setAuto(false);
    setStep(i);
    if (focus) tabs.current[i - 1]?.focus();
  };
  const onKey = (e: React.KeyboardEvent) => {
    const n = STEPS.length;
    if (e.key === 'ArrowRight') go((step % n) + 1, true);
    else if (e.key === 'ArrowLeft') go(((step + n - 2) % n) + 1, true);
    else if (e.key === 'Home') go(1, true);
    else if (e.key === 'End') go(n, true);
    else return;
    e.preventDefault();
  };
  const s = STEPS[step - 1];

  return (
    <div className="hw-tour lx-compact" ref={ref}>
      <div className="hw-tour__tabs" role="tablist" aria-label="How it works" onKeyDown={onKey}>
        {LABELS.map((label, i) => {
          const n = i + 1;
          return (
            <button
              key={label}
              ref={(el) => { tabs.current[i] = el; }}
              role="tab"
              id={`hw-tab-${n}`}
              aria-selected={step === n}
              aria-controls="hw-tour-panel"
              tabIndex={step === n ? 0 : -1}
              className="hw-tab"
              data-done={step > n || undefined}
              onClick={() => go(n)}
            >
              <span className="hw-tab__num">{String(n).padStart(2, '0')}</span>
              <span className="hw-tab__label">{label}</span>
              {/* Fill = time left on this step while auto-playing; full when done or picked. */}
              <span
                key={`${step}-${playing}`}
                className="hw-tab__bar"
                data-mode={step > n ? 'full' : step === n ? (playing ? 'run' : 'full') : 'empty'}
                style={{ animationDuration: `${STEP_MS}ms` }}
              />
            </button>
          );
        })}
      </div>
      <div id="hw-tour-panel" role="tabpanel" aria-labelledby={`hw-tab-${step}`} className="hw-tour__panel">
        <StoryStage step={step} />
        <div key={step} className="hw-caption">
          <span className="hw-caption__num" aria-hidden="true">{String(step).padStart(2, '0')}</span>
          <div>
            <h3 className="lp-display hw-caption__title">{s.title}</h3>
            <p className="hw-caption__body">{s.body}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
