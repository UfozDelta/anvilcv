import { useState } from 'react';
import { NavStrip } from '../app/AppNav';
import { useStoryFlow } from './model';
import { DescBlock, Head, RepoPane, Undo } from './parts';
import { BulletsTab } from './BulletsTab';
import { GenerateTab } from './GenerateTab';
import '../../styles/landing.css';
import '../../styles/ledger.css';
import './project-detail.css';
import './flow.css';

type T = 'bullets' | 'generate' | 'repo';

export function LabStoryFlow() {
  const sf = useStoryFlow();
  const [tab, setTab] = useState<T>('bullets');
  const [focus, setFocus] = useState<string | null>(null);
  const tabs: { k: T; label: React.ReactNode }[] = [
    { k: 'bullets', label: <>Bullets <span className="tabs__badge">{sf.bullets.length}</span></> },
    { k: 'generate', label: 'Generate' },
    { k: 'repo', label: 'Repo' },
  ];
  const view = (id: string | null) => { setFocus(id); setTab('bullets'); };
  const pickTab = (k: T) => { setFocus(null); setTab(k); };
  // Roving tabindex: ←/→ (and Home/End) move between tabs and select them.
  const onKey = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const i = tabs.findIndex(t => t.k === tab);
    const n = e.key === 'ArrowRight' ? i + 1 : e.key === 'ArrowLeft' ? i - 1 : e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 : null;
    if (n === null) return;
    e.preventDefault();
    const k = tabs[(n + tabs.length) % tabs.length].k;
    pickTab(k);
    document.getElementById(`sf-tab-${k}`)?.focus();
  };
  return (
    <div className="ap-root">
      <NavStrip />
      <div className="shell ap-page sf-page">
        <Head sf={sf} />
        <DescBlock sf={sf} />
        <div className="tabs sf-tabs" role="tablist" aria-label="Project" onKeyDown={onKey}>
          {tabs.map(t => (
            <button key={t.k} type="button" role="tab" id={`sf-tab-${t.k}`} aria-controls={tab === t.k ? `sf-panel-${t.k}` : undefined}
              aria-selected={tab === t.k} tabIndex={tab === t.k ? 0 : -1} className={tab === t.k ? 'is-on' : ''}
              onClick={() => pickTab(t.k)}>{t.label}</button>
          ))}
        </div>
        <div className="tabpane sf-pane" role="tabpanel" id={`sf-panel-${tab}`} aria-labelledby={`sf-tab-${tab}`}>
          {tab === 'bullets' && <BulletsTab sf={sf} focus={focus} onGenerate={() => setTab('generate')} />}
          {tab === 'generate' && <GenerateTab sf={sf} onView={view} />}
          {tab === 'repo' && <RepoPane sf={sf} />}
        </div>
        <Undo sf={sf} />
      </div>
    </div>
  );
}
