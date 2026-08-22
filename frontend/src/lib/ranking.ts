import type { RankedBullet } from './api';

export function parseRanking(raw: string | null | undefined): RankedBullet[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed as RankedBullet[];
    return [];
  } catch { return []; }
}

export function setsEqual(a: Set<string>, b: Set<string>) {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}
