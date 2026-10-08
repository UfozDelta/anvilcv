/* G2 · Guided: Source → Find → Write. */
import { useEffect, useRef, useState } from 'react';
import { REPO_MAP } from '../project-split/data';
import { LENSES } from '../workspace/data';
import { RunChips, Spin } from '../workspace/parts';
import { AREAS, LINE, type Term } from './data';
import { fitsOf, storyOf, textOf, type WS } from './model';
import { LensDots, plain } from './bullets';

type P = { ws: WS; term: Term; toBullets: () => void };
type Src = 'description' | 'repo' | 'bullets';

export function GenGuided({ ws, term, toBullets }: P) {
  const [step, setStep] = useState(0);
  const [src, setSrc] = useState<Set<Src>>(new Set(['description', 'repo', 'bullets']));
  const [finding, setFinding] = useState(false);
  const [found, setFound] = useState<string[]>([]);
  const [verdict, setVerdict] = useState<Record<string, 'yes' | 'no'>>({});
  const [lenses, setLenses] = useState<Record<string, Set<string>>>({});
  const [wrote, setWrote] = useState(false);
  const timer = useRef<number>();
  useEffect(() => () => clearTimeout(timer.current), []);

  const accepted = found.filter(id => verdict[id] === 'yes');
  const files = AREAS.reduce((n, a) => n + a.files, 0);
  const sources: { k: Src; name: string; n: string; tip: string }[] = [
    { k: 'description', name: 'Description', n: `${ws.project.description.length}c`, tip: 'Project description' },
    { k: 'repo', name: 'Repo', n: `${AREAS.length} areas · ${files} files`, tip: `${REPO_MAP.modules.length} modules @ ${REPO_MAP.sha.slice(0, 7)}` },
    { k: 'bullets', name: 'Bullets', n: `${ws.bullets.length}`, tip: 'Skip what you have' },
  ];

  const find = () => {
    setStep(1);
    setFinding(true);
    setVerdict({});
    timer.current = window.setTimeout(() => {
      const ids = ws.pool().map(s => s.id).filter(id => src.has('repo') || !['s-replay', 's-canary'].includes(id));
      setFound(ids);
      setFinding(false);
    }, 800);
  };
  const decide = (id: string, v: 'yes' | 'no') => setVerdict(m => {
    const n = { ...m };
    if (n[id] === v) delete n[id]; else n[id] = v;
    return n;
  });
  const toWrite = () => {
    setLenses(m => Object.fromEntries(accepted.map(id => [id, m[id] ?? new Set(fitsOf(id))])));
    setWrote(false);
    setStep(2);
  };
  const flipLens = (id: string, l: string) => setLenses(m => {
    const n = new Set(m[id]);
    n.has(l) ? n.delete(l) : n.add(l);
    return { ...m, [id]: n };
  });
  const picks = accepted.filter(id => !ws.inBank(id)).map(id => ({ id, lenses: [...(lenses[id] ?? [])] })).filter(p => p.lenses.length > 0);
  const total = picks.reduce((n, p) => n + p.lenses.length, 0);
  const write = () => { setWrote(true); ws.take(picks); };

  const steps = [
    { name: 'Source', n: src.size },
    { name: 'Find', n: found.length ? accepted.length : null },
    { name: 'Write', n: wrote ? accepted.filter(id => ws.inBank(id)).length : null },
  ];
  const canGo = (i: number) => i === 0 || (i === 1 && found.length > 0) || (i === 2 && accepted.length > 0);

  return (
    <div className="wt-guided">
      <ol className="wt-steps" aria-label="Steps">
        {steps.map((s, i) => (
          <li key={s.name}>
            <button className="wt-step" aria-current={step === i ? 'step' : undefined} data-done={i < step || undefined}
              disabled={!canGo(i) || ws.busy} onClick={() => setStep(i)}>
              <span className="wt-step__i">{i + 1}</span>
              <span className="wt-step__name">{s.name}</span>
              {s.n !== null && <span className="wt-step__n">{s.n}</span>}
            </button>
          </li>
        ))}
      </ol>

      {step === 0 && (
        <div className="wt-stage">
          <div className="wt-srcs">
            {sources.map(s => (
              <label key={s.k} className="wt-src" data-on={src.has(s.k) || undefined} title={s.tip}>
                <input type="checkbox" checked={src.has(s.k)}
                  onChange={() => setSrc(cur => { const n = new Set(cur); n.has(s.k) ? n.delete(s.k) : n.add(s.k); return n; })} />
                <span className="ws-lens-opt__box" aria-hidden="true">{src.has(s.k) ? '✓' : ''}</span>
                <span className="wt-src__name">{s.name}</span>
                <span className="wt-src__n">{s.n}</span>
              </label>
            ))}
          </div>
          <div className="wt-next">
            <button className="btn btn--acid ps-gen__btn" disabled={!src.has('description') && !src.has('repo')} onClick={find}>✦ Find {term.many.toLowerCase()}</button>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="wt-stage">
          {finding ? <div className="wt-empty"><Spin /></div> : found.length === 0 ? <div className="wt-empty">Nothing new</div> : (
            <ul className="wt-cards" aria-live="polite">
              {found.map(id => {
                const s = storyOf(id)!;
                const v = verdict[id];
                const full = v !== 'yes' && accepted.length >= ws.room;
                return (
                  <li key={id} className="wt-card" data-v={v}>
                    <div className="wt-card__top">
                      <span className="wt-cand__glyph">{s.glyph}</span>
                      <span className="wt-cand__title">{s.title}</span>
                    </div>
                    <div className="wt-cand__line">{LINE[id]}</div>
                    <div className="wt-card__foot">
                      <LensDots fits={s.fits} />
                      <span className="wt-card__acts">
                        <button className="wt-yn" data-k="no" aria-pressed={v === 'no'} title="Skip" aria-label="Skip" onClick={() => decide(id, 'no')}>✕</button>
                        <button className="wt-yn" data-k="yes" aria-pressed={v === 'yes'} disabled={full} title={full ? 'Bank full' : 'Accept'} aria-label="Accept" onClick={() => decide(id, 'yes')}>✓</button>
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="wt-next">
            <span className="wt-room" data-full={accepted.length >= ws.room || undefined} title={`Room for ${ws.room}`}>{accepted.length} / {ws.room}</span>
            <button className="btn btn--acid ps-gen__btn" disabled={accepted.length === 0} onClick={toWrite}>Next</button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="wt-stage">
          <ul className="wt-writes">
            {accepted.map(id => {
              const s = storyOf(id)!;
              const done = ws.inBank(id);
              return (
                <li key={id} className="wt-write" data-done={done || undefined}>
                  <span className="wt-cand__glyph">{s.glyph}</span>
                  <span className="wt-cand__title" title={s.title}>{s.title}</span>
                  <span className="wt-write__lenses">
                    {LENSES.filter(l => s.fits.includes(l.slug)).map(l => {
                      const on = done ? ws.has(id, l.slug) : !!lenses[id]?.has(l.slug);
                      return (
                        <button key={l.slug} className="wt-lchip" aria-pressed={on} disabled={done || ws.busy}
                          title={plain(textOf(id, l.slug))} onClick={() => flipLens(id, l.slug)}>{l.short}</button>
                      );
                    })}
                  </span>
                  <span className="wt-write__st">{ws.pending.has(id) ? <Spin /> : done ? '✓' : ''}</span>
                </li>
              );
            })}
          </ul>
          <div className="wt-next">
            {wrote && <RunChips run={ws.run} />}
            {wrote && !ws.busy
              ? <button className="btn btn--acid ps-gen__btn" onClick={toBullets}>Bullets <span className="ps-chips__n">+{ws.newIds.size}</span></button>
              : <button className="btn btn--acid ps-gen__btn" disabled={ws.busy || total === 0} onClick={write}>{ws.busy ? <Spin /> : '✦'} Write {total}</button>}
          </div>
        </div>
      )}
    </div>
  );
}
