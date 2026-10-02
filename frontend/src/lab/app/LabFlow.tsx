import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { FLOW_APPS, FLOW_HISTORY } from './appData';
import { BulletBar, PageTitle, inert, useDemoPage } from './shared';
import { OutcomeSankey, RANK } from '../../components/OutcomeSankey';
import type { OutcomeHistoryEntry } from '../../lib/api';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

const STAGE_LABEL: Record<string, string> = { applied: 'Applied', oa: 'Online assessment', interview: 'Interview', offer: 'Offer', rejected: 'Rejected', ghosted: 'Ghosted' };
/** Two ways it ends badly: they said no, or they said nothing. */
const STAGE_NOTE: Record<string, string> = { rejected: 'They said no', ghosted: 'No reply' };

/** How many distinct applications ever reached each stage. */
function reached(history: OutcomeHistoryEntry[]) {
  const apps = new Set(history.map((h) => h.applicationId));
  const by = new Map<string, Set<string>>();
  for (const h of history) {
    if (!by.has(h.outcome)) by.set(h.outcome, new Set());
    by.get(h.outcome)!.add(h.applicationId);
  }
  return { total: apps.size, count: (s: string) => by.get(s)?.size ?? 0 };
}

/**
 * The Sankey drawn at the container's real width, so it spans the page. Labels are the shared
 * 11px on desktop and 10px on phones, where the 560-wide drawing used to scale down and collide.
 */
function WideSankey({ history, selected, onSelect }: { history: OutcomeHistoryEntry[]; selected: string | null; onSelect: (s: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setW(Math.round(e.contentRect.width)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  const wide = w >= 721;
  return (
    <div ref={ref} className="fl-sankey">
      {w > 0 && <OutcomeSankey history={history} selected={selected} onSelect={onSelect} rich width={w} height={wide ? Math.min(Math.max(Math.round(w * 0.3), 300), 420) : 300} labelSize={wide ? undefined : 10} compact={!wide} />}
      {!wide && w > 0 && (
        <ul className="fl-legend" aria-label="Colour key">
          <li><i data-k="rejected" />Rejected</li>
          <li><i data-k="ghosted" />Ghosted</li>
        </ul>
      )}
    </div>
  );
}

function FlowLedger({ empty }: { empty: boolean }) {
  const history = empty ? [] : FLOW_HISTORY;
  const { total, count } = reached(history);
  // "Heard back" = got any reply at all, even one that later went quiet.
  const heard = new Set(history.filter((h) => h.outcome !== 'applied' && h.outcome !== 'ghosted').map((h) => h.applicationId)).size;
  const [open, setOpen] = useState<string | null>(null);
  const reduced = usePrefersReducedMotion();
  const rowRefs = useRef<Record<string, HTMLLIElement | null>>({});

  const toggle = (stage: string) => setOpen((o) => (o === stage ? null : stage));
  /** From the chart: open the stage and bring its row into view. */
  const fromChart = (stage: string) => {
    setOpen(stage);
    requestAnimationFrame(() => rowRefs.current[stage]?.scrollIntoView({ block: 'center', behavior: reduced ? 'auto' : 'smooth' }));
  };
  const appsAt = (stage: string) => FLOW_APPS.filter((a) => a.path.includes(stage)).sort((a, b) => a.company.localeCompare(b.company));

  return (
    <div className="shell ap-page fl-page">
      <PageTitle
        title="Outcome flow"
        count={total > 0 && <><strong>{total}</strong> applications · <strong>{heard}</strong> heard back ({Math.round((heard / total) * 100)}%) · <strong>{count('interview')}</strong> interviewed · <strong>{count('offer')}</strong> offer{count('offer') === 1 ? '' : 's'}</>}
      />

      {total === 0 ? (
        <section className="ap-empty">
          <h2 className="lp-display ap-empty__title">Nothing to chart yet.</h2>
          <p className="ap-empty__sub">Change an application's status and its path shows up here.</p>
        </section>
      ) : (
        <>
          <section className="pf-sec fl-flow">
            <h2 className="ap-label">Flow</h2>
            <WideSankey history={history} selected={open} onSelect={fromChart} />
          </section>
          <section className="pf-sec">
            <h2 className="ap-label">Stages</h2>
            <ul className="ld-table fl-list" aria-label="Applications that reached each stage">
              {RANK.map((s) => {
                const n = count(s);
                const pct = Math.round((n / total) * 100);
                const isOpen = open === s;
                return (
                  <li key={s} ref={(el) => { rowRefs.current[s] = el; }} className="fl-item" data-stage={s} data-open={isOpen || undefined}>
                    <button type="button" className="ld-row fl-row" aria-expanded={isOpen} aria-controls={`fl-apps-${s}`} onClick={() => toggle(s)}>
                      <span className="ld-name">
                        <strong>{STAGE_LABEL[s] ?? s}</strong>
                        {STAGE_NOTE[s] && <span className="ld-desc">{STAGE_NOTE[s]}</span>}
                      </span>
                      <span className="ld-bullets">
                        <span className="ld-bullets__n">{n}</span>
                        <BulletBar n={n} max={total} label={`${STAGE_LABEL[s] ?? s}: ${n} of ${total}`} />
                      </span>
                      <span className="fl-pct">{pct}%</span>
                      <span className="fl-chev" aria-hidden="true">{isOpen ? '–' : '+'}</span>
                    </button>
                    {isOpen && (
                      <ul id={`fl-apps-${s}`} className="fl-apps" aria-label={`Applications that reached ${STAGE_LABEL[s] ?? s}`}>
                        {appsAt(s).map((a) => (
                          <li key={a.id} className="fl-app">
                            <a href="#" onClick={inert} className="fl-app__co">{a.company}</a>
                            <span className="fl-app__role">{a.role}</span>
                            <span className="fl-app__end" data-end={a.path[a.path.length - 1]}>{STAGE_LABEL[a.path[a.path.length - 1]] ?? a.path[a.path.length - 1]}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}

/** /lab/flow — Outcome flow in the Ledger style: the Sankey across the page, then stage funnel rows. E toggles the empty state. */
export function LabFlow() {
  const { empty, mountKey, replay } = useDemoPage();
  return (
    <div className="ap-root">
      <NavStrip />
      <FlowLedger key={`${mountKey}-${empty}`} empty={empty} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
