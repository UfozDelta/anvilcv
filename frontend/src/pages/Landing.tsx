import { Link, NavLink } from 'react-router-dom';
import '../styles/landing.css';

const STEPS = [
  {
    num: '01',
    title: 'Build your\nbullet bank',
    body: 'Add projects and experiences. Paste a description — AI writes 6–12 bullets per entry. Edit freely.',
    tag: 'AI GENERATION',
  },
  {
    num: '02',
    title: 'Paste the\njob posting',
    body: 'Drop in raw JD text or a URL. AI cleans it, extracts company, role, and keywords. Takes two seconds.',
    tag: 'JD PARSING',
  },
  {
    num: '03',
    title: 'AI ranks\nevery bullet',
    body: 'Every bullet scored against the JD. Top 8 auto-selected (max 3 per project). Override anything. See exactly why each bullet ranked.',
    tag: 'RANKED MATCHING',
  },
  {
    num: '04',
    title: 'One-click\nPDF output',
    body: 'LaTeX template → compiler → production-ready PDF. Cover letter included.',
    tag: 'LATEX + PDF',
  },
];

export function Landing() {
  return (
    <div className="lp-root">

      {/* ── HERO ── */}
      <section className="lp-hero shell" style={{ paddingTop: 56 }}>
        <div className="lp-hero__eyebrow">
          <span className="lp-label">ANVIL CV</span>
          <div className="lp-hero__rule" />
          <span className="lp-label lp-muted">AI RESUME TAILORING</span>
        </div>

        <div className="lp-hero__grid">
          <div className="lp-hero__left">
            <h1 className="lp-display lp-hero__heading">
              Anvil<br />
              <span className="lp-hero__slash">// </span>
              CV
            </h1>
            <p className="lp-editorial lp-hero__sub">
              Paste a job description.<br />
              Get a tailored résumé PDF<br />
              in under thirty seconds.
            </p>
            <div className="lp-hero__cta-row">
              <Link to="/login" className="lp-btn lp-btn--acid">
                GET STARTED &nbsp;→
              </Link>
            </div>
          </div>

          <div className="lp-hero__right">
            <div className="lp-hero__terminal">
              <div className="lp-hero__terminal-bar">
                <span />
                <span />
                <span />
                <span className="lp-hero__terminal-title">anvilcv — tectonic</span>
              </div>
              <pre className="lp-hero__terminal-body">{`$ POST /api/applications?includePdf=true
  jdText: "We're hiring a backend engineer..."
  roleEmphasis: "distributed systems"

← 200 OK  (17.2s)
  company:     "Acme Corp"
  role:        "Senior Engineer"
  bullets:     8 selected / 34 ranked
  atsMatched:  ["Kubernetes","gRPC","Postgres"]
  atsMissing:  ["Terraform"]
  pdfBase64:   264 KB  ✓`}</pre>
            </div>
          </div>
        </div>
      </section>

      {/* ── HOW IT WORKS ── */}
      <section className="lp-steps shell">
        <div className="lp-section-mark">
          <span className="lp-section-title">HOW IT WORKS</span>
          <div className="lp-section-rule" />
        </div>

        <div className="lp-steps__grid">
          {STEPS.map((s) => (
            <div key={s.num} className="lp-step">
              <div className="lp-step__num">{s.num}</div>
              <h3 className="lp-display lp-step__title">{s.title}</h3>
              <p className="lp-step__body">{s.body}</p>
              <div className="lp-tag">{s.tag}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ── CTA BAND ── */}
      <section className="lp-cta-band">
        <div className="shell lp-cta-band__inner">
          <p className="lp-display lp-cta-band__heading">
            Your résumé.<br />Every job.<br />In seconds.
          </p>
          <Link to="/login" className="lp-btn lp-btn--ink">
            OPEN ANVIL &nbsp;→
          </Link>
        </div>
      </section>

      {/* ── FOOTER ── */}
      <footer className="lp-footer shell">
        <span className="lp-label lp-muted">ANVIL CV</span>
        <nav className="lp-footer__links">
          <NavLink to="/login" className="lp-footer__link">GET STARTED</NavLink>
          <span className="lp-footer__sep">·</span>
          <NavLink to="/docs"  className="lp-footer__link">DOCS</NavLink>
        </nav>
        <span className="lp-label lp-muted">SPRING BOOT · REACT · NEON · TECTONIC</span>
      </footer>

    </div>
  );
}
