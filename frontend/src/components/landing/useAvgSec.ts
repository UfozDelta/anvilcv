import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { AVG_SEC } from './heroData';

interface PublicStats { avgPipelineDurationSec: number | null }

let cached: number | null = null;
let pending: Promise<number | null> | null = null;
/** One request shared by every mention on the page, so they can't disagree. */
function load() {
  pending ??= api.get<PublicStats>('/api/public/stats')
    .then((s) => (cached = s.avgPipelineDurationSec ?? null))
    .catch(() => null);
  return pending;
}

/**
 * The live JD -> PDF average in whole seconds, from /api/public/stats; the fixed claim until it
 * loads (or if it never does). Used by the landing page and the pricing page so they agree.
 */
export function useAvgSec(): number {
  const [sec, setSec] = useState(cached ?? AVG_SEC);
  useEffect(() => {
    let alive = true;
    load().then((v) => { if (alive && v != null) setSec(v); });
    return () => { alive = false; };
  }, []);
  return Math.round(sec);
}
