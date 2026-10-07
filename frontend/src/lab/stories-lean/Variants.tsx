import { useState, type DragEvent } from 'react';
import { RichText } from '../../components/RichText';
import type { StoryFx, WStatus, Wording } from '../stories/storyFixtures';
import {
  Dots, Fit, Lenses, Prints, Proof, ProofBody, Verdict, WTag, useToggleSet, type Bank, type Lane,
} from './model';

/* ── 1 · Rows: the old bullets tab, story as a thin group head ── */

export function RowsView({ b }: { b: Bank }) {
  const [tab, setTab] = useState<'stories' | 'older' | 'dismissed'>('stories');
  const [proof, toggleProof] = useToggleSet();
  return (
    <>
      <div className="tabs">
        <button className={tab === 'stories' ? 'is-on' : ''} onClick={() => setTab('stories')}>Stories <span className="tabs__badge">{b.live.length}</span></button>
        <button className={tab === 'older' ? 'is-on' : ''} onClick={() => setTab('older')}>Older <span className="tabs__badge">{b.loose.length}</span></button>
        <button className={tab === 'dismissed' ? 'is-on' : ''} onClick={() => setTab('dismissed')}>Dismissed <span className="tabs__badge">{b.dismissed.length}</span></button>
      </div>
      <div className="tabpane sl-pane">
        {tab === 'stories' && b.live.map(s => (
          <section key={s.id} className="sl-group" data-new={b.newIds.has(s.id) || undefined}>
            <header className="sl-group__head">
              <span className="sl-group__title">{s.title}</span>
              <span className="sl-group__meta">
                <Lenses ids={s.lenses} general={!b.lensesOn} />
                <Proof ev={s.evidence} open={proof.has(s.id)} onToggle={() => toggleProof(s.id)} />
              </span>
            </header>
            {proof.has(s.id) && <ProofBody ev={s.evidence} />}
            {b.ws(s).map((w, i) => (
              <WRow key={w.id} w={w} alt={i > 0} b={b} printed={b.prints(s) === w.id} />
            ))}
          </section>
        ))}
        {tab === 'older' && b.loose.map(l => (
          <div key={l.id} className="sl-row" data-s={b.status[l.id]}>
            <Verdict value={b.status[l.id]} onSet={st => b.setOne(l.id, st)} />
            <div className="sl-row__body">
              <p className="sl-text"><RichText text={l.text} /></p>
              <span className="sl-meta"><Fit text={l.text} /><span className="sl-wtag" title="No story">—</span></span>
            </div>
          </div>
        ))}
        {tab === 'dismissed' && <DismissedList b={b} />}
        {tab === 'older' && b.loose.length === 0 && <p className="sl-empty">∅</p>}
      </div>
    </>
  );
}

function WRow({ w, alt, b, printed }: { w: Wording; alt: boolean; b: Bank; printed: boolean }) {
  const st = b.status[w.id];
  return (
    <div className="sl-row" data-alt={alt || undefined} data-s={st}>
      <Verdict value={st} onSet={x => b.setOne(w.id, x)} />
      <div className="sl-row__body">
        <p className="sl-text"><RichText text={w.text} /></p>
        <span className="sl-meta">{printed && <Prints />}<Fit text={w.text} /><WTag w={w} /></span>
      </div>
    </div>
  );
}

function DismissedList({ b }: { b: Bank }) {
  if (b.dismissed.length === 0) return <p className="sl-empty">∅</p>;
  return (
    <ul className="sl-dis">
      {b.dismissed.map(s => (
        <li key={s.id}>
          <span title={b.ws(s).map(w => w.text).join('\n')}>{s.title}</span>
          <button type="button" className="minibtn" onClick={() => b.setStory(s, 'PENDING')} title="Restore">↺</button>
        </li>
      ))}
    </ul>
  );
}

/* ── 2 · Cards: one tile per story, best wording up front ── */

export function CardsView({ b }: { b: Bank }) {
  const [more, toggleMore] = useToggleSet();
  const [proof, toggleProof] = useToggleSet();
  const [showDis, setShowDis] = useState(false);
  return (
    <>
      <div className="sl-grid">
        {b.live.map(s => {
          const ws = b.ws(s);
          const pid = b.prints(s) ?? ws[0].id;
          const best = ws.find(w => w.id === pid)!;
          const rest = ws.filter(w => w.id !== pid);
          const lane = b.laneOf(s);
          return (
            <article key={s.id} className="sl-card" data-lane={lane} data-new={b.newIds.has(s.id) || undefined}>
              <header className="sl-card__top">
                <Lenses ids={s.lenses} general={!b.lensesOn} />
                <Proof ev={s.evidence} open={proof.has(s.id)} onToggle={() => toggleProof(s.id)} />
              </header>
              <h3 className="sl-card__title">{s.title}</h3>
              {proof.has(s.id) && <ProofBody ev={s.evidence} />}
              <CardWording w={best} b={b} printed={!!b.prints(s)} />
              {more.has(s.id) && rest.map(w => <CardWording key={w.id} w={w} b={b} printed={false} dim />)}
              <footer className="sl-card__foot">
                {rest.length > 0 && (
                  <button type="button" className="sl-more" aria-expanded={more.has(s.id)} onClick={() => toggleMore(s.id)}>
                    {more.has(s.id) ? '−' : `+${rest.length}`}
                  </button>
                )}
                <Dots ws={ws} status={b.status} />
                <Verdict label="story" value={lane === 'APPROVED' ? 'APPROVED' : 'PENDING'}
                  onSet={st => b.setStory(s, st)} />
              </footer>
            </article>
          );
        })}
        {b.loose.map(l => (
          <article key={l.id} className="sl-card sl-card--old" data-lane={b.status[l.id]}>
            <header className="sl-card__top"><span className="sl-wtag" title="Older, no story">Older</span></header>
            <p className="sl-text"><RichText text={l.text} /></p>
            <footer className="sl-card__foot">
              <span className="sl-meta"><Fit text={l.text} /></span>
              <Verdict value={b.status[l.id]} onSet={st => b.setOne(l.id, st)} />
            </footer>
          </article>
        ))}
      </div>
      {b.dismissed.length > 0 && (
        <div className="sl-strip">
          <button type="button" className="minibtn" aria-expanded={showDis} onClick={() => setShowDis(v => !v)}>
            ✕ Dismissed {b.dismissed.length}
          </button>
          {showDis && <DismissedList b={b} />}
        </div>
      )}
    </>
  );
}

function CardWording({ w, b, printed, dim }: { w: Wording; b: Bank; printed: boolean; dim?: boolean }) {
  const st = b.status[w.id];
  return (
    <div className="sl-cw" data-dim={dim || undefined} data-s={st}>
      <p className="sl-text"><RichText text={w.text} /></p>
      <span className="sl-meta">
        {printed && <Prints />}<Fit text={w.text} /><WTag w={w} />
        {dim && <Verdict value={st} onSet={x => b.setOne(w.id, x)} />}
      </span>
    </div>
  );
}

/* ── 3 · Split: story list left, the selected story right ── */

type Sel = { kind: 'story'; s: StoryFx } | { kind: 'older' } | null;

export function SplitView({ b }: { b: Bank }) {
  const [selId, setSelId] = useState<string | null>(b.live[0]?.id ?? null);
  const [proofOpen, setProofOpen] = useState(false);
  const all = [...b.live, ...b.dismissed];
  const sel: Sel = selId === '__older' ? { kind: 'older' } : (() => {
    const s = all.find(x => x.id === selId);
    return s ? { kind: 'story', s } : null;
  })();
  const pick = (id: string) => { setSelId(id); setProofOpen(false); };

  return (
    <div className="sl-split" data-detail={sel ? '' : undefined}>
      <nav className="sl-list" aria-label="Stories">
        {b.live.map(s => (
          <ListItem key={s.id} s={s} b={b} on={selId === s.id} onPick={() => pick(s.id)} />
        ))}
        {b.loose.length > 0 && (
          <button type="button" className="sl-li sl-li--sub" aria-current={selId === '__older' || undefined} onClick={() => pick('__older')}>
            <span className="sl-li__t">Older</span>
            <Dots ws={b.loose.map(l => ({ id: l.id, text: l.text, status: l.status }))} status={b.status} />
          </button>
        )}
        {b.dismissed.length > 0 && <div className="sl-list__k">✕ {b.dismissed.length}</div>}
        {b.dismissed.map(s => (
          <ListItem key={s.id} s={s} b={b} on={selId === s.id} onPick={() => pick(s.id)} />
        ))}
      </nav>

      <div className="sl-detail">
        {sel && <button type="button" className="sl-back minibtn" onClick={() => setSelId(null)}>←</button>}
        {sel?.kind === 'story' && (
          <>
            <h3 className="sl-detail__title">{sel.s.title}</h3>
            <div className="sl-detail__tags">
              <Lenses ids={sel.s.lenses} general={!b.lensesOn} />
              <Proof ev={sel.s.evidence} open={proofOpen} onToggle={() => setProofOpen(o => !o)} />
              <span className="sl-detail__all">
                <Verdict label="story" value={b.laneOf(sel.s) === 'APPROVED' ? 'APPROVED' : b.laneOf(sel.s) === 'REJECTED' ? 'REJECTED' : 'PENDING'}
                  onSet={st => b.setStory(sel.s, st)} />
              </span>
            </div>
            {proofOpen && <ProofBody ev={sel.s.evidence} />}
            {b.ws(sel.s).map((w, i) => (
              <WRow key={w.id} w={w} alt={i > 0} b={b} printed={b.prints(sel.s) === w.id} />
            ))}
          </>
        )}
        {sel?.kind === 'older' && b.loose.map(l => (
          <div key={l.id} className="sl-row" data-s={b.status[l.id]}>
            <Verdict value={b.status[l.id]} onSet={st => b.setOne(l.id, st)} />
            <div className="sl-row__body">
              <p className="sl-text"><RichText text={l.text} /></p>
              <span className="sl-meta"><Fit text={l.text} /></span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ListItem({ s, b, on, onPick }: { s: StoryFx; b: Bank; on: boolean; onPick: () => void }) {
  return (
    <button type="button" className="sl-li" aria-current={on || undefined} data-lane={b.laneOf(s)}
      data-new={b.newIds.has(s.id) || undefined} onClick={onPick}>
      <span className="sl-li__t">{s.title}</span>
      <Dots ws={b.ws(s)} status={b.status} />
    </button>
  );
}

/* ── 4 · Board: drag or press stories between three lanes ── */

const LANES: { id: Lane; label: string; glyph: string }[] = [
  { id: 'PENDING', label: 'Pending', glyph: '○' },
  { id: 'APPROVED', label: 'Approved', glyph: '✓' },
  { id: 'REJECTED', label: 'Dismissed', glyph: '✕' },
];

export function BoardView({ b }: { b: Bank }) {
  const [over, setOver] = useState<Lane | null>(null);
  const [open, toggleOpen] = useToggleSet();
  const all = [...b.live, ...b.dismissed];

  const onDrop = (lane: Lane) => (e: DragEvent) => {
    e.preventDefault();
    setOver(null);
    const id = e.dataTransfer.getData('text/plain');
    const s = all.find(x => x.id === id);
    if (s) b.setStory(s, lane);
    else if (b.loose.some(l => l.id === id)) b.setOne(id, lane);
  };

  return (
    <div className="sl-board">
      {LANES.map(lane => {
        const stories = all.filter(s => b.laneOf(s) === lane.id);
        const loose = b.loose.filter(l => b.status[l.id] === lane.id);
        return (
          <section key={lane.id} className="sl-lane" data-lane={lane.id} data-over={over === lane.id || undefined}
            onDragOver={e => { e.preventDefault(); setOver(lane.id); }}
            onDragLeave={() => setOver(o => (o === lane.id ? null : o))}
            onDrop={onDrop(lane.id)}>
            <h3 className="sl-lane__head"><span>{lane.glyph} {lane.label}</span><span>{stories.length + loose.length}</span></h3>
            {stories.map(s => (
              <article key={s.id} className="sl-chipcard" draggable data-new={b.newIds.has(s.id) || undefined}
                onDragStart={e => e.dataTransfer.setData('text/plain', s.id)}>
                <button type="button" className="sl-chipcard__t" aria-expanded={open.has(s.id)} onClick={() => toggleOpen(s.id)}
                  title={s.evidence.map(e => `“${e.quote}”`).join('\n')}>
                  {s.title}
                </button>
                <div className="sl-chipcard__row">
                  <Lenses ids={s.lenses} general={!b.lensesOn} />
                  <Dots ws={b.ws(s)} status={b.status} />
                  <Move lane={lane.id} onMove={st => b.setStory(s, st)} />
                </div>
                {open.has(s.id) && b.ws(s).map(w => (
                  <div key={w.id} className="sl-cw" data-s={b.status[w.id]}>
                    <p className="sl-text"><RichText text={w.text} /></p>
                    <span className="sl-meta">
                      {b.prints(s) === w.id && <Prints />}<Fit text={w.text} /><WTag w={w} />
                      <Verdict value={b.status[w.id]} onSet={x => b.setOne(w.id, x)} />
                    </span>
                  </div>
                ))}
              </article>
            ))}
            {loose.map(l => (
              <article key={l.id} className="sl-chipcard sl-chipcard--old" draggable
                onDragStart={e => e.dataTransfer.setData('text/plain', l.id)}>
                <span className="sl-chipcard__t sl-chipcard__t--static" title={l.text}>
                  <span className="sl-wtag">Older</span> {l.text}
                </span>
                <div className="sl-chipcard__row">
                  <span className="sl-meta"><Fit text={l.text} /></span>
                  <Move lane={lane.id} onMove={st => b.setOne(l.id, st)} />
                </div>
              </article>
            ))}
          </section>
        );
      })}
    </div>
  );
}

function Move({ lane, onMove }: { lane: Lane; onMove: (s: WStatus) => void }) {
  return (
    <span className="sl-move">
      {LANES.filter(l => l.id !== lane).map(l => (
        <button key={l.id} type="button" data-s={l.id} title={l.label} aria-label={l.label} onClick={() => onMove(l.id)}>
          {l.id === 'PENDING' ? '↺' : l.glyph}
        </button>
      ))}
    </span>
  );
}
