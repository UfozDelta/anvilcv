import { Link } from 'react-router-dom';

/** Public-site nav for the landing and pricing pages (the app uses AppNav). */
export function TopNav() {
  return (
    <header className="lx-nav">
      <div className="shell lx-nav__inner">
        <Link to="/" className="lx-nav__brand">ANVIL <span>//</span> CV</Link>
        <nav className="lx-nav__links">
          <Link to="/jobs">JOBS</Link>
          <Link to="/pricing">PRICING</Link>
          <Link to="/login" className="lx-nav__login">LOG IN</Link>
        </nav>
      </div>
    </header>
  );
}

/** Section label is the real <h2>; the aside is plain text, never arrow-styled. */
export function SectionMark({ title, aside }: { title: string; aside?: string }) {
  return (
    <div className="lp-section-mark">
      <h2 className="lp-section-title lx-mark">{title}</h2>
      <div className="lp-section-rule" />
      {aside && <span className="lp-section-title lp-muted">{aside}</span>}
    </div>
  );
}

export function SiteFooter() {
  return (
    <footer className="lp-footer shell">
      <span className="lp-label lp-muted">ANVIL // CV</span>
      <nav className="lp-footer__links">
        <Link to="/login" className="lp-footer__link">GET STARTED</Link>
        <span className="lp-footer__sep">·</span>
        <Link to="/jobs" className="lp-footer__link">JOBS</Link>
        <span className="lp-footer__sep">·</span>
        <Link to="/pricing" className="lp-footer__link">PRICING</Link>
        <span className="lp-footer__sep">·</span>
        <Link to="/docs" className="lp-footer__link">DOCS</Link>
      </nav>
    </footer>
  );
}
