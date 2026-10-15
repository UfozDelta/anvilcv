import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../lib/api';
import { openPortal } from '../lib/billing';
import type { Usage } from '../lib/quota';

const NAMES: Record<string, string> = { free: 'Free', starter: 'Starter', pro: 'Pro', pack: 'Pack' };

/** Plan, what is used, and the way to change it. Settings page. */
export function BillingSection() {
  const [u, setU] = useState<Usage | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [params] = useSearchParams();
  const justPaid = params.get('billing') === 'success';

  useEffect(() => {
    const load = () => { api.get<Usage>('/api/quota').then(setU).catch(() => setU(null)); };
    load();
    // Stripe tells us about the payment by webhook, a moment after the browser comes back (longer for
    // a delayed payment method). Keep checking while the plan is still free, for about a minute.
    if (!justPaid) return;
    let tries = 0;
    const id = window.setInterval(() => {
      tries += 1;
      if (tries > 20) window.clearInterval(id);
      else load();
    }, 3000);
    return () => window.clearInterval(id);
  }, [justPaid]);

  if (!u) return null;
  const free = u.plan === 'free';
  const paid = !free && !u.exempt;
  const resets = u.resetsAt ? new Date(u.resetsAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric' }) : null;

  const manage = () => {
    setErr(null);
    setBusy(true);
    openPortal().catch((e: Error) => { setErr(e.message); setBusy(false); });
  };

  return (
    <section className="pf-sec bl" aria-labelledby="bl-h">
      <h2 id="bl-h" className="ap-label">Plan and billing</h2>
      {justPaid && <p className="bl-ok" role="status">{free ? 'Payment received. Your plan updates shortly; a delayed payment method can take longer.' : `You are on ${NAMES[u.plan] ?? u.plan}. Thank you.`}</p>}
      {u.exempt ? (
        <p className="bl-line">Your account is not metered.</p>
      ) : (
        <>
          <p className="bl-line"><strong>{NAMES[u.plan] ?? u.plan}</strong>{resets && <span> · resets {resets}</span>}</p>
          <ul className="bl-use">
            {u.tailors.limit != null && <li>Tailors: {u.tailors.used} of {u.tailors.limit}{u.plan === 'pack' ? ' total' : ''}</li>}
            {u.generations.limit != null && <li>Generations: {u.generations.used} of {u.generations.limit}{u.plan === 'pack' ? ' total' : ''}</li>}
            {free && u.projects && u.experiences && (
              <li>Bullets written for {u.projects.used} of {u.projects.cap} projects and {u.experiences.used} of {u.experiences.cap} work experience</li>
            )}
          </ul>
          <div className="bl-act">
            {free && <Link to="/pricing" className="ap-btn ap-btn--acid">See plans</Link>}
            {paid && <button type="button" className="ap-btn ap-btn--ghost" disabled={busy} onClick={manage}>{busy ? 'Opening…' : 'Manage billing'}</button>}
          </div>
          {err && <div className="err" role="alert">{err}</div>}
        </>
      )}
    </section>
  );
}
