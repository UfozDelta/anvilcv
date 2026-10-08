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
  return (
    <div className="ap-root">
      <NavStrip />
      <div className="shell ap-page sf-page">
        <Head sf={sf} />
        <DescBlock sf={sf} />
        <div className="tabs sf-tabs" role="tablist">
          {tabs.map(t => (
            <button key={t.k} role="tab" aria-selected={tab === t.k} className={tab === t.k ? 'is-on' : ''}
              onClick={() => { setFocus(null); setTab(t.k); }}>{t.label}</button>
          ))}
        </div>
        <div className="tabpane sf-pane">
          {tab === 'bullets' && <BulletsTab sf={sf} focus={focus} onGenerate={() => setTab('generate')} />}
          {tab === 'generate' && <GenerateTab sf={sf} onView={view} />}
          {tab === 'repo' && <RepoPane sf={sf} />}
        </div>
        <Undo sf={sf} />
      </div>
    </div>
  );
}
