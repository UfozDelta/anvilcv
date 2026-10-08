import { useEffect, useState } from 'react';
import { CATEGORIES } from '../../lib/api';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { useDemoPage } from '../app/shared';
import { RichText } from '../../components/RichText';
import { LENSES, SHORT } from './data';
import { storyOf, useWorkspace, wid, type WS } from './model';
import {
  BulletsPane, DescriptionPane, Fit, GenPanel, Head, RepoPane, RunChips, Spin, StatusToggle, StoryMark, Trash, Undo,
} from './parts';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import '../project-split/split.css';
import './workspace.css';

const NAMES = ['Tabs', 'Desk', 'Matrix'];

export function LabWorkspace() {
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
  const ws = useWorkspace();
  return (
    <div className="shell ap-page ps-page ws-page">
      {v === 0 && <TabsView ws={ws} />}
      {v === 1 && <DeskView ws={ws} />}
      {v === 2 && <MatrixView ws={ws} />}
      <Undo ws={ws} />
    </div>
  );
}

type T = 'bullets' | 'generate' | 'description' | 'repo';

function TabBar<K extends string>({ tabs, tab, setTab }: { tabs: { k: K; label: React.ReactNode }[]; tab: K; setTab: (k: K) => void }) {
  return (
    <div className="tabs ps-tabs" role="tablist">
      {tabs.map(t => (
        <button key={t.k} role="tab" aria-selected={tab === t.k} className={tab === t.k ? 'is-on' : ''} onClick={() => setTab(t.k)}>{t.label}</button>
      ))}
    </div>
  );
}

/* ── 1 · Tabs: Bullets · Generate · Description · Repo, full width ── */

function TabsView({ ws }: { ws: WS }) {
  const [tab, setTab] = useState<T>('bullets');
  const fresh = ws.newIds.size;
  return (
    <>
      <Head ws={ws}><RunChips run={ws.run} /></Head>
      <TabBar tab={tab} setTab={setTab} tabs={[
        { k: 'bullets', label: <>Bullets <span className="tabs__badge">{ws.bullets.length}</span>{fresh > 0 && tab !== 'bullets' && <span className="ws-dot" title={`${fresh} new`} />}</> },
        { k: 'generate', label: <>{ws.busy ? <Spin /> : '✦'} Generate</> },
        { k: 'description', label: 'Description' },
        { k: 'repo', label: 'Repo' },
      ]} />
      <div className="tabpane">
        {tab === 'bullets' && <BulletsPane ws={ws} />}
        {tab === 'generate' && <GenPanel ws={ws} />}
        {tab === 'description' && <div className="ps-narrow"><DescriptionPane ws={ws} /></div>}
        {tab === 'repo' && <RepoPane ws={ws} />}
      </div>
    </>
  );
}

/* ── 2 · Desk: bullets as the surface, sources folded left, Generate slides over ── */

function DeskView({ ws }: { ws: WS }) {
  const [src, setSrc] = useState<'description' | 'repo' | null>(null);
  const [drawer, setDrawer] = useState(false);

  useEffect(() => {
    if (!drawer) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setDrawer(false); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [drawer]);

  return (
    <>
      <Head ws={ws}>
        <button className="btn btn--sm btn--acid ps-gen__btn" onClick={() => setDrawer(true)} aria-expanded={drawer}>
          {ws.busy ? <Spin /> : '✦'} Generate
        </button>
        <RunChips run={ws.run} />
      </Head>
      <div className="ws-desk" data-src={src ? '' : undefined}>
        <aside className="ws-rail" aria-label="Sources">
          <div className="ws-rail__tabs" role="tablist">
            {(['description', 'repo'] as const).map(k => (
              <button key={k} role="tab" aria-selected={src === k} className={src === k ? 'is-on' : ''}
                title={k === 'description' ? 'Description' : 'Repo map'} onClick={() => setSrc(s => (s === k ? null : k))}>
                {k === 'description' ? 'Description' : 'Repo'}
              </button>
            ))}
          </div>
          {src && (
            <div className="ws-rail__body">
              {src === 'description' ? <DescriptionPane ws={ws} /> : <RepoPane ws={ws} />}
            </div>
          )}
        </aside>
        <main className="ws-desk__main tabpane"><BulletsPane ws={ws} switchable /></main>
      </div>

      <div className="ws-drawer" data-open={drawer || undefined} aria-hidden={!drawer}>
        <div className="ws-drawer__scrim" onClick={() => setDrawer(false)} />
        <section className="ws-drawer__panel" role="dialog" aria-label="Generate" aria-modal="true">
          <div className="ps-tools">
            <span className="label">✦ Generate</span>
            <button className="minibtn" onClick={() => setDrawer(false)} aria-label="Close" title="Close (Esc)" tabIndex={drawer ? 0 : -1}>✕</button>
          </div>
          {drawer && <GenPanel ws={ws} />}
        </section>
      </div>
    </>
  );
}

/* ── 3 · Matrix: stories × lenses, every cell a wording ── */

function MatrixView({ ws }: { ws: WS }) {
  const [tab, setTab] = useState<'matrix' | 'description' | 'repo'>('matrix');
  const all = LENSES.map(l => l.slug);
  return (
    <>
      <Head ws={ws}>
        <button className="btn btn--sm btn--acid ps-gen__btn" disabled={ws.busy} onClick={() => ws.generate(all)}
          title={ws.room === 0 ? 'Bank full' : `New stories, every lens they fit`}>
          {ws.busy && ws.run?.kind === 'new' ? <Spin /> : '✦'} New stories
        </button>
        <RunChips run={ws.run} />
      </Head>
      <TabBar tab={tab} setTab={setTab} tabs={[
        { k: 'matrix', label: <>Bullets <span className="tabs__badge">{ws.bullets.length}</span></> },
        { k: 'description', label: 'Description' },
        { k: 'repo', label: 'Repo' },
      ]} />
      <div className="tabpane">
        {tab === 'matrix' && (
          <div className="ws-mx-wrap">
            <Matrix ws={ws} />
            <CellEditor ws={ws} />
          </div>
        )}
        {tab === 'description' && <div className="ps-narrow"><DescriptionPane ws={ws} /></div>}
        {tab === 'repo' && <RepoPane ws={ws} />}
      </div>
    </>
  );
}

const LABEL = Object.fromEntries(CATEGORIES.map(c => [c.slug, c.label]));

function Matrix({ ws }: { ws: WS }) {
  return (
    <div className="ws-mx" role="grid" aria-label="Stories by lens">
      <div className="ws-mx__row ws-mx__row--head" role="row">
        <span className="ws-mx__story" role="columnheader">
          <span className="label muted" title="Stories in the bank">{ws.stories.length + ws.loose.length}</span>
        </span>
        {LENSES.map(l => (
          <span key={l.slug} className="ws-mx__col" role="columnheader">
            <span className="ws-mx__lname" title={LABEL[l.slug]}><span className="ws-full">{l.short}</span><span className="ws-abbr">{l.abbr}</span></span>
            <button className="ws-mx__fill" disabled={ws.busy || ws.open[l.slug] === 0}
              title={ws.open[l.slug] ? `Write ${ws.open[l.slug]} ${l.short}` : `No ${l.short} gaps`} onClick={() => ws.fill({ lenses: [l.slug] })}>
              +{ws.open[l.slug] || ''}
            </button>
          </span>
        ))}
      </div>

      {ws.stories.map(s => {
        const gaps = ws.missing(s.id).length;
        return (
          <div key={s.id} className="ws-mx__row" role="row" data-sib={ws.hoverStory === s.id || undefined}>
            <span className="ws-mx__story" role="rowheader">
              <span className="ps-glyph">{s.glyph}</span>
              <span className="ws-mx__title" title={s.title}>{s.title}</span>
              <button className="ws-mx__fill" disabled={ws.busy || gaps === 0} title={gaps ? `Write ${gaps} more lenses` : 'Every lens written'}
                onClick={() => ws.fill({ storyId: s.id })}>+{gaps || ''}</button>
            </span>
            {LENSES.map(l => <Cell key={l.slug} ws={ws} storyId={s.id} lens={l.slug} fits={s.fits.includes(l.slug)} />)}
          </div>
        );
      })}

      {ws.loose.map(b => (
        <div key={b.id} className="ws-mx__row ws-mx__row--loose" role="row">
          <span className="ws-mx__story" role="rowheader">
            <span className="ps-glyph" title="No story">○</span>
            <span className="ws-mx__title" title={b.text.replace(/\*\*/g, '')}>{b.text.replace(/\*\*/g, '')}</span>
          </span>
          {LENSES.map(l => (l.slug === b.lens
            ? <CellBtn key={l.slug} ws={ws} id={b.id} lens={l.slug} />
            : <span key={l.slug} className="ws-cell" data-k="none" role="gridcell" />))}
        </div>
      ))}
    </div>
  );
}

function CellBtn({ ws, id, lens }: { ws: WS; id: string; lens: string }) {
  const b = ws.bullets.find(x => x.id === id)!;
  const prints = ws.printed.has(id);
  const k = b.status === 'APPROVED' ? 'approved' : 'bullet';
  return (
    <button className="ws-cell" role="gridcell" data-k={k} data-prints={prints || undefined} data-new={ws.newIds.has(id) || undefined}
      aria-selected={ws.selectedId === id} title={`${SHORT[lens]} · ${k === 'approved' ? 'Approved' : 'Bullet'}${prints ? ' · prints' : ''}\n${b.text.replace(/\*\*/g, '')}`}
      onClick={() => ws.setSelectedId(ws.selectedId === id ? null : id)}
      onMouseEnter={() => b.storyId && ws.setHoverStory(b.storyId)} onMouseLeave={() => ws.setHoverStory(null)}>
      {prints ? '▶' : k === 'approved' ? '✓' : ''}
    </button>
  );
}

function Cell({ ws, storyId, lens, fits }: { ws: WS; storyId: string; lens: string; fits: boolean }) {
  const id = wid(storyId, lens);
  if (ws.pending.has(id)) return <span className="ws-cell" data-k="pending" role="gridcell"><Spin /></span>;
  if (ws.has(storyId, lens)) return <CellBtn ws={ws} id={id} lens={lens} />;
  if (!fits) return <span className="ws-cell" data-k="none" role="gridcell" title={`No ${SHORT[lens]} angle`} />;
  return (
    <button className="ws-cell" role="gridcell" data-k="open" disabled={ws.busy} title={`Write ${SHORT[lens]}`}
      aria-label={`Write ${SHORT[lens]}`} onClick={() => ws.fill({ storyId, lenses: [lens] })}>+</button>
  );
}

/** The picked cell: its wording, status, fit and the other lenses of the same story. */
function CellEditor({ ws }: { ws: WS }) {
  const r = ws.selected;
  if (!r) return null;
  const s = storyOf(r.storyId);
  const sibs = ws.siblings(r).filter(w => w.id !== r.id);
  return (
    <section className="ws-edit" aria-label="Wording">
      <div className="ps-tools">
        <span className="ps-meta">
          <span className="ws-lens" title={LABEL[r.lens]}>{SHORT[r.lens]}</span>
          <StoryMark ws={ws} bullet={r} />
          {s && <span className="label muted ps-detail__title" title={s.title}>{s.title}</span>}
        </span>
        <button className="minibtn" onClick={() => ws.setSelectedId(null)} aria-label="Close">✕</button>
      </div>
      <div className="ws-edit__body">
        <StatusToggle ws={ws} bullet={r} />
        <div style={{ minWidth: 0 }}>
          <textarea className="field__textarea" style={{ minHeight: 84 }} value={r.text} aria-label="Wording"
            onChange={e => ws.patch(r.id, { text: e.target.value })} />
          <div className="ps-meta">
            <Fit text={r.text} />
            {r.tags.map(t => <span key={t} className="ps-tag">{t}</span>)}
            <span className="ws-acts"><Trash onClick={() => ws.remove(r.id)} /></span>
          </div>
        </div>
      </div>
      {sibs.length > 0 && (
        <div className="ws-edit__sibs">
          {sibs.map(w => (
            <button key={w.id} className="ps-sib" onClick={() => ws.setSelectedId(w.id)} title={w.text.replace(/\*\*/g, '')}>
              <span className="ps-sib__lens">{SHORT[w.lens]}</span>
              <span className="ps-sib__text"><RichText text={w.text} /></span>
              <span className="ps-sib__st">{ws.printed.has(w.id) ? '▶' : w.status === 'APPROVED' ? '✓' : '·'}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
