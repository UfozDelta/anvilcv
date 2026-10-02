import { useEffect, useState } from 'react';
import { HeroCopy } from './HeroCopy';
import { ASSEMBLY_PICKS, BULLETS, JD_TEXT, SAMPLE_JDS, SAMPLE_PDF_URL } from './heroData';
import { useCountUp, useHeroLoop } from './useHeroLoop';

// 0 reset · 1 type JD · 2 parse · 3 rank · 4 select · 5 compile · 6 hold
const DURATIONS = [400, 2400, 1000, 1100, 1500, 900, 3600];
const STAGES = ['JD', 'PARSE', 'RANK', 'SELECT', 'PDF'];
const JD = SAMPLE_JDS[0];

export function HeroAssembly({ avgSec }: { avgSec?: number }) {
  // Plays once and parks on the finished PDF; looping would keep wiping the page next to the copy.
  const { ref, step, reduced, replay } = useHeroLoop<HTMLElement>(DURATIONS, true);
  const typed = useTyped(JD_TEXT, step === 1, step >= 2 || reduced);
  const ranked = useCountUp(34, step >= 3, 800);
  const stageIdx = Math.max(0, Math.min(step - 1, 4));
  const picks = ASSEMBLY_PICKS.map((id) => BULLETS.find((b) => b.id === id)!);

  return (
    <section className="lp-hero shell hx">
      <div className="lp-hero__grid hx-grid">
        <HeroCopy avgSec={avgSec} />

        <figure className="hx-asm" ref={ref} aria-label="Demo: a job description becoming a tailored one-page résumé">
          <ol className="hx-rail">
            {STAGES.map((s, i) => (
              <li key={s} data-state={step === 0 ? 'idle' : i < stageIdx ? 'done' : i === stageIdx ? 'on' : 'idle'}>
                {s}
              </li>
            ))}
          </ol>

          <div className="hx-jd">
            <span className="hx-jd__prompt">$ paste</span>
            {/* Invisible full text reserves the height, so the page below never shifts while typing. */}
            <p className="hx-jd__text">
              <span className="hx-jd__ghost">{JD_TEXT}</span>
              <span className="hx-jd__typed">
                {typed}
                {step === 1 && <span className="hx-caret" aria-hidden="true">▮</span>}
              </span>
            </p>
            <div className="hx-jd__parsed" data-on={step >= 2 || undefined}>
              <span className="lp-tag">{JD.company.toUpperCase()}</span>
              <span className="lp-tag">{JD.role.toUpperCase()}</span>
              <span className="lp-label lp-muted hx-jd__rank">{ranked} / 34 ranked</span>
            </div>
          </div>

          <div className="hx-paper">
            <div className="lp-doc__name">JORDAN REYES</div>
            <div className="lp-doc__contact">{JD.role.toUpperCase()} · BACKEND · DISTRIBUTED SYSTEMS</div>
            <div className="lp-doc__rule" />
            {(['EXPERIENCE', 'PROJECTS'] as const).map((sec) => (
              <div key={sec}>
                <div className="lp-doc__section">{sec}</div>
                {picks.map((b, i) => b.section === sec && (
                  <div key={b.id} className="hx-slot">
                    {step >= 4 ? (
                      <div className="hx-bullet" style={{ animationDelay: `${i * 140}ms` }}>
                        <span />{b.text}
                      </div>
                    ) : (
                      <div className="lp-doc__bullet"><span /><div className="lp-doc__line" style={{ width: `${70 + (i * 7) % 25}%` }} /></div>
                    )}
                  </div>
                ))}
              </div>
            ))}
            <div className="hx-ats" data-on={step >= 5 || undefined}>
              {JD.matched.map((k, i) => (
                <span key={k} className="lp-tag lp-tag--acid" style={{ transitionDelay: `${i * 60}ms` }}>{k} ✓</span>
              ))}
              {JD.missing.map((k) => <span key={k} className="lp-tag lp-tag--miss">{k} : MISSING</span>)}
            </div>
          </div>
          <div className="hx-done" data-on={step >= 6 || undefined}>
            {/* Hidden until a real sample PDF is published (see SAMPLE_PDF_URL). */}
            {SAMPLE_PDF_URL && (
              <a href={SAMPLE_PDF_URL} target="_blank" rel="noreferrer" className="hx-done__pdf">VIEW SAMPLE PDF →</a>
            )}
            {!reduced && (
              <button type="button" className="hx-done__replay" onClick={replay} disabled={step < 6}>↻ REPLAY</button>
            )}
          </div>
        </figure>
      </div>
    </section>
  );
}

function useTyped(text: string, run: boolean, full: boolean) {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (!run) return;
    setN(0);
    const id = window.setInterval(() => setN((c) => (c >= text.length ? c : c + 3)), 30);
    return () => window.clearInterval(id);
  }, [run, text]);
  if (full) return text;
  return run ? text.slice(0, n) : '';
}
