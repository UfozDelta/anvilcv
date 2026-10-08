/* Generate tab: 1 story → 2 lens(es) → 3 Generate. */
import { useState } from 'react';
import { RichText } from '../../components/RichText';
import { Spin } from '../../components/ledger/parts';
import { BY_ID, LENS_OF, LENSES, type Lens } from './data';
import { fits, lensOf, type Bullet, type SF } from './model';
import { Finding, LensTag, PaneStatus } from './parts';

const NEW = '+new';
type Run = { story: string; ids: string[]; before: string[] };

export function GenerateTab({ sf, onView }: { sf: SF; onView: (storyId: string | null) => void }) {
  const [story, setStory] = useState<string>(sf.stories[0] ?? NEW);
  const [picked, setPicked] = useState<Set<Lens>>(new Set());
  const [run, setRun] = useState<Run | null>(null);

  const sid = story === NEW || sf.stories.includes(story) ? story : (sf.stories[0] ?? NEW);
  const isNew = sid === NEW;
  const newOff = sf.room === 0 ? 'Bank full' : sf.left === 0 ? 'No new stories left' : null;
  const go = isNew ? [] : [...picked].filter(l => !sf.isPending(sid, l));
  const busy = sf.finding > 0;
  const can = isNew ? !newOff && !busy : go.length > 0;
  const why = can ? null : isNew ? (newOff ?? 'Finding stories…') : 'Pick a lens';
  const lensRun = run && run.story !== NEW ? run.ids : [];
  const writing = lensRun.filter(id => sf.pending.has(id)).map(id => LENS_OF[lensOf(id)].name);
  const status = busy ? 'Finding stories…' : writing.length ? `Writing ${writing.join(', ')}…` : null;

  const pick = (id: string) => { setStory(id); setPicked(new Set()); };
  const flip = (l: Lens) => setPicked(s => { const n = new Set(s); n.has(l) ? n.delete(l) : n.add(l); return n; });
  const generate = () => {
    if (isNew) { setRun({ story: NEW, ids: [], before: sf.stories }); sf.generate(); }
    else setRun({ story: sid, ids: sf.getLenses(sid, go), before: [] });
    setPicked(new Set());
  };

  return (
    <div className="sf-gen">
      <PaneStatus busy={status} done={lensRun.some(id => sf.failed.has(id)) ? 'Generation failed' : 'Done'} />
      <section className="sf-step" aria-labelledby="sf-a">
        <h2 className="sf-step__k" id="sf-a"><b>1</b> Story</h2>
        <div className="sf-opts" role="radiogroup" aria-labelledby="sf-a">
          {sf.stories.map(id => {
            const n = sf.wordings(id).length;
            return (
              <button key={id} type="button" role="radio" aria-checked={sid === id} className="sf-opt" onClick={() => pick(id)}>
                <span className="sf-opt__name">{BY_ID[id].title}</span>
                <span className="sf-opt__n">{n}<span className="sr-only"> bullet{n === 1 ? '' : 's'}</span></span>
              </button>
            );
          })}
          <button type="button" role="radio" aria-checked={isNew} className="sf-opt sf-opt--new" onClick={() => pick(NEW)}>
            <span className="sf-opt__name">+ New stories</span>
            {newOff && <span className="sf-opt__n">{sf.room === 0 ? 'Full' : 'None'}</span>}
          </button>
        </div>
      </section>

      <section className="sf-step" aria-labelledby="sf-b" data-idle={isNew || undefined}>
        <h2 className="sf-step__k" id="sf-b"><b>2</b> Lens</h2>
        {isNew ? (
          <p className="sf-auto">Best lens per story</p>
        ) : (
          <div className="sf-lenses">
            {LENSES.map(l => {
              const n = sf.count(sid, l.slug);
              const wait = sf.isPending(sid, l.slug);
              const weak = !fits(sid, l.slug);
              const on = picked.has(l.slug) && !wait;
              return (
                <button key={l.slug} type="button" className="sf-chip" data-s={wait ? 'wait' : on ? 'on' : 'get'} aria-pressed={on}
                  disabled={wait} onClick={() => flip(l.slug)}>
                  <span className="sf-chip__mark" aria-hidden="true">{wait ? <Spin label={`Writing ${l.name}`} /> : on ? '■' : '□'}</span>
                  {l.name}
                  {weak && <span className="sf-chip__weak">weak fit</span>}
                  {n > 0 && <span className="sf-chip__has">has {n}</span>}
                </button>
              );
            })}
          </div>
        )}
      </section>

      <section className="sf-step" aria-labelledby="sf-c">
        <h2 className="sf-step__k" id="sf-c"><b>3</b> Generate</h2>
        <button type="button" className="btn btn--acid sf-go" disabled={!can} aria-describedby={why ? 'sf-why' : undefined} onClick={generate}>
          {busy ? <Spin label="Finding stories" /> : <span aria-hidden="true">✦</span>} Generate
        </button>
        {why && <p className="sf-go__why" id="sf-why">{why}</p>}
        {run && <Result sf={sf} run={run} onView={onView} />}
        <label className="sf-sim">
          <input type="checkbox" checked={sf.failSim} onChange={e => sf.setFailSim(e.target.checked)} /> Lab: make lens runs fail
        </label>
      </section>
    </div>
  );
}

function Result({ sf, run, onView }: { sf: SF; run: Run; onView: (storyId: string | null) => void }) {
  const isNew = run.story === NEW;
  const bs = isNew
    ? sf.bullets.filter(b => !run.before.includes(b.storyId))
    : run.ids.map(id => sf.bullets.find(b => b.id === id)).filter((b): b is Bullet => !!b);
  const wait = isNew ? [] : run.ids.filter(id => sf.pending.has(id)).map(lensOf);
  const failed = isNew ? [] : run.ids.filter(id => sf.failed.has(id));
  const empty = isNew && sf.finding === 0 && bs.length === 0 && sf.last;
  return (
    <div className="sf-out">
      {isNew && <Finding n={sf.finding} />}
      {wait.map(l => <div key={l} className="sf-out__row"><Spin label={`Writing ${LENS_OF[l].name}`} /> <LensTag lens={l} /></div>)}
      {failed.map(id => (
        <div key={id} className="sf-out__row" data-err>
          <LensTag lens={lensOf(id)} />
          <span className="sf-out__err">Couldn’t write this one.</span>
          <button type="button" className="minibtn" onClick={() => sf.getLenses(run.story, [lensOf(id)])}>Retry</button>
        </div>
      ))}
      {bs.map(b => (
        <div key={b.id} className="sf-out__row" data-new={sf.newIds.has(b.id) || undefined}>
          <LensTag lens={b.lens} />
          <div className="sf-out__text">
            {isNew && <b>{BY_ID[b.storyId].title}</b>}
            <RichText text={b.text} />
          </div>
        </div>
      ))}
      {empty && <span className="sf-out__none">{sf.last?.full ? 'Bank full' : 'Nothing new'}</span>}
      {bs.length > 0 && (
        <button type="button" className="minibtn sf-out__view" onClick={() => onView(isNew ? bs[0].storyId : run.story)}>
          View in Bullets
        </button>
      )}
    </div>
  );
}
