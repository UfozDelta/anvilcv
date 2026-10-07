import { useEffect, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { useDemoPage } from '../app/shared';
import { useSplitBank, type Bank } from './model';
import { BulletsPane, Detail, GenButton, Head, PaneSwitch, Sheet, SourceBody, Tabs, type Tab } from './parts';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import './split.css';

const NAMES = ['Workspace', 'Inspector', 'In / Out'];
const ALL: Tab[] = ['bullets', 'description', 'context', 'repo'];
const SOURCES: Tab[] = ['description', 'context', 'repo'];

export function LabProjectSplit() {
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
      <Page key={mountKey} v={v} />
      <Picker names={NAMES} current={v} onPick={setV} onReplay={replay} />
    </div>
  );
}

function Page({ v }: { v: number }) {
  const b = useSplitBank();
  return (
    <div className="shell ap-page ps-page">
      {v === 0 && <Workspace b={b} />}
      {v === 1 && <Inspector b={b} />}
      {v === 2 && <InOut b={b} />}
    </div>
  );
}

/** 1 · Tabs left, the printed page right; the selected bullet is lit on the page. */
function Workspace({ b }: { b: Bank }) {
  const [tab, setTab] = useState<Tab>('bullets');
  const [pane, setPane] = useState(0);
  return (
    <>
      <Head b={b}><GenButton b={b} /></Head>
      <PaneSwitch names={['Edit', 'Page']} on={pane} setOn={setPane} />
      <div className="ps-split ps-split--work">
        <div className="ps-pane" data-on={pane === 0 || undefined}>
          <Tabs tabs={ALL} tab={tab} setTab={setTab} b={b} />
          <div className="tabpane">{tab === 'bullets' ? <BulletsPane b={b} /> : <SourceBody tab={tab} b={b} />}</div>
        </div>
        <aside className="ps-pane ps-side" data-on={pane === 1 || undefined}>
          <Sheet b={b} />
        </aside>
      </div>
    </>
  );
}

/** 2 · Lens list left, the selected bullet's full detail right. */
function Inspector({ b }: { b: Bank }) {
  const [tab, setTab] = useState<Tab>('bullets');
  const [pane, setPane] = useState(0);
  return (
    <>
      <Head b={b}><GenButton b={b} /></Head>
      <Tabs tabs={ALL} tab={tab} setTab={setTab} b={b} />
      {tab === 'bullets' ? (
        <div className="ps-split ps-split--md tabpane">
          <div className="ps-pane ps-master" data-on={pane === 0 || undefined}>
            <BulletsPane b={b} compact onPick={() => setPane(1)} />
          </div>
          <aside className="ps-pane ps-side ps-detail-pane" data-on={pane === 1 || undefined}>
            <Detail b={b} onBack={() => setPane(0)} />
          </aside>
        </div>
      ) : (
        <div className="tabpane ps-narrow"><SourceBody tab={tab} b={b} /></div>
      )}
    </>
  );
}

/** 3 · What went in (sources) left, what came out (bullets) right, Generate between. */
function InOut({ b }: { b: Bank }) {
  const [tab, setTab] = useState<Tab>('context');
  const [pane, setPane] = useState(0);
  return (
    <>
      <Head b={b} />
      <PaneSwitch names={['Source', 'Bullets']} on={pane} setOn={setPane} />
      <div className="ps-split ps-split--io">
        <div className="ps-pane" data-on={pane === 0 || undefined}>
          <Tabs tabs={SOURCES} tab={tab} setTab={setTab} b={b} />
          <div className="tabpane"><SourceBody tab={tab} b={b} /></div>
        </div>
        <div className="ps-mid"><GenButton b={b} vertical /></div>
        <div className="ps-pane" data-on={pane === 1 || undefined}>
          <div className="tabs ps-tabs"><span className="ps-tabhead">Bullets <span className="tabs__badge">{b.bullets.length}</span></span></div>
          <div className="tabpane"><BulletsPane b={b} /></div>
        </div>
      </div>
    </>
  );
}
