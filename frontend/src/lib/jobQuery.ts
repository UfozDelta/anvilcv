/** Not a source: a filter value meaning "my saved postings". Signed-in only. */
export const SAVED = 'saved';

export interface JobFilters {
  q: string;
  location: string;
  remote: boolean;
  days: string;
  /** Stack tags; a posting needs any one of them. */
  stack: string[];
  /** Only postings whose stack overlaps the user's profile skills. */
  mine: boolean;
}

/** The GET /api/public/jobs URL for one page under these filters. */
export function jobsQuery(source: string, { q, location, remote, days, stack, mine }: JobFilters, page: number, size: number) {
  const p = new URLSearchParams({ page: String(page), size: String(size) });
  if (source === SAVED) p.set('saved', 'true');
  else if (source) p.set('source', source);
  if (q.trim()) p.set('q', q.trim());
  if (location.trim()) p.set('location', location.trim());
  if (remote) p.set('remote', 'true');
  if (days) p.set('days', days);
  for (const t of stack) p.append('stack', t);
  if (mine) p.set('mine', 'true');
  return `/api/public/jobs?${p}`;
}

/** Adds the tag when absent, removes it when present. */
export function toggleTag(tags: string[], tag: string) {
  return tags.includes(tag) ? tags.filter(t => t !== tag) : [...tags, tag];
}
