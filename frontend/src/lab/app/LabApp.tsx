import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Picker } from '../hero/LabHero';
import { NavDock, NavStrip, NavZones } from './AppNav';
import { HomeLedger } from './HomeLedger';
import { HomeBench } from './HomeBench';
import { HomeForge } from './HomeForge';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

type Variant = { name: string; C: React.ComponentType<{ empty: boolean }> };

const HOMES: Variant[] = [
  { name: 'Ledger', C: HomeLedger },
  { name: 'Bench', C: HomeBench },
  { name: 'Forge', C: HomeForge },
];
const NAVS = [
  { name: 'Strip', C: NavStrip },
  { name: 'Two-zone', C: NavZones },
  { name: 'Dock', C: NavDock },
];

/**
 * Shared picker harness. 1–3 / ←→ switch, R replays, E toggles the empty (first-run)
 * state. `?empty=1` persists it.
 */
function useHarness(count: number) {
  const [params, setParams] = useSearchParams();
  const v = Math.min(Math.max((parseInt(params.get('v') ?? '1', 10) || 1) - 1, 0), count - 1);
  const empty = params.get('empty') === '1';
  const [mountKey, setMountKey] = useState(0);
  const set = useCallback((next: { v?: number; empty?: boolean }) => {
    const nv = next.v ?? v;
    const ne = next.empty ?? empty;
    setParams(ne ? { v: String(nv + 1), empty: '1' } : { v: String(nv + 1) }, { replace: true });
    setMountKey((k) => k + 1);
  }, [v, empty, setParams]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable || t.getAttribute('role') === 'tab') return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const num = parseInt(e.key, 10);
      if (num >= 1 && num <= count) set({ v: num - 1 });
      else if (e.key === 'ArrowRight') set({ v: (v + 1) % count });
      else if (e.key === 'ArrowLeft') set({ v: (v - 1 + count) % count });
      else if (e.key === 'r' || e.key === 'R') setMountKey((k) => k + 1);
      else if (e.key === 'e' || e.key === 'E') set({ empty: !empty });
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [v, empty, count, set]);

  return { v, empty, mountKey, set, replay: () => setMountKey((k) => k + 1) };
}

function useMedia(q: string) {
  const [m, setM] = useState(() => window.matchMedia(q).matches);
  useEffect(() => {
    const mq = window.matchMedia(q);
    const on = () => setM(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, [q]);
  return m;
}

/** /lab/app — post-login home (Projects) directions, under the Strip nav. */
export function LabApp() {
  const h = useHarness(HOMES.length);
  const { C } = HOMES[h.v];
  return (
    <div className="ap-root">
      <NavStrip />
      <C key={h.mountKey} empty={h.empty} />
      <Picker names={HOMES.map((x) => x.name)} current={h.v} onPick={(i) => h.set({ v: i })} onReplay={h.replay} />
    </div>
  );
}

/** /lab/nav — app header/nav directions, over the Ledger home for context. */
export function LabNav() {
  const h = useHarness(NAVS.length);
  const { C } = NAVS[h.v];
  const dock = NAVS[h.v].name === 'Dock';
  const phone = useMedia('(max-width: 1040px)');
  return (
    <div className={`ap-root${dock ? ' ap-root--dock' : ''}`}>
      <C key={h.mountKey} />
      <HomeLedger empty={h.empty} />
      {/* Dock owns the bottom edge on phones, so the picker moves up (picker spec). */}
      <Picker names={NAVS.map((x) => x.name)} current={h.v} onPick={(i) => h.set({ v: i })} onReplay={h.replay} position={dock && phone ? 'top' : undefined} />
    </div>
  );
}
