import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useInView } from 'framer-motion';
import { api, type JobList, type JobPosting } from '../lib/api';
import { HeroAssembly } from '../components/landing/HeroAssembly';
import { HowItWorks } from '../components/landing/HowItWorks';
import { RerankPanel } from '../components/landing/RerankPanel';
import { Ticker } from '../components/landing/Ticker';
import { SectionMark, SiteFooter, TopNav } from '../components/landing/SiteNav';
import { AVG_SEC } from '../components/landing/heroData';
import '../styles/landing.css';
import '../components/landing/hero.css';
import '../components/landing/landing-page.css';

interface PublicStats {
  avgPipelineDurationSec: number | null;
  sampleSize: number | null;
}

const VERSUS: [string, string, string][] = [
  ['Your work', 'Re-pasted every session', 'Saved once, reused for every job'],
  ['Choosing bullets', 'Whatever the prompt keeps', 'Every bullet scored against the job'],
  ['ATS keywords', 'You guess', 'Matched and missing, before you apply'],
  ['Output', 'Text to fix up in Word', 'A finished one-page PDF'],
];

const FEED_CARDS = 4;
const TICKER_ITEMS = 12;

/**
 * `How` swaps the "How it works" body; the live site uses the default. Lab prototypes
 * pass alternatives to compare in place.
 */
export function Landing({ How = HowItWorks }: { How?: React.ComponentType }) {
  const [avgSec, setAvgSec] = useState(AVG_SEC);
  const [jobs, setJobs] = useState<JobPosting[]>([]);

  // Both are best-effort: the page renders with the fixed claim and no jobs on failure.
  useEffect(() => {
    let alive = true;
    api.get<PublicStats>('/api/public/stats')
      .then((s) => { if (alive && s.avgPipelineDurationSec != null) setAvgSec(s.avgPipelineDurationSec); })
      .catch(() => {});
    api.get<JobList>(`/api/public/jobs?page=0&size=${TICKER_ITEMS}`)
      .then((r) => { if (alive) setJobs(r.jobs); })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  const cards = jobs.slice(0, FEED_CARDS);
  const ticker = jobs.filter((j) => j.title).map((j) => (j.company ? `${j.title} · ${j.company}` : j.title!));
  const secs = Math.round(avgSec);

  return (
    <div className="lp-root lx">
      <TopNav />
      <HeroAssembly avgSec={avgSec} />

      {/* ── HOW IT WORKS ── */}
      <section className="shell lx-section">
        <SectionMark title="How it works" aside="4 STEPS" />
        <How />
      </section>

      {/* ── ONE BANK, MANY JOBS ── */}
      <section className="shell lx-section">
        <SectionMark title="One bank, many jobs" />
        <div className="lx-split">
          <div className="lx-split__copy">
            <h3 className="lp-display lx-h2">Same bank.<br />Different job.<br />Different page.</h3>
            <p className="lx-body">
              Your work goes in once. Each job re-ranks all of it, so the data role gets your
              Kafka pipeline and the web role gets your design-system migration, without you
              rewriting a line.
            </p>
            <p className="lp-label lp-muted lx-hint">↳ SWITCH THE JOB TAB TO RE-RANK</p>
          </div>
          <RerankPanel />
        </div>
      </section>

      {/* ── WHY ANVIL ── */}
      <section className="shell lx-section">
        <SectionMark title="Why Anvil" aside="NOT JUST A PROMPT" />
        <div className="lx-vs">
          <div className="lx-vs__row lx-vs__row--head">
            <span />
            <span className="lp-display lx-vs__brand">Anvil <span>//</span> CV</span>
            <span className="lp-label lp-muted">CHATGPT</span>
          </div>
          {VERSUS.map(([what, them, us], i) => (
            <Reveal key={what} className="lx-vs__row" delay={i * 0.06}>
              <span className="lp-label">{what}</span>
              <span className="lx-vs__us">{us}</span>
              <span className="lx-vs__them">{them}</span>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ── HIRING NOW: live from the job feed; hidden when the feed is empty ── */}
      {cards.length > 0 && (
        <section className="shell lx-section">
          <SectionMark title="Hiring now" aside="FROM THE JOB FEED" />
          <div className="lx-jobs">
            {cards.map((j) => (
              <Link key={j.id} to="/jobs" className="lx-job">
                <span className="lp-label lp-muted lx-job__meta">
                  {(j.company ?? 'Company').toUpperCase()}{j.posted ? ` · ${j.posted}` : ''}
                </span>
                <span className="lp-display lx-job__role">{j.title ?? j.role ?? 'Open role'}</span>
                {j.location && <span className="lx-job__where">{j.location}</span>}
                {j.stack.length > 0 && (
                  <span className="lx-job__tags">
                    {j.stack.slice(0, 3).map((t) => <span key={t} className="lp-tag">{t}</span>)}
                  </span>
                )}
                <span className="lx-job__cta">OPEN IN JOB FEED →</span>
              </Link>
            ))}
          </div>
          <Link to="/jobs" className="hx-link lx-more">SEE ALL JOBS →</Link>
        </section>
      )}

      {/* ── PRICING NUDGE ── */}
      <section className="shell lx-section lx-section--tight">
        <div className="lx-price">
          <span className="lp-display lx-price__big">$0</span>
          <div className="lx-price__copy">
            <p><strong>Free to start.</strong> Pro is $14/mo for 150 tailored PDFs a month.</p>
            <p className="lx-price__alt">Technical? Stay free forever with your own AI key.</p>
          </div>
          <Link to="/pricing" className="lp-btn lp-btn--ink lx-btn">SEE PRICING →</Link>
        </div>
      </section>

      <Ticker items={ticker} />

      {/* ── CTA BAND ── */}
      <section className="lp-cta-band">
        <div className="shell lp-cta-band__inner">
          <p className="lp-display lp-cta-band__heading">Your résumé.<br />Every job.<br />~{secs} seconds.</p>
          <Link to="/login" className="lp-btn lp-btn--acid lx-btn">OPEN ANVIL &nbsp;→</Link>
        </div>
      </section>

      <SiteFooter />
    </div>
  );
}

/** Fades content up once as it scrolls into view. Reduced motion is handled in CSS. */
function Reveal({ children, className, delay = 0 }: { children: React.ReactNode; className?: string; delay?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const seen = useInView(ref, { once: true, margin: '0px 0px -80px 0px' });
  return (
    <div
      ref={ref}
      className={`lx-reveal ${className ?? ''}`}
      data-seen={seen || undefined}
      style={{ transitionDelay: `${delay}s` }}
    >
      {children}
    </div>
  );
}
