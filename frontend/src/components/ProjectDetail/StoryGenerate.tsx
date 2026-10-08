import { useState } from 'react';
import { EventStream } from '../EventStream';
import { CATEGORIES } from '../../lib/api';
import { isLive, lensChoice, lensLabel } from '../../lib/storyBank';
import type { useStoryBank } from '../../hooks/useStoryBank';

type SB = ReturnType<typeof useStoryBank>;

/**
 * Generate tab. Step A: "New stories" or one existing story. Step B (existing story only): pick
 * lenses, any of them, repeats allowed. Step C: run it as a background job with the live panel.
 */
export function StoryGenerate({ sb }: { sb: SB }) {
  const [pick, setPick] = useState<string | null>(null); // a story id, or 'new'
  const [lenses, setLenses] = useState<string[]>([]);
  const [job, setJob] = useState<{ submitUrl: string; submitBody: object; title: string } | null>(null);
  const full = sb.stories.length >= sb.cap;
  const story = sb.stories.find(s => s.id === pick);
  const wordings = story ? sb.bullets.filter(b => b.storyId === story.id && isLive(b)) : [];

  function choose(id: string) {
    setPick(id);
    setLenses([]);
  }

  function start() {
    if (pick === 'new') setJob({ ...sb.newStoriesJob(), title: 'FINDING STORIES' });
    else if (story && lenses.length > 0) setJob({ ...sb.wordingsJob(story.id, lenses), title: 'WRITING WORDINGS' });
  }

  return (
    <div className="stack-sm">
      <div className="label">A · PICK A STORY</div>
      <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
        <button type="button" className="btn btn--sm" aria-pressed={pick === 'new'} disabled={full}
          onClick={() => choose('new')} title={full ? `The bank holds ${sb.cap} stories. Trash a story's wordings to make room.` : undefined}
          style={{ background: pick === 'new' ? 'var(--ink)' : 'var(--paper)', color: pick === 'new' ? 'var(--paper)' : 'var(--ink)' }}>
          New stories
        </button>
        {sb.stories.map(s => (
          <button key={s.id} type="button" className="btn btn--sm" aria-pressed={pick === s.id} onClick={() => choose(s.id)}
            style={{ background: pick === s.id ? 'var(--ink)' : 'var(--paper)', color: pick === s.id ? 'var(--paper)' : 'var(--ink)' }}>
            {s.title}
          </button>
        ))}
      </div>
      {full && <div className="label muted">The bank is full at {sb.cap} stories.</div>}

      {story && (
        <>
          <div className="label">B · PICK LENSES</div>
          <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
            {CATEGORIES.map(c => {
              const ch = lensChoice(story, wordings, c.slug);
              const n = lenses.filter(x => x === c.slug).length;
              return (
                <button key={c.slug} type="button" className="btn btn--sm" title={c.blurb} onClick={() => setLenses(ls => [...ls, c.slug])}>
                  {c.label}
                  {ch.count > 0 && <span className="muted"> · has {ch.count}</span>}
                  {ch.weakFit && <span className="muted"> · weak fit</span>}
                  {n > 0 && <span> +{n}</span>}
                </button>
              );
            })}
          </div>
          {lenses.length > 0 && (
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
              {lenses.map((slug, i) => (
                <button key={i} type="button" className="kw" onClick={() => setLenses(ls => ls.filter((_, j) => j !== i))}>
                  {lensLabel(slug)} ×
                </button>
              ))}
              <button type="button" className="minibtn" onClick={() => setLenses([])}>Clear</button>
            </div>
          )}
        </>
      )}

      <div className="label">C · GENERATE</div>
      <div>
        <button type="button" className="btn btn--acid btn--sm" disabled={!pick || (pick !== 'new' && lenses.length === 0) || !!job}
          onClick={start}>
          {pick === 'new' ? 'Find new stories' : story ? `Write ${lenses.length || ''} wording${lenses.length === 1 ? '' : 's'}` : 'Generate'}
        </button>
      </div>

      {job && (
        <EventStream
          submitUrl={job.submitUrl}
          submitBody={job.submitBody}
          pollUrl={sb.pollUrl}
          onDone={() => { setJob(null); setLenses([]); sb.load(); }}
          onClose={() => setJob(null)}
          title={`${job.title}…`}
          doneLabel=""
        />
      )}
    </div>
  );
}
