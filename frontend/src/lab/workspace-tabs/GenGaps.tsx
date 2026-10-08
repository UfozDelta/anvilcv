/* G3 · Gaps: thin lenses and uncovered repo areas, each with a one-click fill; the result lands on its row. */
import { useState } from 'react';
import { LENSES } from '../workspace/data';
import { RunChips, Spin } from '../workspace/parts';
import { AREAS, THIN, type Term } from './data';
import { fitsOf, storyOf, type WS } from './model';
import { LENS } from './bullets';

type P = { ws: WS; term: Term; toBullets: () => void };

export function GenGaps({ ws, term, toBullets }: P) {
  const [last, setLast] = useState<string | null>(null);
  const run = (key: string, fn: () => void) => { setLast(key); fn(); };

  const lensRows = LENSES.map(l => {
    const n = ws.count(l.slug);
    const approved = ws.bullets.filter(b => b.lens === l.slug && b.status === 'APPROVED').length;
    const open = ws.open[l.slug];
    const fresh = ws.pool().find(s => s.fits.includes(l.slug));
    return { l, n, approved, open, fresh, gap: n < THIN };
  });
  const areaRows = AREAS.map(a => {
    const have = a.items.filter(id => ws.inBank(id));
    const left = ws.pool(a.items);
    return { a, have, left, gap: have.length === 0 };
  });
  const gaps = lensRows.filter(r => r.gap).length + areaRows.filter(r => r.gap).length;
  const max = Math.max(THIN + 1, ...lensRows.map(r => r.n));

  const result = (key: string) => last === key && ws.run && (
    <div className="wt-result" aria-live="polite">
      {ws.run.live ? <Spin /> : <RunChips run={ws.run} />}
      {ws.run.stories.map(id => <span key={id} className="wt-result__t">{storyOf(id)!.glyph} {storyOf(id)!.title}</span>)}
      {!ws.run.live && !ws.run.full && <button className="minibtn" onClick={toBullets}>Bullets</button>}
    </div>
  );

  return (
    <div className="wt-gaps">
      <div className="wt-gaps__sum" title={`Lenses under ${THIN} bullets, areas with no ${term.one.toLowerCase()}`}>
        <span className="wt-gaps__n" data-zero={gaps === 0 || undefined}>{gaps}</span>
        <span className="wt-k">{gaps === 1 ? 'Gap' : 'Gaps'}</span>
        <span className="wt-room" data-full={ws.room === 0 || undefined} title={`Room for ${ws.room} more ${term.many.toLowerCase()}`}>{ws.room} left</span>
      </div>

      <div className="wt-gaps__cols">
        <section aria-label="Lenses">
          <div className="wt-bar"><span className="wt-k">Lenses</span></div>
          {lensRows.map(({ l, n, approved, open, fresh, gap }) => {
            const key = `l:${l.slug}`;
            const act = open > 0
              ? { label: `+${open}`, tip: `Write ${l.short} for ${open} ${open === 1 ? term.one : term.many}`.toLowerCase(), go: () => ws.fill({ lenses: [l.slug] }) }
              : fresh ? { label: `+${term.one}`, tip: `${fresh.title} · ${l.short}`, go: () => ws.take([{ id: fresh.id, lenses: [l.slug] }]) } : null;
            return (
              <div key={l.slug} className="wt-gap" data-gap={gap || undefined}>
                <div className="wt-gap__row">
                  <span className="wt-gap__name" title={LENS[l.slug]?.blurb}>{l.short}</span>
                  <span className="wt-meter" title={`${n} bullets · ${approved} approved`} aria-label={`${n} bullets`}>
                    {Array.from({ length: max }, (_, i) => (
                      <i key={i} data-k={i < approved ? 'ok' : i < n ? 'on' : i < THIN ? 'need' : undefined} />
                    ))}
                  </span>
                  <span className="wt-gap__n">{n}</span>
                  {act && (gap || open > 0)
                    ? <button className={`btn btn--sm wt-fill${gap ? ' btn--acid' : ''}`} disabled={ws.busy} title={act.tip} onClick={() => run(key, act.go)}>{act.label}</button>
                    : <span className="wt-fill wt-fill--none" aria-hidden="true" />}
                </div>
                {result(key)}
              </div>
            );
          })}
        </section>

        <section aria-label="Repo areas">
          <div className="wt-bar"><span className="wt-k">Areas</span></div>
          {areaRows.map(({ a, have, left, gap }) => {
            const key = `a:${a.name}`;
            const next = left[0];
            return (
              <div key={a.name} className="wt-gap" data-gap={gap || undefined}>
                <div className="wt-gap__row">
                  <span className="wt-gap__name" title={`${a.purpose}\n${a.modules.join(', ')} · ${a.files} files`}>{a.name}</span>
                  <span className="wt-gap__have" title={have.map(id => storyOf(id)!.title).join('\n') || `No ${term.one.toLowerCase()}`}>
                    {have.length ? have.map(id => storyOf(id)!.glyph).join(' ') : '—'}
                  </span>
                  <span className="wt-gap__n">{have.length}</span>
                  {next
                    ? <button className={`btn btn--sm wt-fill${gap ? ' btn--acid' : ''}`} disabled={ws.busy || ws.room === 0}
                        title={ws.room === 0 ? 'Bank full' : `${next.title} · ${fitsOf(next.id).length} lenses · ${left.length} left`} onClick={() => run(key, () => ws.take([{ id: next.id, lenses: fitsOf(next.id) }]))}>
                        +1
                      </button>
                    : <span className="wt-fill wt-fill--none" aria-hidden="true" />}
                </div>
                {result(key)}
              </div>
            );
          })}
        </section>
      </div>
    </div>
  );
}
