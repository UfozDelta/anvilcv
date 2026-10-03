import { useEffect, useRef, useState } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import './steps.css';
import { useAvgSec } from './useAvgSec';

export const STEPS = [
  { title: 'Bank it once', body: 'Every role and project you have done, six to twelve bullets each. AI drafts them, you edit. The last time you write a bullet from scratch.' },
  { title: 'Paste a job post', body: 'Raw text or a link. Anvil pulls out the company, the role and the keywords an ATS will screen for.' },
  { title: 'Every bullet scored', body: 'All of them, against this job. The best rise to the top, and no single role gets more than three, so the page still shows range.' },
  { title: 'One page, ~17 seconds', body: 'A typeset one-page PDF and a cover letter for the same job. Swap or pin anything before you download.' },
];

/**
 * "How it works" as a side-by-side scroll: the steps scroll on one side while the
 * bank → JD → ranking → page visual (from the v2 prototype) stays pinned on the other.
 * The step crossing the reading line (62% down the viewport) drives the stage.
 */
export function PinnedSteps({ Stage }: { Stage: (p: { step: number }) => React.ReactNode }) {
  const [step, setStep] = useState(1);
  const sec = useAvgSec();
  const stepsRef = useRef<HTMLOListElement>(null);
  const { scrollYProgress } = useScroll({ target: stepsRef, offset: ['start 62%', 'end 62%'] });
  // Full transform string: the shorthand runs on the main thread and drops frames.
  const fill = useTransform(scrollYProgress, (v) => `scaleY(${v})`);

  useEffect(() => {
    const items = Array.from(stepsRef.current?.children ?? []) as HTMLElement[];
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) setStep(Number((e.target as HTMLElement).dataset.i) + 1);
        });
      },
      { rootMargin: '-62% 0px -37% 0px' },
    );
    items.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <div className="lx-how lx-compact">
      <div className="lx-how__pin">
        <Stage step={step} />
      </div>
      <div className="lv-story__steps lx-how__steps">
        <div className="lv-story__rail lx-how__rail" aria-hidden="true">
          <motion.span className="lv-story__fill" style={{ transform: fill }} />
        </div>
        <ol ref={stepsRef} className="lv-steps">
          {STEPS.map((s, i) => (
            <li key={s.title} data-i={i} className="lv-step" data-on={step === i + 1 || undefined} data-done={step > i + 1 || undefined}>
              <span className="lv-step__num">{String(i + 1).padStart(2, '0')}</span>
              <h3 className="lp-display lv-step__title">{s.title.replace('~17', `~${sec}`)}</h3>
              <p className="lv-step__body">{s.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
