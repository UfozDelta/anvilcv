import { useCallback, useEffect, useRef, useState } from 'react';

export function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const on = () => setReduced(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  return reduced;
}

/**
 * Steps through `durations` (ms per step) and loops — or, with `once`, parks on the
 * last step until `replay()`. Pauses while the element is off-screen or the tab is
 * hidden. With reduced motion it parks on the last step, so the demo shows its
 * finished state instead of moving.
 */
export function useHeroLoop<T extends Element>(durations: number[], once = false) {
  const ref = useRef<T>(null);
  const reduced = usePrefersReducedMotion();
  const last = durations.length - 1;
  const [step, setStep] = useState(0);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => setVisible(e.isIntersecting), { threshold: 0.2 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (reduced || !visible || (once && step >= last)) return;
    const t = window.setTimeout(() => {
      if (document.hidden) return;
      setStep((s) => (s >= last ? 0 : s + 1));
    }, durations[step]);
    return () => window.clearTimeout(t);
  }, [step, visible, reduced, durations, last, once]);

  const replay = useCallback(() => setStep(0), []);
  return { ref, step: reduced ? last : step, reduced, replay };
}

/** Counts 0 → target over `ms` with an ease-out, once `run` turns true. */
export function useCountUp(target: number, run: boolean, ms = 700, decimals = 0) {
  const [val, setVal] = useState(run ? target : 0);
  useEffect(() => {
    if (!run) { setVal(0); return; }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / ms);
      setVal(target * (1 - Math.pow(1 - p, 3)));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, run, ms]);
  return val.toFixed(decimals);
}
