import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { usePrefersReducedMotion } from '../landing/useHeroLoop';
import { useField } from '../../lib/field';
import { findTarget, textFor, useGuide } from './guide';
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
type Pos = { x: number; y: number };

const TIP_W = 300;
const overlap = (a: { l: number; t: number; r: number; b: number }, b: { left: number; top: number; right: number; bottom: number }) =>
  Math.max(0, Math.min(a.r, b.right) - Math.max(a.l, b.left)) * Math.max(0, Math.min(a.b, b.bottom) - Math.max(a.t, b.top));

/** How many sample points inside the rectangle land on something readable or clickable: text, a control, an image. */
function textHits(r: { l: number; t: number; r: number; b: number }, tip: HTMLElement | null): number {
  let n = 0;
  for (let i = 0; i < 6; i++) {
    for (let j = 0; j < 4; j++) {
      const x = r.l + ((i + 0.5) / 6) * (r.r - r.l), y = r.t + ((j + 0.5) / 4) * (r.b - r.t);
      if (x < 0 || y < 0 || x >= window.innerWidth || y >= window.innerHeight) continue;
      const el = document.elementsFromPoint(x, y).find((e) => !e.closest('.ob-layer') && e !== tip && !tip?.contains(e));
      if (!el || el === document.body || el === document.documentElement) continue;
      const readable = [...el.childNodes].some((c) => c.nodeType === Node.TEXT_NODE && (c.textContent ?? '').trim() !== '')
        || el.matches('button, a, input, textarea, select, img, svg, canvas, iframe');
      if (readable) n++;
    }
  }
  return n;
}

/**
 * Where the tip sits: of the spots around the target and the screen corners, the one that covers the
 * fewest buttons and fields, never the target itself, and stays on screen. Nothing the user must read or press is hidden.
 */
function placeTip(box: Box, h: number, target: HTMLElement | null, tip: HTMLElement | null): Pos {
  const vw = window.innerWidth, vh = window.innerHeight, w = Math.min(TIP_W, vw - 16);
  const cx = (x: number) => Math.max(8, Math.min(x, vw - w - 8));
  const right = cx(box.x + box.w - w);
  const spots: Pos[] = [
    { x: cx(box.x), y: box.y + box.h + 12 }, { x: right, y: box.y + box.h + 12 },
    { x: cx(box.x), y: box.y - h - 12 }, { x: right, y: box.y - h - 12 },
    { x: box.x + box.w + 12, y: box.y }, { x: box.x - w - 12, y: box.y },
    { x: vw - w - 20, y: vh - h - 20 }, { x: 20, y: vh - h - 20 }, { x: vw - w - 20, y: 64 }, { x: 20, y: 64 },
  ];
  const controls = [...document.querySelectorAll<HTMLElement>('button, a[href], input, textarea, select')]
    .filter((e) => e !== target && !target?.contains(e) && !tip?.contains(e) && !e.closest('.ob-strip'))
    .map((e) => e.getBoundingClientRect())
    .filter((r) => r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < vh);
  const targetRect = { left: box.x, top: box.y, right: box.x + box.w, bottom: box.y + box.h };
  let best = spots[0], low = Infinity;
  for (const p of spots) {
    const r = { l: p.x, t: p.y, r: p.x + w, b: p.y + h };
    const onScreen = overlap(r, { left: 0, top: 56, right: vw, bottom: vh });
    const score = (w * h - onScreen) * 4 + overlap(r, targetRect) * 20 + controls.reduce((n, k) => n + overlap(r, k), 0)
      + textHits(r, tip) * 900;
    if (score < low) { low = score; best = p; }
  }
  return best;
}

/**
 * One spotlight on the real control for the current step. It goes away the moment the user touches
 * that control (click, focus, typing) and comes back with the next step; the bar above keeps saying what is next.
 */
function Coach({ g }: { g: ReturnType<typeof useGuide> }) {
  const reduce = usePrefersReducedMotion();
  const [box, setBox] = useState<Box | null>(null);
  // Which control was touched, so a later tip on the same page (a new target) shows again.
  const [seen, setSeen] = useState<{ key: string; el: HTMLElement | null } | null>(null);
  const raf = useRef(0);
  const target = useRef<HTMLElement | null>(null);
  const tipEl = useRef<HTMLDivElement>(null);
  const [th, setTh] = useState(150);
  const [pos, setPos] = useState<Pos | null>(null);
  const id = g.current?.id;
  const key = `${id}@${g.pathname}`;
  const keyRef = useRef(key);
  keyRef.current = key;

  useLayoutEffect(() => {
    if (!id) { setBox(null); return; }
    // The welcome page has no app shell, so it is watched whole.
    const main = document.querySelector('main.ap-main') ?? document.body;
    const measure = () => {
      const { el, onPage } = findTarget(id, g.pathname);
      target.current = el;
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

  // Touching the control the tip points at is the acknowledgement.
  useEffect(() => {
    const touched = (e: Event) => { if (target.current?.contains(e.target as Node)) setSeen({ key: keyRef.current, el: target.current }); };
    const evs = ['pointerdown', 'focusin', 'keydown'] as const;
    evs.forEach((n) => document.addEventListener(n, touched, true));
    return () => evs.forEach((n) => document.removeEventListener(n, touched, true));
  }, []);

  const c = g.current;
  const hidden = seen?.key === key && seen.el === target.current;
  const text = c ? textFor(c, g.pathname) : null;
  useLayoutEffect(() => { if (tipEl.current) setTh(tipEl.current.offsetHeight); }, [id, box?.onPage, hidden, text?.body]);
  useLayoutEffect(() => { setPos(box && !hidden ? placeTip(box, th, target.current, tipEl.current) : null); }, [box, th, hidden]);
  const go = !(box?.onPage ?? g.here);
  const acts = (
    <span className="ob-tip__acts">
      {go && <button type="button" className="ob-tip__go" onClick={g.go}>Take me there</button>}
      <button type="button" className="ob-skip" onClick={() => setSeen({ key, el: target.current })}>Got it</button>
    </span>
  );
  const body = c && text && (
    <>
      <span className="ap-label">{g.done + 1} of {g.steps.length}</span>
      <strong>{text.title}</strong>
      <span>{text.body}</span>
      {acts}
    </>
  );
  return (
    <>
      <AnimatePresence>
        {c && box && !hidden && (
          <>
            <motion.div key="ring" className="ob-ring" aria-hidden
              initial={{ opacity: 0, x: box.x, y: box.y, width: box.w, height: box.h }}
              animate={{ opacity: 1, x: box.x, y: box.y, width: box.w, height: box.h }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }} />
            {pos && <motion.div key="tip" ref={tipEl} className="ob-tip" role="status"
              initial={{ opacity: 0, x: pos.x, y: pos.y + 6 }}
              animate={{ opacity: 1, x: pos.x, y: pos.y }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }}>
              {body}
            </motion.div>}
          </>
        )}
      </AnimatePresence>
      {c && !box && (
        <div className="ob-corner" role="status" ref={tipEl}>{body}</div>
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
