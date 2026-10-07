import { describe, expect, it } from 'vitest';
import { jobsQuery, SAVED, toggleTag, type JobFilters } from './jobQuery';

const NONE: JobFilters = { q: '', location: '', remote: false, days: '', stack: [], mine: false };

describe('jobsQuery', () => {
  it('sends only paging when no filters are set', () => {
    expect(jobsQuery('', NONE, 0, 50)).toBe('/api/public/jobs?page=0&size=50');
  });

  it('repeats stack tags, encodes them, and adds mine', () => {
    const url = jobsQuery('linkedin', { ...NONE, stack: ['C++', 'Java'], mine: true }, 1, 50);
    const p = new URL(url, 'http://x').searchParams;
    expect(p.getAll('stack')).toEqual(['C++', 'Java']);
    expect(p.get('mine')).toBe('true');
    expect(p.get('source')).toBe('linkedin');
  });

  it('maps the saved tab to saved=true, not a source', () => {
    const p = new URL(jobsQuery(SAVED, NONE, 0, 50), 'http://x').searchParams;
    expect(p.get('saved')).toBe('true');
    expect(p.has('source')).toBe(false);
  });
});

describe('toggleTag', () => {
  it('adds then removes', () => {
    expect(toggleTag(['Java'], 'Go')).toEqual(['Java', 'Go']);
    expect(toggleTag(['Java', 'Go'], 'Java')).toEqual(['Go']);
  });
});
