import { Link } from 'react-router-dom';
import { AVG_SEC, PIPELINE, RUNS, TICKER_ROLES } from '../../components/landing/heroData';
import { useCountUp, useHeroLoop } from '../../components/landing/useHeroLoop';
import { Ticker } from '../../components/landing/Ticker';
import './broadsheet.css';

// 0 reset · 1–4 light each stage · 5 hold
const DURATIONS = [500, 1000, 1000, 1000, 1000, 3200];

export function HeroBroadsheet() {
  const { ref, step } = useHeroLoop<HTMLDivElement>(DURATIONS);
  const today = new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });

  return (
    <section className="lp-hero hx hx-bs">
      <div className="shell">
        <div className="hx-bs__dateline">
          <span className="lp-label">VOL. 01 · NO. {RUNS}</span>
          <span className="lp-label lp-muted hx-bs__center">AI RÉSUMÉ TAILORING</span>
          <span className="lp-label lp-muted">{today.toUpperCase()}</span>
        </div>

        <h1 className="lp-display hx-bs__title">
          Anvil<span className="lp-hero__slash hx-bs__slash"> // </span>CV
        </h1>

        <div className="hx-bs__deck">
          <p className="lp-editorial hx-bs__sub">
            Paste a job description. Get a tailored résumé PDF in under thirty seconds —
            ranked from a bullet bank that remembers everything you've built.
          </p>
          <div className="hx-ctas">
            <Link to="/login" className="lp-btn lp-btn--acid hx-btn">GET STARTED &nbsp;→</Link>
            <Link to="/jobs" className="hx-link">BROWSE JOBS →</Link>
          </div>
        </div>

        <div className="hx-bs__strip" ref={ref}>
          {PIPELINE.map((p, i) => (
            <StageCol key={p.key} stage={p} index={i} on={step >= i + 1} />
          ))}
          <span
            className="hx-bs__sweep"
            aria-hidden="true"
            style={{
              transform: `scaleX(${Math.min(step, 4) / 4})`,
              transition: step === 0 ? 'none' : undefined,
            }}
          />
        </div>
        <p className="lp-label lp-muted hx-bs__foot">LIVE AVERAGE {AVG_SEC}s ACROSS {RUNS} RUNS</p>
      </div>

      <Ticker items={TICKER_ROLES} />
    </section>
  );
}

function StageCol({ stage, index, on }: { stage: (typeof PIPELINE)[number]; index: number; on: boolean }) {
  const decimals = stage.value.includes('.') ? 1 : 0;
  const val = useCountUp(parseFloat(stage.value), on, 650, decimals);
  return (
    <div className="hx-bs__col" data-on={on || undefined}>
      <span className="lp-label hx-bs__key">0{index + 1} · {stage.key}</span>
      <span className="hx-bs__num">
        {val}<small>{stage.unit}</small>
      </span>
      <span className="hx-bs__cap">{stage.caption}</span>
    </div>
  );
}
