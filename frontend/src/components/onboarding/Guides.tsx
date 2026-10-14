import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { usePrefersReducedMotion } from '../landing/useHeroLoop';
import { useField } from '../../lib/field';
import { findTarget, useGuide } from './guide';
import { completeOnboarding, useProgress, type Progress } from './store';
import './onboarding.css';

const EASE = [0.23, 1, 0.32, 1] as const;

/** Sticky bar under the nav: one next step, four-fifths of the app untouched. */
function Strip({ g }: { g: ReturnType<typeof useGuide> }) {
  const c = g.current;
  return (
    <div className="ob-strip" role="status">
      <span className="ob-strip__k">Next</span>
      <strong key={c?.id ?? 'done'} className="ob-strip__now">{c ? c.label : 'All set'}</strong>
      <ol className="ob-strip__seg" aria-label={`${g.done} of ${g.steps.length} done`}>
        {g.steps.map((s) => <li key={s.id} data-on={s.done || undefined} title={s.label} />)}
      </ol>
      <button type="button" className="ob-skip" onClick={() => void completeOnboarding()}>Hide tips</button>
    </div>
  );
}

type Box = { x: number; y: number; w: number; h: number; onPage: boolean };

/** One spotlight on the real control for the current step. Falls back to a corner tip when there is nothing to point at. */
function Coach({ g }: { g: ReturnType<typeof useGuide> }) {
  const reduce = usePrefersReducedMotion();
  const [box, setBox] = useState<Box | null>(null);
  const raf = useRef(0);
  const id = g.current?.id;

  useLayoutEffect(() => {
    if (!id) { setBox(null); return; }
    const main = document.querySelector('main.ap-main');
    if (!main) return;
    const measure = () => {
      const { el, onPage } = findTarget(id, g.pathname);
      if (!el) { setBox(null); return; }
      const vr = el.getBoundingClientRect();
      if (vr.top < 70 || vr.bottom > window.innerHeight - 40) el.scrollIntoView?.({ block: 'center', behavior: reduce ? 'auto' : 'smooth' });
      const r = el.getBoundingClientRect();
      setBox({ x: r.left - 6, y: r.top - 6, w: r.width + 12, h: r.height + 12, onPage });
    };
    const queue = () => { cancelAnimationFrame(raf.current); raf.current = requestAnimationFrame(measure); };
    queue();
    const mo = new MutationObserver(queue);
    mo.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-pressed', 'class'] });
    window.addEventListener('resize', queue);
    window.addEventListener('scroll', queue, true);
    return () => {
      mo.disconnect();
      window.removeEventListener('resize', queue);
      window.removeEventListener('scroll', queue, true);
      cancelAnimationFrame(raf.current);
    };
  }, [id, g.here, g.pathname, reduce]);

  const c = g.current;
  const tipX = box ? Math.max(8, Math.min(box.x, window.innerWidth - 316)) : 0;
  return (
    <>
      <AnimatePresence>
        {c && box && (
          <>
            <motion.div key="ring" className="ob-ring" aria-hidden
              initial={{ opacity: 0, x: box.x, y: box.y, width: box.w, height: box.h }}
              animate={{ opacity: 1, x: box.x, y: box.y, width: box.w, height: box.h }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }} />
            <motion.div key="tip" className="ob-tip" role="status"
              initial={{ opacity: 0, x: tipX, y: box.y + box.h + 18 }}
              animate={{ opacity: 1, x: tipX, y: box.y + box.h + 12 }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }}>
              <span className="ap-label">{g.done + 1} of {g.steps.length}</span>
              <strong>{c.title}</strong>
              <span>{c.body}</span>
              <span className="ob-tip__acts">
                {!box.onPage && <button type="button" className="ob-tip__go" onClick={g.go}>Take me there</button>}
                <button type="button" className="ob-skip" onClick={() => void completeOnboarding()}>Skip tips</button>
              </span>
            </motion.div>
          </>
        )}
      </AnimatePresence>
      {c && !box && (
        <div className="ob-corner" role="status">
          <span className="ap-label">{g.done + 1} of {g.steps.length}</span>
          <strong>{c.title}</strong>
          <span>{c.body}</span>
          {!g.here && <button type="button" className="ap-btn ap-btn--acid" onClick={g.go}>Take me there</button>}
        </div>
      )}
    </>
  );
}

function Guide({ p }: { p: Progress }) {
  const { field } = useField();
  const g = useGuide(p, field);
  const finished = !g.current;
  // Every step done: the guide has done its job, so stop showing it on the next visit.
  useEffect(() => { if (finished) void completeOnboarding(); }, [finished]);
  return (
    <>
      <Strip g={g} />
      <div className="ob-layer"><Coach g={g} /></div>
    </>
  );
}

/** The first-run guide for the signed-in app: nothing for a user who has finished or hidden it. */
export function OnboardingGuide() {
  const { pathname } = useLocation();
  const p = useProgress(pathname);
  if (!p || p.status.onboarded) return null;
  return <Guide p={p} />;
}
