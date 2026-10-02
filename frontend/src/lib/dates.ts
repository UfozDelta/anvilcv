/** Best-effort parsing of the free-text `dates` on an experience ("Jun 2024 – Present", "2019 – 2021"). */

export type Span = { start: number; end: number; present: boolean };

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

/** Months since year 0 for "Mon YYYY" or "YYYY"; a bare year is Jan when `isStart`, else Dec. */
function point(s: string, isStart: boolean): number | null {
  const year = s.match(/\b(19|20)\d{2}\b/);
  if (!year) return null;
  const word = s.replace(year[0], '').match(/[A-Za-z]{3,}/)?.[0].slice(0, 3).toLowerCase();
  const m = word ? MONTHS.indexOf(word) : -1;
  return Number(year[0]) * 12 + (m >= 0 ? m : isStart ? 0 : 11);
}

/** Start/end as month indexes (year*12 + month0); null when the text isn't a recognisable range. */
export function parseDates(text: string | null | undefined, now: Date = new Date()): Span | null {
  if (!text) return null;
  const parts = text.split(/\s*(?:[-–—]|\bto\b)\s*/i);
  if (parts.length !== 2) return null;
  const start = point(parts[0], true);
  if (start === null) return null;
  const present = /\b(present|current|now|ongoing)\b/i.test(parts[1]);
  const end = present ? now.getFullYear() * 12 + now.getMonth() : point(parts[1], false);
  if (end === null || end < start) return null;
  return { start, end, present };
}

/** "1y 4m", counting both end months; "" when the range can't be parsed. */
export function tenure(span: Span | null): string {
  if (!span) return '';
  const months = span.end - span.start + 1;
  const y = Math.floor(months / 12);
  const m = months % 12;
  return [y && `${y}y`, m && `${m}m`].filter(Boolean).join(' ');
}

/** Sort key for "most recent end first": current roles on top, unparsable text falls back to createdAt. */
export function endKey(dates: string | null | undefined, createdAt: string | null | undefined, now: Date = new Date()): number {
  const span = parseDates(dates, now);
  if (span) return span.present ? Infinity : span.end;
  const t = createdAt ? new Date(createdAt) : null;
  return t && !Number.isNaN(t.getTime()) ? t.getFullYear() * 12 + t.getMonth() : 0;
}

const PRESENT = /\b(present|current|now|ongoing)\s*$/i;
/** True when the text ends in "Present" / "Current" / "Now" / "Ongoing". */
export function looksCurrent(text: string | null | undefined): boolean {
  return !!text && PRESENT.test(text);
}

/** The part before the range separator: "Jun 2024 – Aug 2024" -> "Jun 2024". */
function startOf(text: string): string {
  return text.split(/\s*(?:[-–—]|\bto\b)\s*/i)[0].trim();
}

/** Ticking "I currently work here": make the dates end in Present ("Jun 2024" -> "Jun 2024 – Present"). */
export function ensurePresent(text: string): string {
  if (looksCurrent(text)) return text;
  const start = startOf(text);
  return start ? `${start} – Present` : '';
}

/** Un-ticking it: drop the Present end and keep the start ("Jun 2024 – Present" -> "Jun 2024"). */
export function dropPresent(text: string): string {
  return looksCurrent(text) ? startOf(text) : text;
}
