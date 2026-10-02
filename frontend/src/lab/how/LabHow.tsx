import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Picker } from '../hero/LabHero';
import { SectionMark, TopNav } from '../../components/landing/SiteNav';
import { HowItWorks } from '../../components/landing/HowItWorks';
import { HowCaption } from './HowCaption';
import { HowTour } from './HowTour';
import '../../styles/landing.css';
import '../../components/landing/hero.css';
import '../picker.css';
import '../../components/landing/landing-page.css';
import './how.css';

const VARIANTS = [
  { name: 'Spotlight', C: HowItWorks },
  { name: 'Caption', C: HowCaption },
  { name: 'Tour', C: HowTour },
];

/** "How it works" prototypes, judged in place between real landing sections. */
export function LabHow() {
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
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable || t.getAttribute('role') === 'tab') return;
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
    <div className="lp-root lx">
      <TopNav />
      <section className="shell lx-section">
        <p className="lp-label lp-muted">↑ HERO ABOVE · PROTOTYPE: HOW IT WORKS ({VARIANTS[current].name.toUpperCase()})</p>
      </section>
      <section className="shell lx-section">
        <SectionMark title="How it works" aside="4 STEPS" />
        <C key={mountKey} />
      </section>
      <section className="shell lx-section" style={{ paddingBottom: 200 }}>
        <SectionMark title="One bank, many jobs" />
        <p className="lx-body">Next section continues here…</p>
      </section>
      <Picker names={VARIANTS.map((v) => v.name)} current={current} onPick={setActive} onReplay={() => setMountKey((k) => k + 1)} />
    </div>
  );
}
