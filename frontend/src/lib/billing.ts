import { api } from './api';

interface UrlResponse { url: string }

/** Sends the browser to Stripe. Rejects with a message to show if the backend refuses. */
async function go(path: string, body?: unknown): Promise<void> {
  const { url } = await api.post<UrlResponse>(path, body);
  if (!url) throw new Error('Billing did not return a page. Try again.');
  window.location.assign(url);
}

/** Checkout for starter, pro or pack. Free accounts only; others are told to use the portal. */
export const startCheckout = (plan: string) => go('/api/billing/checkout', { plan });

/** Runs a checkout once at a time. On failure the buttons come back and the message is shown. */
export function guardedCheckout(plan: string, busy: boolean, setBusy: (b: boolean) => void, setErr: (m: string | null) => void): void {
  if (busy) return;
  setErr(null);
  setBusy(true);
  // On success the page navigates away, so busy stays set until then.
  startCheckout(plan).catch((e: Error) => { setErr(e.message); setBusy(false); });
}

/** The Stripe portal: change plan, update card, cancel, invoices. */
export const openPortal = () => go('/api/billing/portal');
