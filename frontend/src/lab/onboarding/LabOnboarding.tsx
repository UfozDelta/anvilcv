import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DemoApp } from './DemoApp';
import { AutoDrive, Coach, Strip } from './Guides';
import { START, WelcomePage, type Path } from './Welcome';
import { installFakeApi, resetFake, setGuidePath, type Field } from './fakeApi';
import '../../styles/landing.css';
import './onboarding.css';

/** Two-step welcome, then the real app with the next-step bar and the dimmed coachmark. */
function Flow({ auto, field, path: autoPath }: { auto: boolean; field: Field; path: Path }) {
  const [path, setPath] = useState<Path | null>(null);
  if (!path) return <WelcomePage auto={auto} field={field} path={autoPath} onDone={(p) => { setGuidePath(p); setPath(p); }} />;
  return (
    <DemoApp start={START[path]} banner={<Strip />}>
      <Coach />
      {auto && <AutoDrive />}
    </DemoApp>
  );
}

/** Patches fetch with the fake backend before the real pages mount; restores it on the way out. */
function Gate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const restore = installFakeApi();
    resetFake();
    setReady(true);
    return restore;
  }, []);
  return ready ? <>{children}</> : null;
}

/** First-run prototype: a welcome, then the REAL pages guided through the first résumé. R replays, A autoplays. */
export function LabOnboarding() {
  const [params, setParams] = useSearchParams();
  const [mountKey, setMountKey] = useState(0);
  const [auto, setAuto] = useState(params.get('auto') === '1');
  const [field, setFieldSel] = useState<Field>(params.get('field') === 'mechanical' ? 'mechanical' : 'software');
  const [road, setRoad] = useState<Path>((['manual', 'skip'] as const).find((r) => r === params.get('road')) ?? 'import');

  const replay = useCallback(() => setMountKey((k) => k + 1), []);
  const toggleAuto = useCallback(() => {
    setAuto((a) => {
      setParams((p) => { if (a) p.delete('auto'); else p.set('auto', '1'); return p; }, { replace: true });
      return !a;
    });
    setMountKey((k) => k + 1);
  }, [setParams]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === 'r' || e.key === 'R') replay();
      else if (e.key === 'a' || e.key === 'A') toggleAuto();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [replay, toggleAuto]);

  return (
    <>
      <Gate key={mountKey}><Flow auto={auto} field={field} path={road} /></Gate>
      <div className="ob-lab">
        <select aria-label="Autoplay road" value={road} onChange={(e) => { setRoad(e.target.value as Path); setMountKey((k) => k + 1); }}>
          <option value="import">Autoplay: import</option>
          <option value="manual">Autoplay: by hand</option>
          <option value="skip">Autoplay: skip</option>
        </select>
        <select aria-label="Autoplay field" value={field} onChange={(e) => { setFieldSel(e.target.value as Field); setMountKey((k) => k + 1); }}>
          <option value="software">Software</option>
          <option value="mechanical">Mechanical</option>
        </select>
        <button type="button" onClick={replay}>↻ Replay (R)</button>
        <button type="button" aria-pressed={auto} onClick={toggleAuto}>{auto ? '■ Stop autoplay' : '▶ Autoplay (A)'}</button>
      </div>
    </>
  );
}
