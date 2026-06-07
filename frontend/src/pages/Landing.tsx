import { useEffect, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { api } from '../lib/api';
import '../styles/landing.css';

interface PublicStats {
  avgPipelineDurationSec: number | null;
  sampleSize: number | null;
}

// Fallback shown instantly; live value overwrites once /api/public/stats resolves.
const DEFAULT_AVG_SEC = 17;

const STEPS = [
  {
    num: '01',
    title: 'Build your\nbullet bank',
    body: 'Add projects and experiences. Paste a description. AI writes 6 to 12 bullets per entry. Edit freely.',
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

const FEATURES = [
  {
    icon: '[01]',
    title: 'NOT A CHATBOT',
    body: 'ChatGPT forgets your work between sessions. Anvil keeps a permanent bullet bank. Write once, reuse across every application.',
  },
  {
    icon: '[02]',
    title: 'ATS-AWARE',
    body: 'Every Job Description parsed for keywords. The pipeline tells you which terms matched and which are missing before you apply. No guessing.',
  },
  {
    icon: '[03]',
    title: 'ONE BANK, MANY JOBS',
    body: 'Tailor the same experience to a backend role and a data role in minutes. The bank stays; only the selection changes.',
  },
];

export function Landing() {
  const [avgSec, setAvgSec] = useState<number>(DEFAULT_AVG_SEC);
  const [sampleSize, setSampleSize] = useState<number | null>(null);

  useEffect(() => {
    let alive = true;
    api.get<PublicStats>('/api/public/stats')
      .then((s) => {
        if (!alive) return;
        if (s.avgPipelineDurationSec != null) setAvgSec(s.avgPipelineDurationSec);
        setSampleSize(s.sampleSize);
      })
      .catch(() => { /* keep default on any failure — no error UI on landing */ });
    return () => { alive = false; };
  }, []);

  // round to whole seconds for the headline stat
  const avgDisplay = Math.round(avgSec);

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
                <span className="lp-hero__terminal-title">anvilcv : tectonic</span>
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

      {/* ── PROOF: SAMPLE OUTPUT ── */}
      <section className="lp-proof shell">
        <div className="lp-section-mark">
          <span className="lp-section-title">SAMPLE OUTPUT</span>
          <div className="lp-section-rule" />
          <span className="lp-section-title lp-muted">ONE JD → ONE PDF</span>
        </div>

        <div className="lp-proof__grid">
          <div className="lp-proof__copy">
            <h2 className="lp-display lp-proof__heading">
              See the<br />actual page.
            </h2>
            <p className="lp-proof__body">
              Not a mockup. Every run produces a real, compiled PDF.
              Ranked bullets, matched keywords, single-page fit.
            </p>
            <div className="lp-proof__stats">
              <div className="lp-proof__stat">
                <span className="lp-proof__stat-num">8</span>
                <span className="lp-proof__stat-label">bullets selected</span>
              </div>
              <div className="lp-proof__stat">
                <span className="lp-proof__stat-num">34</span>
                <span className="lp-proof__stat-label">bullets ranked</span>
              </div>
              <div className="lp-proof__stat">
                <span className="lp-proof__stat-num">{avgDisplay}s</span>
                <span className="lp-proof__stat-label">
                  avg JD → PDF
                  {sampleSize != null && ` · ${sampleSize} runs`}
                </span>
              </div>
            </div>
            <div className="lp-proof__ats">
              <span className="lp-tag lp-tag--acid">KUBERNETES ✓</span>
              <span className="lp-tag lp-tag--acid">GRPC ✓</span>
              <span className="lp-tag lp-tag--acid">POSTGRES ✓</span>
              <span className="lp-tag lp-tag--miss">TERRAFORM : MISSING</span>
            </div>
          </div>

          {/* CSS-rendered résumé page mock */}
          <div className="lp-proof__paper" aria-label="Sample résumé page preview">
            <div className="lp-doc">
              <div className="lp-doc__name">JORDAN REYES</div>
              <div className="lp-doc__contact">SENIOR ENGINEER · BACKEND · DISTRIBUTED SYSTEMS</div>
              <div className="lp-doc__rule" />
              <div className="lp-doc__section">EXPERIENCE</div>
              <div className="lp-doc__line lp-doc__line--w90" />
              <div className="lp-doc__line lp-doc__line--w70" />
              <div className="lp-doc__bullet"><span /><div className="lp-doc__line lp-doc__line--w85" /></div>
              <div className="lp-doc__bullet"><span /><div className="lp-doc__line lp-doc__line--w95" /></div>
              <div className="lp-doc__bullet"><span /><div className="lp-doc__line lp-doc__line--w60" /></div>
              <div className="lp-doc__section">PROJECTS</div>
              <div className="lp-doc__line lp-doc__line--w80" />
              <div className="lp-doc__bullet"><span /><div className="lp-doc__line lp-doc__line--w90" /></div>
              <div className="lp-doc__bullet"><span /><div className="lp-doc__line lp-doc__line--w75" /></div>
              <div className="lp-doc__section">SKILLS</div>
              <div className="lp-doc__line lp-doc__line--w95" />
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

      {/* ── WHY ANVIL ── */}
      <section className="lp-features shell">
        <div className="lp-section-mark">
          <span className="lp-section-title">WHY ANVIL</span>
          <div className="lp-section-rule" />
          <span className="lp-section-title lp-muted">NOT JUST A PROMPT</span>
        </div>

        <div className="lp-features__grid">
          {FEATURES.map((f) => (
            <div key={f.title} className="lp-feature">
              <span className="lp-feature__icon">{f.icon}</span>
              <h3 className="lp-feature__title">{f.title}</h3>
              <p className="lp-feature__body">{f.body}</p>
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
