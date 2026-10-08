/* Take 3 · Lens columns: one row per story, one slot per lens; "+" writes it; expand a row to edit. */
import { Fragment, useState } from 'react';
import { RichText } from '../../components/RichText';
import { Spin } from '../workspace/parts';
import { BY_ID, LENSES, type Lens } from './data';
import { fits, wid, type SF } from './model';
import { EvidenceChip, Finding, Quotes, Wordings } from './parts';

function Slot({ sf, id, lens, onOpen }: { sf: SF; id: string; lens: Lens; onOpen: () => void }) {
  const name = LENSES.find(l => l.slug === lens)!.name;
  const b = sf.wordings(id).find(w => w.lens === lens);
  if (b) {
    const prints = sf.wordings(id).length > 1 && sf.printed(id)?.id === b.id;
    return (
      <button type="button" className="sf-slot" data-s="have" data-in={b.status === 'APPROVED' || undefined} data-new={sf.newIds.has(b.id) || undefined}
        title={`${name}${b.status === 'APPROVED' ? ' · approved' : ''}${prints ? ' · prints' : ''}`} onClick={onOpen}>
        <span className="sf-slot__k">{name}</span>
        {(b.status === 'APPROVED' || prints) && <span className="sf-slot__mark">{b.status === 'APPROVED' && '✓ '}{prints && '▶'}</span>}
        <span className="sf-slot__text"><RichText text={b.text} /></span>
      </button>
    );
  }
  if (sf.pending.has(wid(id, lens))) {
    return <div className="sf-slot" data-s="wait" title="Writing…"><span className="sf-slot__k">{name}</span><Spin /></div>;
  }
  if (!fits(id, lens)) {
    return <div className="sf-slot" data-s="off" title={`Doesn't fit ${name}`}><span className="sf-slot__k">{name}</span>—</div>;
  }
  return (
    <button type="button" className="sf-slot" data-s="get" title={`Get ${name} bullet`} aria-label={`Get ${name} bullet`}
      onClick={() => sf.getLenses(id, [lens])}>
      <span className="sf-slot__k">{name}</span><span className="sf-slot__plus">+</span>
    </button>
  );
}

export function TakeColumns({ sf }: { sf: SF }) {
  const [open, setOpen] = useState<string | null>(null);
  const flip = (id: string) => setOpen(o => (o === id ? null : id));
  return (
    <div className="sf-grid" role="table" aria-label="Stories by lens">
      <div className="sf-grid__head" role="row">
        <span role="columnheader">Story</span>
        {LENSES.map(l => <span key={l.slug} role="columnheader" title={l.tip}>{l.name}</span>)}
      </div>
      <Finding n={sf.finding} />
      {sf.stories.map(id => {
        const s = BY_ID[id];
        const isOpen = open === id;
        return (
          <Fragment key={id}>
            <div className="sf-grid__row" role="row" data-open={isOpen || undefined} data-new={sf.newIds.has(id) || undefined}>
              <div className="sf-grid__story" role="rowheader">
                <button type="button" className="sf-grid__name" aria-expanded={isOpen} onClick={() => flip(id)} title={isOpen ? 'Collapse' : 'Expand to edit'}>
                  <span className="sf-caret" aria-hidden="true">{isOpen ? '▾' : '▸'}</span>
                  <span className="sf-glyph" aria-hidden="true">{s.glyph}</span>
                  <span className="sf-grid__title">{s.title}</span>
                </button>
                <EvidenceChip id={id} open={isOpen} onToggle={() => flip(id)} />
              </div>
              {LENSES.map(l => <Slot key={l.slug} sf={sf} id={id} lens={l.slug} onOpen={() => setOpen(id)} />)}
            </div>
            {isOpen && (
              <div className="sf-grid__open">
                <Quotes id={id} />
                <Wordings sf={sf} id={id} />
              </div>
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
