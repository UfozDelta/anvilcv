import { useState } from 'react';
import { EventStream } from '../EventStream';
import { Spin } from '../ledger/parts';
import { isLive, lensChoice, weakFit } from '../../lib/storyBank';
import type { useStoryBank } from '../../hooks/useStoryBank';

type SB = ReturnType<typeof useStoryBank>;

const NEW = '+new';

/** Lens cards, same names and order as the lab's story-flow prototype. */
export const GENERATE_LENSES: { slug: string; name: string; tip: string }[] = [
  { slug: 'ai-ml', name: 'AI/ML', tip: 'Models, training, prediction' },
  { slug: 'backend', name: 'Backend', tip: 'APIs, services, storage' },
  { slug: 'data', name: 'Data Eng', tip: 'Pipelines, parsing, ingestion' },
  { slug: 'general', name: 'General', tip: 'Any role: outcome first' },
];

/**
 * Generate tab, same shape as the lab: 1 Story -> 2 Lens -> 3 Generate. Story rows are radio
 * buttons; "+ New stories" finds stories on the best lens of each. A story runs "more wordings"
 * for the lenses picked. The run is a background job shown in the live progress panel.
 */
export function StoryGenerate({ sb }: { sb: SB }) {
  const [story, setStory] = useState<string>(sb.stories[0]?.id ?? NEW);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [job, setJob] = useState<{ submitUrl: string; submitBody: object; title: string } | null>(null);

  const sid = story === NEW || sb.stories.some(s => s.id === story) ? story : (sb.stories[0]?.id ?? NEW);
  const isNew = sid === NEW;
  const current = sb.stories.find(s => s.id === sid);
  const wordings = current ? sb.bullets.filter(b => b.storyId === current.id && isLive(b)) : [];
  const full = sb.stories.length >= sb.cap;
  const newOff = full ? 'Bank full' : null;
  const busy = job !== null;
  const can = !busy && (isNew ? !full : picked.size > 0);
  const why = busy ? 'Working…' : can ? null : isNew ? (newOff ?? 'Pick a story') : 'Pick a lens';

  function pick(id: string) {
    setStory(id);
    setPicked(new Set());
  }

  function flip(slug: string) {
    setPicked(s => { const n = new Set(s); n.has(slug) ? n.delete(slug) : n.add(slug); return n; });
  }

  function generate() {
    if (isNew) setJob({ ...sb.newStoriesJob(), title: 'Finding stories' });
    else if (current) setJob({ ...sb.wordingsJob(current.id, [...picked]), title: 'Writing wordings' });
  }

  return (
    <div className="sf-gen">
      <section className="sf-step" aria-labelledby="sf-a">
        <h2 className="sf-step__k" id="sf-a"><b>1</b> Story</h2>
        <div className="sf-opts" role="radiogroup" aria-labelledby="sf-a">
          {sb.stories.map(s => (
            <button key={s.id} type="button" role="radio" aria-checked={sid === s.id} className="sf-opt" onClick={() => pick(s.id)}>
              <span className="sf-opt__name">{s.title}</span>
              <span className="sf-opt__n">{sb.bullets.filter(b => b.storyId === s.id && isLive(b)).length}<span className="sr-only"> bullets</span></span>
            </button>
          ))}
          <button type="button" role="radio" aria-checked={isNew} className="sf-opt sf-opt--new" onClick={() => pick(NEW)}>
            <span className="sf-opt__name">+ New stories</span>
            {newOff && <span className="sf-opt__n">Full</span>}
          </button>
        </div>
      </section>

      <section className="sf-step" aria-labelledby="sf-b" data-idle={isNew || undefined}>
        <h2 className="sf-step__k" id="sf-b"><b>2</b> Lens</h2>
        {isNew || !current ? (
          <p className="sf-auto">Best lens per story</p>
        ) : (
          <div className="sf-lenses">
            {GENERATE_LENSES.map(l => {
              const ch = lensChoice(current, wordings, l.slug);
              const on = picked.has(l.slug);
              return (
                <button key={l.slug} type="button" className="sf-chip" data-s={on ? 'on' : 'get'} aria-pressed={on}
                  title={l.tip} disabled={busy} onClick={() => flip(l.slug)}>
                  <span className="sf-chip__mark" aria-hidden="true">{on ? '■' : '□'}</span>
                  {l.name}
                  {weakFit(current, l.slug) && <span className="sf-chip__weak">weak fit</span>}
                  {ch.count > 0 && <span className="sf-chip__has">has {ch.count}</span>}
                </button>
              );
            })}
          </div>
        )}
      </section>

      <section className="sf-step" aria-labelledby="sf-c">
        <h2 className="sf-step__k" id="sf-c"><b>3</b> Generate</h2>
        <button type="button" className="btn btn--acid sf-go" disabled={!can} aria-describedby={why ? 'sf-why' : undefined} onClick={generate}>
          {busy ? <Spin label="Working" /> : <span aria-hidden="true">✦</span>} Generate
        </button>
        {why && <p className="sf-go__why" id="sf-why">{why}</p>}
        {job && (
          <EventStream
            submitUrl={job.submitUrl}
            submitBody={job.submitBody}
            pollUrl={sb.pollUrl}
            onDone={() => { setJob(null); setPicked(new Set()); sb.load(); }}
            onClose={() => setJob(null)}
            title={`${job.title}…`}
            doneLabel=""
          />
        )}
      </section>
    </div>
  );
}
