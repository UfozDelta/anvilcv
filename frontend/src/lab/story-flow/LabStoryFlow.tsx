import { useEffect, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { useDemoPage } from '../app/shared';
import { useStoryFlow } from './model';
import { DescriptionPane, Head, RepoPane, Undo } from './parts';
import { TakeCards } from './TakeCards';
import { TakePick } from './TakePick';
import { TakeColumns } from './TakeColumns';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import '../project-split/split.css';
import '../workspace/workspace.css';
import '../workspace-tabs/tabs.css';
import './flow.css';

const NAMES = ['Story cards', 'Pick then build', 'Lens columns'];

export function LabStoryFlow() {
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

type T = 'stories' | 'description' | 'repo';

function Page({ v }: { v: number }) {
  const sf = useStoryFlow();
  const [tab, setTab] = useState<T>('stories');
  const tabs: { k: T; label: React.ReactNode }[] = [
    { k: 'stories', label: <>Stories <span className="tabs__badge">{sf.stories.length}</span></> },
    { k: 'description', label: 'Description' },
    { k: 'repo', label: 'Repo' },
  ];
  const generate = () => { setTab('stories'); sf.generate(); };
  return (
    <div className="shell ap-page ps-page ws-page wt-page sf-page">
      <Head sf={sf} onGenerate={generate} />
      <div className="tabs ps-tabs" role="tablist">
        {tabs.map(t => (
          <button key={t.k} role="tab" aria-selected={tab === t.k} className={tab === t.k ? 'is-on' : ''} onClick={() => setTab(t.k)}>{t.label}</button>
        ))}
      </div>
      <div className="tabpane wt-pane">
        {tab === 'stories' && v === 0 && <TakeCards sf={sf} />}
        {tab === 'stories' && v === 1 && <TakePick sf={sf} />}
        {tab === 'stories' && v === 2 && <TakeColumns sf={sf} />}
        {tab === 'description' && <div className="ps-narrow"><DescriptionPane sf={sf} /></div>}
        {tab === 'repo' && <RepoPane sf={sf} />}
      </div>
      <Undo sf={sf} />
    </div>
  );
}
