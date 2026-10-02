import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { HeroAssembly } from '../../components/landing/HeroAssembly';
import { HeroRerank } from './HeroRerank';
import { HeroBroadsheet } from './HeroBroadsheet';
import '../../styles/landing.css';
import '../../components/landing/hero.css';
import '../picker.css';

const VARIANTS = [
  { name: 'Assembly', C: HeroAssembly },
  { name: 'Rerank', C: HeroRerank },
  { name: 'Broadsheet', C: HeroBroadsheet },
];

/** Landing hero prototypes behind the prototype-skill picker. Placeholder data, no API. */
export function LabHero() {
  const [params, setParams] = useSearchParams();
  const initial = Math.min(Math.max((parseInt(params.get('v') ?? '1', 10) || 1) - 1, 0), VARIANTS.length - 1);
  const [current, setCurrent] = useState(initial);
  const [mountKey, setMountKey] = useState(0);

  const setActive = useCallback((i: number) => {
    if (i < 0 || i >= VARIANTS.length) return;
    setCurrent(i);
    setMountKey((k) => k + 1);
    setParams({ v: String(i + 1) }, { replace: true });
  }, [setParams]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const num = parseInt(e.key, 10);
      if (num >= 1 && num <= VARIANTS.length) setActive(num - 1);
      else if (e.key === 'ArrowRight') setActive((current + 1) % VARIANTS.length);
      else if (e.key === 'ArrowLeft') setActive((current - 1 + VARIANTS.length) % VARIANTS.length);
      else if (e.key === 'r' || e.key === 'R') setMountKey((k) => k + 1);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [current, setActive]);

  const { C } = VARIANTS[current];
  return (
    <div className="lp-root">
      <C key={mountKey} />
      <section className="shell hx-next">
        <div className="lp-section-mark">
          <span className="lp-section-title">SAMPLE OUTPUT</span>
          <div className="lp-section-rule" />
          <span className="lp-section-title lp-muted">ONE JD → ONE PDF</span>
        </div>
      </section>
      <Picker names={VARIANTS.map((v) => v.name)} current={current} onPick={setActive} onReplay={() => setMountKey((k) => k + 1)} />
    </div>
  );
}

/** The prototype-skill picker (verbatim spec). Exported so other /lab harnesses reuse it. */
export function Picker({ names, current, onPick, onReplay, position }: { names: string[]; current: number; onPick: (i: number) => void; onReplay: () => void; position?: 'top' }) {
  const navRef = useRef<HTMLElement>(null);
  const hlRef = useRef<HTMLSpanElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useLayoutEffect(() => {
    const move = () => {
      const el = itemRefs.current[current];
      if (!el || !hlRef.current) return;
      hlRef.current.style.width = el.offsetWidth + 'px';
      hlRef.current.style.transform = `translateX(${el.offsetLeft}px)`;
    };
    move();
    window.addEventListener('resize', move);
    return () => window.removeEventListener('resize', move);
  }, [current]);

  useEffect(() => {
    // Enable the slide only after first paint, so load doesn't animate.
    const id = requestAnimationFrame(() => requestAnimationFrame(() => navRef.current?.setAttribute('data-ready', '')));
    return () => cancelAnimationFrame(id);
  }, []);

  return (
    <nav className="proto-picker" aria-label="Prototype variants" ref={navRef} data-position={position}>
      <span className="proto-picker-highlight" aria-hidden="true" ref={hlRef} />
      {names.map((name, i) => (
        <button
          key={name}
          ref={(el) => { itemRefs.current[i] = el; }}
          className="proto-picker-item"
          data-active={i === current || undefined}
          aria-current={i === current ? 'true' : undefined}
          onClick={() => onPick(i)}
        >
          {name}
        </button>
      ))}
      <span className="proto-picker-divider" aria-hidden="true" />
      <button className="proto-picker-item proto-picker-replay" aria-label="Replay animation (R)" onClick={onReplay}>↻</button>
    </nav>
  );
}
