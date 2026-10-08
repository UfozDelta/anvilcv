/* Generate tab: 1 story → 2 lens(es) → 3 Generate. */
import { useState } from 'react';
import { RichText } from '../../components/RichText';
import { Spin } from '../../components/ledger/parts';
import { BY_ID, LENSES, type Lens } from './data';
import { fits, lensOf, type Bullet, type SF } from './model';
import { Finding, LensTag } from './parts';

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

  const pick = (id: string) => { setStory(id); setPicked(new Set()); };
  const flip = (l: Lens) => setPicked(s => { const n = new Set(s); n.has(l) ? n.delete(l) : n.add(l); return n; });
  const generate = () => {
    if (isNew) { setRun({ story: NEW, ids: [], before: sf.stories }); sf.generate(); }
    else setRun({ story: sid, ids: sf.getLenses(sid, go), before: [] });
    setPicked(new Set());
  };

  return (
    <div className="sf-gen">
      <section className="sf-step" aria-labelledby="sf-a">
        <h3 className="sf-step__k" id="sf-a"><b>1</b> Story</h3>
        <div className="sf-opts" role="radiogroup" aria-labelledby="sf-a">
          {sf.stories.map(id => {
            const s = BY_ID[id];
            const n = sf.wordings(id).length;
            return (
              <button key={id} type="button" role="radio" aria-checked={sid === id} className="sf-opt" onClick={() => pick(id)}
                title={s.evidence.map(q => `${q.src}: ${q.text}`).join('\n')}>
                <span className="sf-glyph" aria-hidden="true">{s.glyph}</span>
                <span className="sf-opt__name">{s.title}</span>
                <span className="sf-opt__n" title={`${n} wording${n === 1 ? '' : 's'}`}>{n}</span>
              </button>
            );
          })}
          <button type="button" role="radio" aria-checked={isNew} className="sf-opt sf-opt--new" onClick={() => pick(NEW)}
            title={newOff ?? 'Find new stories in the source, 1 bullet each in its best lens'}>
            <span className="sf-glyph" aria-hidden="true">＋</span>
            <span className="sf-opt__name">New stories</span>
            {newOff && <span className="sf-opt__n">{sf.room === 0 ? 'Full' : 'None'}</span>}
          </button>
        </div>
      </section>

      <section className="sf-step" aria-labelledby="sf-b" data-idle={isNew || undefined}>
        <h3 className="sf-step__k" id="sf-b"><b>2</b> Lens</h3>
        <div className="sf-lenses">
          {LENSES.map(l => {
            const n = isNew ? 0 : sf.count(sid, l.slug);
            const wait = !isNew && sf.isPending(sid, l.slug);
            const weak = !isNew && !fits(sid, l.slug);
            const on = picked.has(l.slug) && !wait && !isNew;
            const s = isNew ? 'off' : wait ? 'wait' : on ? 'on' : 'get';
            return (
              <button key={l.slug} type="button" className="sf-chip" data-s={s} aria-pressed={on}
                disabled={isNew || wait} onClick={() => flip(l.slug)}
                title={isNew ? 'Best fit, picked per story' : wait ? 'Writing…' : l.tip}>
                <span className="sf-chip__mark" aria-hidden="true">{wait ? <Spin /> : on ? '■' : '□'}</span>
                {l.name}
                {weak && <span className="sf-chip__weak" title={`Weak fit: little evidence for ${l.name}`}>weak fit</span>}
                {n > 0 && <span className="sf-chip__has" title={`Already has ${n} wording${n === 1 ? '' : 's'}`}>✓ {n}</span>}
              </button>
            );
          })}
        </div>
        {isNew && <span className="sf-auto" title="Each new story arrives with 1 bullet in its best lens">Auto</span>}
      </section>

      <section className="sf-step" aria-labelledby="sf-c">
        <h3 className="sf-step__k" id="sf-c"><b>3</b> Generate</h3>
        <button type="button" className="btn btn--acid sf-go" disabled={!can} onClick={generate}
          title={isNew ? (newOff ?? 'Find new stories') : go.length === 0 ? 'Pick a lens' : `Write ${go.length} bullet${go.length === 1 ? '' : 's'}`}>
          {busy ? <Spin /> : '✦'} Generate{go.length > 1 && ` ${go.length}`}
        </button>
        {run && <Result sf={sf} run={run} onView={onView} />}
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
  const empty = isNew && sf.finding === 0 && bs.length === 0 && sf.last;
  return (
    <div className="sf-out" aria-live="polite">
      {isNew && <Finding n={sf.finding} />}
      {wait.map(l => <div key={l} className="sf-out__row"><Spin /> <LensTag lens={l} /></div>)}
      {bs.map(b => (
        <div key={b.id} className="sf-out__row" data-new>
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
