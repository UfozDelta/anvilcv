import { useEffect, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { useDemoPage } from '../app/shared';
import { PROJECT } from '../stories/storyFixtures';
import { Meter, useBank, type Bank, type Scenario } from './model';
import { BoardView, CardsView, RowsView, SplitView } from './Variants';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import './lean.css';

const NAMES = ['Rows', 'Cards', 'Split', 'Board'];
const VIEWS = [RowsView, CardsView, SplitView, BoardView];
const SCENARIOS: Scenario[] = ['healthy', 'thin', 'full'];

export function LabStoriesLean() {
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
  const b = useBank();
  const View = VIEWS[v];
  return (
    <div className="shell ap-page sl-page">
      <Head b={b} />
      <View b={b} />
    </div>
  );
}

function Head({ b }: { b: Bank }) {
  const { run } = b;
  return (
    <header className="sl-head">
      <div className="sl-head__id">
        <h1 className="lp-display sl-head__name">{PROJECT.name}</h1>
        <Meter used={b.live.length} usable={b.usable} />
      </div>
      <div className="sl-head__tools">
        <button type="button" className="sl-switch" aria-pressed={b.lensesOn} onClick={() => b.setLensesOn(x => !x)}
          title={b.lensesOn ? 'One wording per lens' : 'General wordings'}>
          <i aria-hidden="true" />Lenses
        </button>
        <div className="filterset sl-demo" title="Demo bank">
          {SCENARIOS.map(s => (
            <button key={s} className={b.scenario === s ? 'is-on' : ''} onClick={() => b.setScenario(s)}>{s}</button>
          ))}
        </div>
        <div className="sl-gen">
          <button type="button" className="ap-btn ap-btn--acid sl-gen__btn" onClick={b.generate} disabled={b.busy}
            data-full={b.live.length >= 12 || undefined} title={b.live.length >= 12 ? 'Bank full: no run' : 'Find new stories'}>
            {b.busy ? <span className="sl-spin" aria-label="Generating" /> : '✦'} Generate
          </button>
          <span className="sl-run" aria-live="polite">
            {run.full ? (
              <span className="sl-run__c" data-bad title="Bank full: nothing generated">Full</span>
            ) : (
              <>
                <span className="sl-run__c" data-good title={`${run.added} new stories`}>+{run.added}</span>
                <span className="sl-run__c" title={`${run.dropped} dropped: repeats or no evidence`}>−{run.dropped}</span>
              </>
            )}
            <span className="sl-run__when" title="Last run">{run.when}</span>
          </span>
        </div>
      </div>
    </header>
  );
}
