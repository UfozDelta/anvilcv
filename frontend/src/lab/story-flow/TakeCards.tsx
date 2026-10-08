/* Take 1 · Story cards: each story a card, its lens wordings beneath, 4 lens buttons on the card. */
import { useState } from 'react';
import { Spin } from '../workspace/parts';
import { BY_ID, LENSES, type Lens } from './data';
import { fits, type SF } from './model';
import { EvidenceChip, Finding, Quotes, Wordings } from './parts';

/** Filled = has it · outlined = get it · dashed off = doesn't fit. */
export function LensButtons({ sf, id }: { sf: SF; id: string }) {
  return (
    <div className="sf-lbtns" role="group" aria-label="Lenses">
      {LENSES.map(l => {
        const have = sf.has(id, l.slug);
        const wait = sf.isPending(id, l.slug);
        const ok = fits(id, l.slug);
        const state = have ? 'have' : wait ? 'wait' : ok ? 'get' : 'off';
        const tip = have ? `${l.name}: written` : wait ? 'Writing…' : ok ? `Get ${l.name} bullet` : `Doesn't fit ${l.name}`;
        return (
          <button key={l.slug} type="button" className="sf-lbtn" data-s={state} disabled={state !== 'get'} title={tip} aria-label={tip}
            onClick={() => sf.getLenses(id, [l.slug as Lens])}>
            {state === 'wait' ? <Spin /> : state === 'get' ? '+' : state === 'have' ? '✓' : ''} {l.name}
          </button>
        );
      })}
    </div>
  );
}

export function TakeCards({ sf }: { sf: SF }) {
  const [ev, setEv] = useState<string | null>(null);
  return (
    <div className="sf-cards">
      <Finding n={sf.finding} />
      {sf.stories.map(id => {
        const s = BY_ID[id];
        return (
          <article key={id} className="sf-card" data-new={sf.newIds.has(id) || undefined}>
            <header className="sf-card__head">
              <span className="sf-glyph" aria-hidden="true">{s.glyph}</span>
              <h3 className="sf-card__title">{s.title}</h3>
              <EvidenceChip id={id} open={ev === id} onToggle={() => setEv(e => (e === id ? null : id))} />
              <LensButtons sf={sf} id={id} />
            </header>
            {ev === id && <Quotes id={id} />}
            <Wordings sf={sf} id={id} />
          </article>
        );
      })}
    </div>
  );
}
