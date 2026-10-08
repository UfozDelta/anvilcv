/* Take 2 · Pick then build: story list left; the picked story's wordings and a "Get bullets" panel right. */
import { useState } from 'react';
import { Spin } from '../workspace/parts';
import { BY_ID, LENSES, type Lens } from './data';
import { fits, type SF } from './model';
import { Quotes, Wordings } from './parts';

function Slots({ sf, id }: { sf: SF; id: string }) {
  return (
    <span className="sf-slots" aria-hidden="true">
      {LENSES.map(l => (
        <i key={l.slug} data-s={sf.has(id, l.slug) ? 'have' : sf.isPending(id, l.slug) ? 'wait' : fits(id, l.slug) ? 'get' : 'off'} title={l.name} />
      ))}
    </span>
  );
}

function GetPanel({ sf, id }: { sf: SF; id: string }) {
  const [picked, setPicked] = useState<Set<Lens>>(new Set());
  const missing = LENSES.filter(l => !sf.has(id, l.slug));
  const go = [...picked].filter(l => sf.open(id).includes(l));
  if (missing.length === 0) return null;
  const flip = (l: Lens) => setPicked(s => { const n = new Set(s); n.has(l) ? n.delete(l) : n.add(l); return n; });
  return (
    <div className="sf-get">
      <span className="sf-get__k">Get bullets</span>
      <div className="sf-get__opts">
        {missing.map(l => {
          const wait = sf.isPending(id, l.slug);
          const ok = fits(id, l.slug);
          return (
            <label key={l.slug} className="sf-check" data-on={(picked.has(l.slug) && !wait) || undefined} data-off={!ok || undefined}
              title={!ok ? `Doesn't fit ${l.name}` : wait ? 'Writing…' : l.tip}>
              <input type="checkbox" disabled={!ok || wait} checked={picked.has(l.slug) && !wait} onChange={() => flip(l.slug)} />
              {wait ? <Spin /> : <span className="sf-check__box" aria-hidden="true" />}
              {l.name}
            </label>
          );
        })}
      </div>
      <button type="button" className="btn btn--sm btn--acid sf-get__go" disabled={go.length === 0}
        onClick={() => { sf.getLenses(id, go); setPicked(new Set()); }}>
        ✦ Generate{go.length > 0 && ` ${go.length}`}
      </button>
    </div>
  );
}

export function TakePick({ sf }: { sf: SF }) {
  const [sel, setSel] = useState<string>(sf.stories[0]);
  const [view, setView] = useState<'list' | 'detail'>('list');
  const id = sf.stories.includes(sel) ? sel : sf.stories[0];
  const s = id ? BY_ID[id] : null;
  return (
    <div className="sf-pick" data-view={view}>
      <ul className="sf-list" role="listbox" aria-label="Stories">
        {Array.from({ length: sf.finding }, (_, i) => <li key={`f-${i}`} className="sf-finding"><Spin /></li>)}
        {sf.stories.map(sid => {
          const st = BY_ID[sid];
          return (
            <li key={sid} role="option" aria-selected={sid === id} tabIndex={0} className="sf-item" data-new={sf.newIds.has(sid) || undefined}
              onClick={() => { setSel(sid); setView('detail'); }}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSel(sid); setView('detail'); } }}>
              <span className="sf-glyph" aria-hidden="true">{st.glyph}</span>
              <span className="sf-item__title">{st.title}</span>
              <Slots sf={sf} id={sid} />
            </li>
          );
        })}
      </ul>
      {s && (
        <section className="sf-detail" key={id}>
          <button type="button" className="minibtn sf-back" onClick={() => setView('list')}>← Stories</button>
          <header className="sf-detail__head">
            <span className="sf-glyph" aria-hidden="true">{s.glyph}</span>
            <h3 className="sf-card__title">{s.title}</h3>
          </header>
          <Quotes id={id} />
          <Wordings sf={sf} id={id} />
          <GetPanel sf={sf} id={id} />
        </section>
      )}
    </div>
  );
}
