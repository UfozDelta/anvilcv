import { Link } from 'react-router-dom';
import { StoryHero } from './StoryHero';
import { JobSwitch } from './JobSwitch';
import { FEED } from '../../components/landing/storyData';
import '../../styles/landing.css';
import './landing-v2.css';
import '../../components/landing/steps.css';
import './switch.css';

// Landing page v2. Placeholder data, no API: the real page would feed "Hiring now"
// from the job feed. Flow: hero + how it works (one pinned page) → same bank, any
// job → hiring now → start (pricing folded in).
export function LabLandingV2() {
  return (
    <div className="lp-root lv">
      <Nav />
      <StoryHero />

      <section className="shell lv-section" aria-labelledby="lv-switch-h">
        <div className="lv-head">
          <h2 id="lv-switch-h" className="lp-display lv-h2">Same bank.<br />Different job.<br />Different page.</h2>
          <p className="lv-body">
            Nothing is rewritten. Each job re-ranks the whole bank, so the data role gets your
            Kafka pipeline and the web role gets your design-system migration. Switch the job.
          </p>
        </div>
        <JobSwitch />
      </section>

      <section className="shell lv-section" aria-labelledby="lv-feed-h">
        <div className="lv-head lv-head--row">
          <h2 id="lv-feed-h" className="lp-display lv-h2">Hiring now.</h2>
          <p className="lv-body">A public feed of live roles. Open one and tailor straight from the post, no copy-paste.</p>
        </div>
        <ul className="lv-feed">
          {FEED.map((j) => (
            <li key={j.role}>
              <Link to="/jobs" className="lv-feed__row">
                <span className="lv-feed__co">{j.company}</span>
                <span className="lp-display lv-feed__role">{j.role}</span>
                <span className="lv-feed__where">{j.where}</span>
                <span className="lv-feed__tags">{j.tags.map((t) => <span key={t} className="lp-tag">{t}</span>)}</span>
                <span className="lv-feed__age">{j.age}</span>
                <span className="lv-feed__go" aria-hidden="true">→</span>
              </Link>
            </li>
          ))}
        </ul>
        <Link to="/jobs" className="lv-link lv-more">SEE ALL JOBS →</Link>
      </section>

      <section className="lv-end">
        <div className="shell lv-end__inner">
          <p className="lp-display lv-end__heading">Your résumé.<br />Every job.<br />~17 seconds.</p>
          <div className="lv-end__price">
            <p className="lv-end__big"><span className="lp-display">$0</span> to start.</p>
            <p className="lv-end__copy">Pro is $9/mo for unlimited PDFs. Bring your own AI key and stay free for good.</p>
            <div className="lv-end__ctas">
              <Link to="/login" className="lp-btn lp-btn--acid lv-btn">OPEN ANVIL &nbsp;→</Link>
              <Link to="/pricing" className="lv-link lv-link--dark">SEE PRICING →</Link>
            </div>
          </div>
        </div>
      </section>

      <footer className="lp-footer shell">
        <span className="lp-label lp-muted">ANVIL // CV</span>
        <nav className="lp-footer__links">
          <Link to="/jobs" className="lp-footer__link">JOBS</Link>
          <span className="lp-footer__sep">·</span>
          <Link to="/pricing" className="lp-footer__link">PRICING</Link>
          <span className="lp-footer__sep">·</span>
          <Link to="/docs" className="lp-footer__link">DOCS</Link>
        </nav>
      </footer>
    </div>
  );
}

function Nav() {
  return (
    <header className="lv-nav">
      <div className="shell lv-nav__inner">
        <Link to="/" className="lv-nav__brand">ANVIL <span>//</span> CV</Link>
        <nav className="lv-nav__links">
          <Link to="/jobs">JOBS</Link>
          <Link to="/pricing">PRICING</Link>
          <Link to="/docs" className="lv-nav__docs">DOCS</Link>
          <Link to="/login" className="lv-nav__login">LOG IN</Link>
        </nav>
      </div>
    </header>
  );
}
