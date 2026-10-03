import { useState } from 'react';
import { Link } from 'react-router-dom';
import { SiteFooter, TopNav } from '../components/landing/SiteNav';
import { useAvgSec } from '../components/landing/useAvgSec';
import '../styles/landing.css';
import '../components/landing/landing-page.css';
import '../styles/pricing.css';

// Proposed pricing — no billing exists yet. Estimated LLM cost ~$0.02–0.05 per PDF;
// verify against llm_usage_log before shipping.
type Plan = {
  id: string;
  name: string;
  price: number;
  per: string;
  /** Tailored PDFs included: per month for subscriptions, total for the pack. */
  pdfs: number;
  kind: 'monthly' | 'pack';
  /** The plan we sell: always shown dark with the "most popular" badge. */
  featured?: boolean;
  line: string;
  features: string[];
};

// Prices come from one internal base rate (~$0.17 per PDF, never shown) times volume,
// except Pro: discounted to ~$0.09 so the middle plan is the obvious deal.
const PLANS: Plan[] = [
  { id: 'starter', name: 'STARTER', price: 10, per: '/ month', pdfs: 60, kind: 'monthly', line: 'About 2 applications a day.', features: ['60 tailored PDFs a month', '10 repo imports a month', 'Cover letters'] },
  { id: 'pro', name: 'PRO', price: 14, per: '/ month', pdfs: 150, kind: 'monthly', featured: true, line: 'About 5 applications a day.', features: ['150 tailored PDFs a month', '25 repo imports a month', 'Tailor from the job feed', 'Priority queue'] },
  { id: 'pack', name: 'PACK', price: 39, per: 'once', pdfs: 200, kind: 'pack', line: 'Pay once, use whenever.', features: ['200 tailored PDFs', 'Never expire', '20 repo imports'] },
];
const FREE_PDFS = 3;
const MANUAL_MIN = 45; // rough minutes to hand-tailor one résumé
const WEEKS_PER_MONTH = 30 / 7; // a 30-day month, so 5 a day = 150

const compare = (sec: number): [string, string, string][] => [
  ['Your work', 'Saved once, reused for every job', 'Re-pasted every session'],
  ['Choosing bullets', 'Every bullet scored against the job', 'Whatever the prompt keeps'],
  ['ATS keywords', 'Matched and missing, before you apply', 'You guess'],
  ['Time per résumé', `~${sec} seconds`, '20+ minutes'],
];

const FAQ = [
  ['What counts as a PDF?', 'One tailored application, résumé plus cover letter, for one job. Re-generating for the same job is free up to 3 times.'],
  ['Do unused PDFs roll over?', 'Monthly plans roll unused PDFs over one month, capped at the plan’s monthly amount. Pack PDFs never expire.'],
  ['Can I use my own AI key?', 'Yes, on any plan. PDFs made with your own OpenAI or Anthropic key are unlimited and don’t use credits. It doesn’t change your subscription price.'],
  ['Refunds?', 'All sales are final. Subscriptions and Packs are non-refundable, including months you ran on your own key. Cancel a subscription any time and keep access until the period ends.'],
]

/** Smallest monthly plan that covers this pace; past every monthly cap, the pack. */
function fitFor(perMonth: number): Plan {
  return PLANS.filter((p) => p.kind === 'monthly').find((p) => perMonth <= p.pdfs) ?? PLANS.find((p) => p.kind === 'pack')!;
}

/** Public pricing. No billing is wired yet: every plan's button goes to sign-up. */
export function Pricing() {
  const avgSec = useAvgSec();
  const [perWeek, setPerWeek] = useState(25);
  const perMonth = Math.max(1, Math.round(perWeek * WEEKS_PER_MONTH));
  const freeFits = perMonth <= FREE_PDFS;
  // The calculator marks a fit; it never greys the other plans out.
  const fit = freeFits ? null : fitFor(perMonth);
  const hours = (perMonth * MANUAL_MIN) / 60;

  return (
    <div className="lp-root lx pr-page">
      <TopNav />

      <section className="shell pr-hero">
        <div className="pr-head">
          <h1 className="lp-display pr-title">Pay for the<br />job, not the<br />chatbot.</h1>

          <div className="pr-calc">
            <label className="lp-label" htmlFor="pr-range">HOW MANY APPLICATIONS A WEEK?</label>
            <div className="pr-calc__row">
              <span className="pr-calc__num">{perWeek}</span>
              <input
                id="pr-range"
                className="pr-range"
                type="range"
                min={1}
                max={50}
                value={perWeek}
                onChange={(e) => setPerWeek(Number(e.target.value))}
                style={{ ['--p' as string]: `${((perWeek - 1) / 49) * 100}%` }}
              />
            </div>
            <dl className="pr-calc__out">
              <div><dt>PDFs a month</dt><dd>~{perMonth}</dd></div>
              <div><dt>Hours saved a month</dt><dd>{Math.round(hours)}</dd></div>
              <div>
                <dt>Best fit</dt>
                <dd className="pr-calc__fit">
                  {!fit ? 'FREE' : fit.kind === 'pack' ? `PACK · lasts ~${Math.max(1, Math.round(fit.pdfs / perMonth))} mo` : `${fit.name} · $${fit.price}/mo`}
                </dd>
              </div>
            </dl>
          </div>
        </div>

        <div className="pr-tiers">
          {PLANS.map((p) => (
            <article key={p.id} className="pr-tier" data-featured={p.featured || undefined} data-fit={p === fit || undefined}>
              {p.featured && <span className="pr-tier__badge">MOST POPULAR</span>}
              <span className="lp-label">{p.name}</span>
              <div className="pr-price">
                <span className="pr-price__cur">$</span>
                <span className="lp-display pr-price__num">{p.price}</span>
                <span className="pr-price__per">{p.per}</span>
              </div>
              <p className="pr-tier__line">{p.line}</p>
              <ul className="pr-tier__list">
                {p.features.map((f) => <li key={f}>{f}</li>)}
              </ul>
              {p === fit && <p className="pr-tier__fit">✓ Fits your ~{perMonth} a month</p>}
              <Link to="/register" className={`lp-btn pr-btn ${p.featured ? 'lp-btn--acid' : 'lp-btn--ink'}`}>
                CHOOSE {p.name} &nbsp;→
              </Link>
            </article>
          ))}
        </div>

        {/* Free sits flat under the paid plans: always there, never competing with them. */}
        <div className="pr-free" data-fit={freeFits || undefined}>
          <div className="pr-free__price">
            <span className="lp-label">FREE</span>
            <span className="lp-display pr-free__num">$0</span>
          </div>
          <p className="pr-free__copy">
            <strong>{FREE_PDFS} PDFs a month, forever.</strong>{' '}
            Unlimited with your own OpenAI or Anthropic key.
          </p>
          {freeFits && <span className="pr-free__fit">✓ Fits your ~{perMonth} a month</span>}
          <Link to="/register" className="lp-btn lp-btn--ink pr-btn pr-free__btn">START FREE →</Link>
        </div>
      </section>

      <section className="shell pr-section">
        <div className="lp-section-mark">
          <h2 className="lp-section-title lx-mark">Why pay for Anvil</h2>
          <div className="lp-section-rule" />
        </div>
        <div className="lx-vs">
          <div className="lx-vs__row lx-vs__row--head">
            <span />
            <span className="lp-display lx-vs__brand">Anvil <span>//</span> CV</span>
            <span className="lp-label lp-muted">CHATGPT + WORD</span>
          </div>
          {compare(avgSec).map(([what, us, them]) => (
            <div key={what} className="lx-vs__row">
              <span className="lp-label">{what}</span>
              <span className="lx-vs__us">{us}</span>
              <span className="lx-vs__them">{them}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="shell pr-section">
        <div className="lp-section-mark">
          <h2 className="lp-section-title lx-mark">Questions</h2>
          <div className="lp-section-rule" />
        </div>
        <div className="pr-faq">
          {FAQ.map(([q, a]) => (
            <details key={q} className="pr-faq__item">
              <summary>{q}</summary>
              <p>{a}</p>
            </details>
          ))}
        </div>
      </section>

      <section className="lp-cta-band">
        <div className="shell lp-cta-band__inner">
          <p className="lp-display lp-cta-band__heading">Start free.<br />Upgrade when it pays.</p>
          <Link to="/register" className="lp-btn lp-btn--acid pr-btn">START FREE &nbsp;→</Link>
        </div>
      </section>

      <SiteFooter />
    </div>
  );
}
