import { forwardRef, useEffect, useRef, useState } from 'react';
import { motion, useInView } from 'framer-motion';
import { BULLETS, SAMPLE_JDS } from './heroData';
import { usePrefersReducedMotion } from './useHeroLoop';

const PICKED = 4;
// No bounce: a tap-triggered re-sort carries no momentum to overshoot with.
const SPRING = { type: 'spring', duration: 0.4, bounce: 0 } as const;
// Opens on DATA so it differs from the backend JD the hero just showed.
const START = 1;
const AUTO_TO = 2;

type RerankProps = { selected?: number; onSelect?: (i: number) => void };

/**
 * One bullet bank re-ranked against three sample JDs. Switches once by itself when it
 * scrolls into view, so the re-sort is seen; after that it only moves on input.
 * Controlled when `selected`/`onSelect` are passed, otherwise keeps its own state.
 */
export const RerankPanel = forwardRef<HTMLDivElement, RerankProps>(function RerankPanel({ selected, onSelect }, outerRef) {
  const reduced = usePrefersReducedMotion();
  const [inner, setInner] = useState(START);
  const idx = selected ?? inner;
  const select = onSelect ?? setInner;
  const touched = useRef(false);
  // Any selection away from START (a tab, or a job card upstream) cancels the auto-switch.
  if (idx !== START) touched.current = true;
  const ref = useRef<HTMLDivElement | null>(null);
  const seen = useInView(ref, { once: true, amount: 0.6 });

  useEffect(() => {
    if (!seen || reduced) return;
    const t = window.setTimeout(() => { if (!touched.current) select(AUTO_TO); }, 1400);
    return () => window.clearTimeout(t);
  }, [seen, reduced, select]);

  const pick = (i: number) => { touched.current = true; select(i); };
  const jd = SAMPLE_JDS[idx];
  const ranked = [...BULLETS].sort((a, b) => jd.scores[b.id] - jd.scores[a.id]);

  return (
    <div
      className="hx-rr"
      ref={(el) => {
        ref.current = el;
        if (typeof outerRef === 'function') outerRef(el);
        else if (outerRef) outerRef.current = el;
      }}
    >
      <div className="hx-rr__head">
        <span className="lp-label lp-muted">ONE BANK · PICK A JOB</span>
        <div className="hx-rr__tabs" role="tablist">
          {SAMPLE_JDS.map((j, i) => (
            <button
              key={j.id}
              role="tab"
              aria-selected={i === idx}
              className="hx-rr__tab"
              onClick={() => pick(i)}
            >
              {j.label}
            </button>
          ))}
        </div>
      </div>

      <div className="hx-rr__role">
        <span className="lp-display hx-rr__role-name">{jd.role}</span>
        <span className="lp-label lp-muted">@ {jd.company.toUpperCase()}</span>
      </div>

      <ul className="hx-rr__list">
        {ranked.map((b, i) => {
          const score = jd.scores[b.id];
          const picked = i < PICKED;
          return (
            <motion.li
              key={b.id}
              layout={!reduced}
              transition={SPRING}
              className="hx-rr__row"
              data-picked={picked || undefined}
            >
              <span className="hx-rr__rank">{String(i + 1).padStart(2, '0')}</span>
              <span className="hx-rr__text">{b.text}</span>
              <span className="hx-rr__score">
                <span className="hx-rr__bar" style={{ transform: `scaleX(${score / 100})` }} />
                <span className="hx-rr__num">{score}</span>
              </span>
            </motion.li>
          );
        })}
      </ul>

      <div className="hx-rr__foot">
        {jd.matched.map((k) => <span key={k} className="lp-tag lp-tag--acid">{k} ✓</span>)}
        {jd.missing.map((k) => <span key={k} className="lp-tag lp-tag--miss">{k} : MISSING</span>)}
        <span className="lp-label lp-muted hx-rr__count">{PICKED} OF 34 ON THE PAGE</span>
      </div>
    </div>
  );
});
