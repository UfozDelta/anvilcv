/** Body of a 402 from QuotaExceededException (ApiExceptionHandler.handleQuota). */
export interface QuotaBody {
  source: 'quota';
  message: string;
  kind: 'TAILOR' | 'GENERATION';
  plan: string;
  limit: number;
  used: number;
  resetsAt: string | null;
}

export const QUOTA_EVENT = 'anvil:quota-exceeded';

/** Tells the paywall modal a request was refused for quota. Returns the body if it was one. */
export function reportQuota(status: number, body: unknown): QuotaBody | null {
  if (status !== 402 || (body as QuotaBody | undefined)?.source !== 'quota') return null;
  window.dispatchEvent(new CustomEvent(QUOTA_EVENT, { detail: body }));
  return body as QuotaBody;
}
