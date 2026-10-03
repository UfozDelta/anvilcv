import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type ApplicationSummary, type OutcomeHistoryEntry } from '../lib/api';
import { BulletBar, EmptyState, PageTitle } from '../components/ledger/shared';
import { usePrefersReducedMotion } from '../components/landing/useHeroLoop';
import { OutcomeSankey, RANK, normalizePath } from '../components/OutcomeSankey';

const STAGE_LABEL: Record<string, string> = { applied: 'Applied', oa: 'Online assessment', interview: 'Interview', offer: 'Offer', rejected: 'Rejected', ghosted: 'Ghosted' };
/** Two ways it ends badly: they said no, or they said nothing. */
const STAGE_NOTE: Record<string, string> = { rejected: 'They said no', ghosted: 'No reply' };
const label = (s: string) => STAGE_LABEL[s] ?? s;

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

/** Per application: the outcomes it went through, oldest first, corrections undone, so the last one is where it stands. */
function paths(history: OutcomeHistoryEntry[]) {
  const by = new Map<string, OutcomeHistoryEntry[]>();
  for (const h of history) {
    if (!by.has(h.applicationId)) by.set(h.applicationId, []);
    by.get(h.applicationId)!.push(h);
  }
  const out = new Map<string, string[]>();
  for (const [id, rows] of by) out.set(id, normalizePath([...rows].sort((a, b) => a.changedAt.localeCompare(b.changedAt)).map((r) => r.outcome)));
  return out;
}

function FlowLedger({ history, apps }: { history: OutcomeHistoryEntry[]; apps: ApplicationSummary[] }) {
  const byApp = useMemo(() => paths(history), [history]);
  const info = useMemo(() => new Map(apps.map((a) => [a.id, a])), [apps]);
  const total = byApp.size;
  const count = (s: string) => [...byApp.values()].filter((p) => p.includes(s)).length;
  // "Heard back" = got any reply at all, even one that later went quiet.
  const heard = [...byApp.values()].filter((p) => p.some((o) => o !== 'applied' && o !== 'ghosted')).length;
  const [open, setOpen] = useState<string | null>(null);
  const reduced = usePrefersReducedMotion();
  const rowRefs = useRef<Record<string, HTMLLIElement | null>>({});

  const toggle = (stage: string) => setOpen((o) => (o === stage ? null : stage));
  /** From the chart: open the stage and bring its row into view. */
  const fromChart = (stage: string) => {
    setOpen(stage);
    requestAnimationFrame(() => rowRefs.current[stage]?.scrollIntoView({ block: 'center', behavior: reduced ? 'auto' : 'smooth' }));
  };
  const appsAt = (stage: string) =>
    [...byApp.entries()]
      .filter(([, p]) => p.includes(stage))
      .map(([id, p]) => ({ id, company: info.get(id)?.company || 'Untitled', role: info.get(id)?.role ?? '', end: p[p.length - 1] }))
      .sort((a, b) => a.company.localeCompare(b.company));

  return (
    <div className="shell ap-page fl-page">
      <PageTitle
        title="Outcome flow"
        count={total > 0 && <><strong>{total}</strong> applications · <strong>{heard}</strong> heard back ({Math.round((heard / total) * 100)}%) · <strong>{count('interview')}</strong> interviewed · <strong>{count('offer')}</strong> offer{count('offer') === 1 ? '' : 's'}</>}
      />

      {total === 0 ? (
        <EmptyState title="Nothing to chart yet." sub="Change an application's status and its path shows up here." />
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
                        <strong>{label(s)}</strong>
                        {STAGE_NOTE[s] && <span className="ld-desc">{STAGE_NOTE[s]}</span>}
                      </span>
                      <span className="ld-bullets">
                        <span className="ld-bullets__n">{n}</span>
                        <BulletBar n={n} max={total} label={`${label(s)}: ${n} of ${total}`} />
                      </span>
                      <span className="fl-pct">{pct}%</span>
                      <span className="fl-chev" aria-hidden="true">{isOpen ? '–' : '+'}</span>
                    </button>
                    {isOpen && (
                      <ul id={`fl-apps-${s}`} className="fl-apps" aria-label={`Applications that reached ${label(s)}`}>
                        {appsAt(s).map((a) => (
                          <li key={a.id} className="fl-app">
                            <Link to={`/applications/${a.id}`} className="fl-app__co">{a.company}</Link>
                            <span className="fl-app__role">{a.role}</span>
                            <span className="fl-app__end" data-end={a.end}>{label(a.end)}</span>
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

export function OutcomeFlow() {
  const [history, setHistory] = useState<OutcomeHistoryEntry[]>([]);
  const [apps, setApps] = useState<ApplicationSummary[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // The history alone says nothing about which application is which; the list names them.
    // If the list fails the chart still works, the stage rows just fall back to "Untitled".
    Promise.all([
      api.get<OutcomeHistoryEntry[]>('/api/applications/outcome-history'),
      api.get<ApplicationSummary[]>('/api/applications').catch(() => [] as ApplicationSummary[]),
    ])
      .then(([h, a]) => { setHistory(h); setApps(a); })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="shell ap-page fl-page"><span className="spinner">LOADING</span></div>;
  if (error) return <div className="shell ap-page fl-page"><div className="err">Could not load outcome history — {error}</div></div>;
  return <FlowLedger history={history} apps={apps} />;
}
