import { useEffect, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { useDemoPage } from '../app/shared';
import { DescriptionPane, Head, RepoPane, RunChips, Spin } from '../workspace/parts';
import { useWorkspace } from './model';
import { TERMS } from './data';
import { Undo, WideBullets } from './bullets';
import { GenPick } from './GenPick';
import { GenGuided } from './GenGuided';
import { GenGaps } from './GenGaps';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import '../project-split/split.css';
import '../workspace/workspace.css';
import './tabs.css';

const NAMES = ['Pick', 'Guided', 'Gaps'];

export function LabWorkspaceTabs() {
  const { mountKey, replay } = useDemoPage(false);
  const [v, setV] = useState(0);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || e.metaKey || e.ctrlKey || e.altKey) return;
      const n = Number(e.key);
      if (n >= 1 && n <= NAMES.length) setV(n - 1);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="ap-root">
      <NavStrip />
      <Page key={`${mountKey}-${v}`} v={v} />
      <Picker names={NAMES} current={v} onPick={setV} onReplay={replay} />
    </div>
  );
}

type T = 'bullets' | 'generate' | 'description' | 'repo';

function Page({ v }: { v: number }) {
  const ws = useWorkspace();
  const term = TERMS[v];
  const [tab, setTab] = useState<T>('generate');
  const fresh = ws.newIds.size;
  const tabs: { k: T; label: React.ReactNode }[] = [
    { k: 'bullets', label: <>Bullets <span className="tabs__badge">{ws.bullets.length}</span>{fresh > 0 && tab !== 'bullets' && <span className="ws-dot" title={`${fresh} new`} />}</> },
    { k: 'generate', label: <>{ws.busy ? <Spin /> : '✦'} Generate</> },
    { k: 'description', label: 'Description' },
    { k: 'repo', label: 'Repo' },
  ];
  const toBullets = () => setTab('bullets');
  return (
    <div className="shell ap-page ps-page ws-page wt-page">
      <Head ws={ws}><RunChips run={ws.run} /></Head>
      <div className="tabs ps-tabs" role="tablist">
        {tabs.map(t => (
          <button key={t.k} role="tab" aria-selected={tab === t.k} className={tab === t.k ? 'is-on' : ''} onClick={() => setTab(t.k)}>{t.label}</button>
        ))}
      </div>
      <div className="tabpane wt-pane">
        {tab === 'bullets' && <WideBullets ws={ws} term={term} />}
        {tab === 'generate' && v === 0 && <GenPick ws={ws} term={term} toBullets={toBullets} />}
        {tab === 'generate' && v === 1 && <GenGuided ws={ws} term={term} toBullets={toBullets} />}
        {tab === 'generate' && v === 2 && <GenGaps ws={ws} term={term} toBullets={toBullets} />}
        {tab === 'description' && <div className="ps-narrow"><DescriptionPane ws={ws} /></div>}
        {tab === 'repo' && <RepoPane ws={ws} />}
      </div>
      <Undo ws={ws} />
    </div>
  );
}
