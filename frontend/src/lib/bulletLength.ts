import type { GenerationConfig } from './api';

/**
 * Client-side mirror of the backend's bullet length filter, so a bullet's fit can be shown
 * on every row without a round trip per keystroke.
 *
 * Kept deliberately small and in one file: it must stay in step with
 * `BulletTextRules.decide` / `CHARS_PER_WORD` on the Java side. The backend remains the
 * authority — nothing here gates a write, it only labels what the server would decide.
 */

/** Mirrors `BulletTextRules.CHARS_PER_WORD` — the word bands are read as character bands. */
export const CHARS_PER_WORD = 5.4;

/** Mirrors `BulletTextRules.CHARS_PER_LINE` — rendered characters per LaTeX bullet line. */
export const CHARS_PER_LINE = 105;

export type Fit = 'ONE_LINE' | 'TWO_LINE' | 'DEAD_ZONE' | 'TOO_LONG' | 'TOO_SHORT' | 'OFF';

/**
 * Rendered length: bold markers compile to \textbf{} and take no width on the page.
 *
 * Only PAIRED markers come off, mirroring the backend `BulletTextRules.charCount` and
 * `ApplicationRenderer.escapeRich`. A lone `**` is not bold syntax - it renders as two
 * visible asterisks, so stripping it would measure the bullet short of what it prints.
 */
export function charCount(text: string): number {
  return text.replace(/\*\*([\s\S]+?)\*\*/g, '$1').trim().length;
}

/** Ceiling division — a partial line still costs a full line of vertical space. */
export function estimatedLines(text: string): number {
  return Math.max(1, Math.ceil(charCount(text) / CHARS_PER_LINE));
}

const chars = (words: number) => Math.round(words * CHARS_PER_WORD);

/**
 * Where a bullet's length lands. `OFF` when the user has the filter disabled — there is no
 * band to be outside of, so nothing is flagged.
 */
export function fitOf(text: string, cfg: GenerationConfig): Fit {
  if (!cfg.wordFilterEnabled) return 'OFF';
  const cc = charCount(text);
  if (cc >= chars(cfg.deadZoneLow) && cc <= chars(cfg.deadZoneHigh)) return 'DEAD_ZONE';
  if (cc > chars(cfg.doubleLineHigh)) return 'TOO_LONG';
  if (cc < chars(cfg.minWordFloor)) return 'TOO_SHORT';
  return cc <= chars(cfg.singleLineHigh) ? 'ONE_LINE' : 'TWO_LINE';
}

/** True for the fits REFIT can act on. `OFF` and the two valid bands are left alone. */
export function needsRefit(fit: Fit): boolean {
  return fit === 'DEAD_ZONE' || fit === 'TOO_LONG' || fit === 'TOO_SHORT';
}

export const FIT_LABEL: Record<Fit, string> = {
  ONE_LINE: '1-LINE',
  TWO_LINE: '2-LINE',
  DEAD_ZONE: 'DEAD ZONE',
  TOO_LONG: 'TOO LONG',
  TOO_SHORT: 'TOO SHORT',
  OFF: '',
};

/** Why this bullet is flagged, in the same terms the backend logs use. Empty when it fits. */
export function fitHint(text: string, cfg: GenerationConfig): string {
  const fit = fitOf(text, cfg);
  const cc = charCount(text);
  switch (fit) {
    case 'DEAD_ZONE':
      return `${cc}c half-fills a second line — needs ${chars(cfg.singleLineLow)}-${chars(cfg.singleLineHigh)} or ${chars(cfg.doubleLineLow)}-${chars(cfg.doubleLineHigh)}`;
    case 'TOO_LONG':
      return `${cc}c spills onto a third line — max ${chars(cfg.doubleLineHigh)}`;
    case 'TOO_SHORT':
      return `${cc}c is under the ${chars(cfg.minWordFloor)} floor`;
    default:
      return '';
  }
}
