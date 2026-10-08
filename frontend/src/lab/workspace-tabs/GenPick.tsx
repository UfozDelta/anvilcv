/* G1 · Pick from repo: tick areas, candidates appear per area, Keep writes every lens it fits. */
import { useEffect, useRef, useState } from 'react';
import { Spin } from '../workspace/parts';
import { AREAS, AREA_OF, LINE, type Term } from './data';
import { fitsOf, storyOf, type WS } from './model';
import { LensDots } from './bullets';

type P = { ws: WS; term: Term; toBullets: () => void };

export function GenPick({ ws, term, toBullets }: P) {
  const [ticked, setTicked] = useState<Set<string>>(new Set());
  const [scanning, setScanning] = useState<Set<string>>(new Set());
  const [skipped, setSkipped] = useState<Set<string>>(new Set());
  const [kept, setKept] = useState<string[]>([]);
  const timers = useRef<number[]>([]);
  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const scan = (names: string[]) => {
    setScanning(s => new Set([...s, ...names]));
    timers.current.push(window.setTimeout(() => setScanning(s => {
      const n = new Set(s);
      names.forEach(x => n.delete(x));
      return n;
    }), 650));
  };
  const flip = (name: string) => {
    const on = ticked.has(name);
    setTicked(t => { const n = new Set(t); on ? n.delete(name) : n.add(name); return n; });
    if (!on) scan([name]);
  };
  const all = ticked.size === AREAS.length;
  const flipAll = () => {
    if (all) { setTicked(new Set()); return; }
    const add = AREAS.map(a => a.name).filter(n => !ticked.has(n));
    setTicked(new Set(AREAS.map(a => a.name)));
    scan(add);
  };

  const shownAreas = AREAS.filter(a => ticked.has(a.name) && !scanning.has(a.name));
  const found = shownAreas.flatMap(a => a.items.filter(id => LINE[id] && (!ws.inBank(id) || kept.includes(id))));
  const live = found.filter(id => !skipped.has(id));
  const skippedHere = found.filter(id => skipped.has(id));
  const keep = (id: string) => { setKept(k => [...k, id]); ws.take([{ id, lenses: fitsOf(id) }]); };
  const skip = (id: string, on: boolean) => setSkipped(s => { const n = new Set(s); on ? n.add(id) : n.delete(id); return n; });

  return (
    <div className="wt-pick">
      <aside className="wt-areas" aria-label="Repo areas">
        <div className="wt-bar">
          <span className="wt-k">Areas</span>
          <button className="minibtn" onClick={flipAll}>{all ? 'None' : 'All'}</button>
        </div>
        {AREAS.map(a => {
          const have = a.items.filter(id => ws.inBank(id));
          const on = ticked.has(a.name);
          return (
            <label key={a.name} className="wt-area" data-on={on || undefined} title={`${a.purpose}\n${a.modules.join(', ')} · ${a.files} files`}>
              <input type="checkbox" checked={on} onChange={() => flip(a.name)} />
              <span className="ws-lens-opt__box" aria-hidden="true">{on ? '✓' : ''}</span>
              <span className="wt-area__name">{a.name}</span>
              <span className="wt-area__have" title={have.length ? have.map(id => storyOf(id)!.title).join('\n') : `No ${term.many.toLowerCase()} yet`}>
                {have.length ? have.map(id => storyOf(id)!.glyph).join('') : '—'}
              </span>
              <span className="wt-area__files">{a.files}</span>
            </label>
          );
        })}
      </aside>

      <section className="wt-found" aria-label={term.many} aria-live="polite">
        <div className="wt-bar">
          <span className="wt-k">{term.many} <span className="ps-chips__n">{live.length}</span></span>
          <span className="wt-room" data-full={ws.room === 0 || undefined} title={`Room for ${ws.room} more`}>{ws.room} left</span>
        </div>
        {ticked.size === 0 && <div className="wt-empty">← Tick areas</div>}
        {scanning.size > 0 && <div className="wt-cand wt-cand--wait"><Spin /></div>}
        {ticked.size > 0 && scanning.size === 0 && found.length === 0 && <div className="wt-empty">Nothing new</div>}
        <ul className="wt-cands">
          {live.map(id => <Cand key={id} ws={ws} id={id} kept={ws.inBank(id)} onKeep={() => keep(id)} onSkip={() => skip(id, true)} />)}
        </ul>
        {skippedHere.length > 0 && (
          <div className="wt-skipped">
            <span className="wt-k">Skipped</span>
            {skippedHere.map(id => (
              <button key={id} className="minibtn" title="Restore" onClick={() => skip(id, false)}>{storyOf(id)!.glyph} {storyOf(id)!.title}</button>
            ))}
          </div>
        )}
        {kept.some(id => ws.inBank(id)) && !ws.busy && (
          <button className="btn btn--sm wt-go" onClick={toBullets}>Bullets <span className="ps-chips__n">+{ws.newIds.size}</span></button>
        )}
      </section>
    </div>
  );
}

function Cand({ ws, id, kept, onKeep, onSkip }: { ws: WS; id: string; kept: boolean; onKeep: () => void; onSkip: () => void }) {
  const s = storyOf(id)!;
  const waiting = ws.pending.has(id);
  return (
    <li className="wt-cand" data-kept={kept || undefined}>
      <span className="wt-cand__glyph">{s.glyph}</span>
      <div className="wt-cand__body">
        <div className="wt-cand__title">{s.title}</div>
        <div className="wt-cand__line">{LINE[id]}</div>
        <div className="wt-cand__meta">
          <span className="ps-tag" title="Repo area">{AREA_OF[id]}</span>
          <LensDots fits={s.fits} on={l => ws.has(id, l)} />
        </div>
      </div>
      <div className="wt-cand__acts">
        {waiting ? <Spin /> : kept ? <span className="wt-done" title="In Bullets">✓ Kept</span> : (
          <>
            <button className="btn btn--sm btn--acid wt-keep" disabled={ws.busy || ws.room === 0} title={ws.room === 0 ? 'Bank full' : `Write ${s.fits.length} lenses`} onClick={onKeep}>Keep</button>
            <button className="minibtn" onClick={onSkip}>Skip</button>
          </>
        )}
      </div>
    </li>
  );
}
