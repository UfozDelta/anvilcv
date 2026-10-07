import type { RankedBullet } from './api';

export function parseRanking(raw: string | null | undefined): RankedBullet[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed as RankedBullet[];
    return [];
  } catch { return []; }
}
