import { useEffect, useRef, useState } from 'react';
import { guardedCheckout } from '../lib/billing';
import { QUOTA_EVENT, type QuotaBody } from '../lib/quota';

const PLANS = [
  { id: 'starter', name: 'STARTER', price: 10, per: '/ month', line: '60 tailors, 5 generations a month' },
  { id: 'pro', name: 'PRO', price: 14, per: '/ month', line: '150 tailors, 15 generations a month', featured: true },
  { id: 'pack', name: 'PACK', price: 39, per: 'once', line: '200 tailors, 20 generations, never expire' },
];

const FOCUSABLE = 'button:not([disabled]), a[href]';

/** Opens when any request is refused for quota (402). Mounted once; see lib/quota.ts. */
export function PaywallModal() {
  const [q, setQ] = useState<QuotaBody | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const on = (e: Event) => { setErr(null); setBusy(false); setQ((e as CustomEvent<QuotaBody>).detail); };
    window.addEventListener(QUOTA_EVENT, on);
    return () => window.removeEventListener(QUOTA_EVENT, on);
  }, []);

  useEffect(() => {
    if (!q) return;
    const prev = document.activeElement as HTMLElement | null;
    box.current?.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    return () => prev?.focus();
  }, [q]);

  useEffect(() => {
    if (!q) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setQ(null); return; }
      if (e.key !== 'Tab' || !box.current) return;
      const els = [...box.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
      const first = els[0], last = els[els.length - 1];
      if (!box.current.contains(document.activeElement)) { e.preventDefault(); first.focus(); }
      else if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [q]);

  if (!q) return null;
  const close = () => setQ(null);
  const resets = q.resetsAt ? new Date(q.resetsAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric' }) : null;
  // On the free plan a generation 402 counts entries written for, not runs.
  const entries = q.plan === 'free' && q.kind === 'GENERATION';
  const what = q.kind === 'GENERATION' ? 'bullet generations' : 'tailors';

  return (
    <div className="pwm-scrim" onClick={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div ref={box} className="pwm" role="dialog" aria-modal="true" aria-labelledby="pwm-h">
        <button type="button" className="pwm-x" aria-label="Close" onClick={close}>✕</button>
        <span className="ap-label">{entries ? `${q.used} of ${q.limit} free entries written` : `${q.used} of ${q.limit} ${what} used`}</span>
        <h2 id="pwm-h" className="pwm-h">{entries ? "That's all the entries the free plan writes bullets for." : `That's all your ${what}${resets ? ' this month' : ''}.`}</h2>
        <p>{q.message}{resets ? ` More on ${resets}.` : ''} Your saved work, preview and downloads stay free.</p>
        <div className="pwm-plans">
          {PLANS.map((p) => (
            <button key={p.id} type="button" className="pwm-plan" data-featured={p.featured || undefined} disabled={busy}
              onClick={() => guardedCheckout(p.id, busy, setBusy, setErr)}>
              <span className="pwm-plan__name">{p.name}{p.featured && <em> · most popular</em>}</span>
              <span><b>${p.price}</b> {p.per}</span>
              <span className="pwm-plan__line">{p.line}</span>
            </button>
          ))}
        </div>
        {err && <div className="err" role="alert">{err}</div>}
        <button type="button" className="ap-btn ap-btn--ghost" data-autofocus onClick={close}>Not now</button>
      </div>
    </div>
  );
}
