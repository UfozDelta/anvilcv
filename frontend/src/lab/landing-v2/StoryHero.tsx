import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, useScroll, useTransform } from 'framer-motion';
import { StoryStage } from './StoryStage';

const STEPS = [
  { title: 'Bank it once', body: 'Every role and project you have done, six to twelve bullets each. AI drafts them, you edit. The last time you write a bullet from scratch.' },
  { title: 'Paste a job post', body: 'Raw text or a link. Anvil pulls out the company, the role and the keywords an ATS will screen for.' },
  { title: 'Every bullet scored', body: 'All of them, against this job. The best rise to the top, and no single role gets more than three, so the page still shows range.' },
  { title: 'One page, ~17 seconds', body: 'A LaTeX-typeset PDF and a cover letter for the same job. Swap or pin anything before you download.' },
];

/**
 * Hero and "how it works" in one section: the résumé page in the hero stays pinned
 * while the steps scroll past it, then rewinds to show how that page was made.
 * The step under the reading line (62% down the viewport, below the pinned stage on
 * phones) drives the stage; before the first step the stage shows the finished page.
 */
export function StoryHero() {
  const [step, setStep] = useState(0);
  const stepsRef = useRef<HTMLOListElement>(null);
  const { scrollYProgress } = useScroll({ target: stepsRef, offset: ['start 62%', 'end 62%'] });
  // Full transform string: the shorthand runs on the main thread and drops frames.
  const fill = useTransform(scrollYProgress, (v) => `scaleY(${v})`);

  useEffect(() => {
    const items = Array.from(stepsRef.current?.children ?? []) as HTMLElement[];
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          const i = Number((e.target as HTMLElement).dataset.i);
          if (e.isIntersecting) setStep(i + 1);
          // Scrolled back above the first step: show the hero's finished page again.
          else if (i === 0 && e.boundingClientRect.top > 0) setStep(0);
        });
      },
      { rootMargin: '-62% 0px -37% 0px' },
    );
    items.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <section className="lv-story shell">
      <div className="lv-story__copy">
        <h1 className="lp-display lv-title">
          Anvil<br />
          <span className="lp-hero__slash">// </span>CV
        </h1>
        <p className="lp-editorial lv-sub">
          Keep every bullet you have ever written. Paste a job post, and Anvil builds the one-page
          résumé that job wants, cover letter included.
        </p>
        <div className="lv-ctas">
          <Link to="/login" className="lp-btn lp-btn--acid lv-btn">GET STARTED &nbsp;→</Link>
          <Link to="/jobs" className="lv-link">BROWSE JOBS →</Link>
        </div>
        <p className="lv-stat"><strong>~17s</strong> <span className="lp-label lp-muted">from job post to PDF</span></p>
        <p className="lp-label lp-muted lv-scrollcue" data-off={step > 0 || undefined}>↓ SCROLL TO SEE HOW THIS PAGE WAS MADE</p>
      </div>

      <div className="lv-story__pin">
        <StoryStage step={step} />
      </div>

      <div className="lv-story__steps">
        <h2 className="lp-section-title lv-story__h2">How it works</h2>
        <div className="lv-story__rail" aria-hidden="true">
          <motion.span className="lv-story__fill" style={{ transform: fill }} />
        </div>
        <ol ref={stepsRef} className="lv-steps">
          {STEPS.map((s, i) => (
            <li key={s.title} data-i={i} className="lv-step" data-on={step === i + 1 || undefined} data-done={step > i + 1 || undefined}>
              <span className="lv-step__num">{String(i + 1).padStart(2, '0')}</span>
              <h3 className="lp-display lv-step__title">{s.title}</h3>
              <p className="lv-step__body">{s.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
