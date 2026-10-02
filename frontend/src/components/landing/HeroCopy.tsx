import { Link } from 'react-router-dom';
import { AVG_SEC } from './heroData';

/** Left column shared by the split variants: eyebrow, Anvil // CV, sub, live stat, CTAs. */
/** `avgSec` is the live JD → PDF average when known; falls back to the fixed claim. */
export function HeroCopy({ avgSec = AVG_SEC }: { avgSec?: number }) {
  return (
    <div className="hx-copy">
      <h1 className="lp-display hx-title">
        Anvil<br />
        <span className="lp-hero__slash">// </span>CV
      </h1>
      <p className="lp-editorial hx-sub">
        Paste a job description.<br />
        Get a tailored one-page résumé,<br />
        cover letter included.
      </p>
      <div className="hx-stat">
        <span className="hx-stat__num">~{Math.round(avgSec)}s</span>
        <span className="lp-label lp-muted">from job post to PDF</span>
      </div>
      <div className="hx-ctas">
        <Link to="/login" className="lp-btn lp-btn--acid hx-btn">GET STARTED &nbsp;→</Link>
        <Link to="/jobs" className="hx-link">BROWSE JOBS →</Link>
      </div>
    </div>
  );
}

export function HeroEyebrow() {
  return (
    <div className="lp-hero__eyebrow">
      <span className="lp-label">ANVIL CV</span>
      <div className="lp-hero__rule" />
      <span className="lp-label lp-muted">AI RESUME TAILORING</span>
    </div>
  );
}
