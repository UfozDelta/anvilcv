import { describe, it, expect } from 'vitest';
import type { Bullet, GenerationConfig, Story } from './api';
import { bankCounts, groupStories, lensChoice, refitNeeded, weakFit } from './storyBank';

const story = (id: string, lenses: string[]): Story =>
  ({ id, title: `title ${id}`, evidence: ['q'], lenses, createdAt: '2026-01-01T00:00:00Z' });

const bullet = (id: string, storyId: string | null, category: string, status: Bullet['status'] = 'PENDING'): Bullet =>
  ({ id, projectId: 'p', text: `text ${id}`, tags: [], category, status, createdAt: '', updatedAt: '', storyId });

// Only the fields fitOf reads. Band values match the backend defaults used in bulletLength.test.ts.
const CFG = {
  wordFilterEnabled: true, singleLineHigh: 13, deadZoneLow: 14, deadZoneHigh: 22,
  doubleLineHigh: 27, minWordFloor: 9,
} as GenerationConfig;

describe('groupStories', () => {
  it('groups live wordings by story, sorted by lens, with lens sub-groups', () => {
    const stories = [story('s1', ['backend', 'data'])];
    const bullets = [
      bullet('b1', 's1', 'data'),
      bullet('b2', 's1', 'backend'),
      bullet('b3', 's1', 'backend'),
    ];
    const { groups, loose } = groupStories(stories, bullets);
    expect(loose).toEqual([]);
    expect(groups).toHaveLength(1);
    expect(groups[0].wordings.map(b => b.id)).toEqual(['b2', 'b3', 'b1']);
    expect(groups[0].lenses.map(l => [l.lens, l.wordings.length])).toEqual([['backend', 2], ['data', 1]]);
  });

  it('hides trashed wordings and drops a story left with none', () => {
    const stories = [story('s1', ['backend']), story('s2', ['data'])];
    const bullets = [bullet('b1', 's1', 'backend', 'REJECTED'), bullet('b2', 's2', 'data')];
    const { groups } = groupStories(stories, bullets);
    expect(groups.map(g => g.story.id)).toEqual(['s2']);
  });

  it('returns storyless and orphaned live wordings as loose', () => {
    const stories = [story('s1', ['backend'])];
    const bullets = [bullet('hand', null, 'general'), bullet('gone', 'missing', 'data'), bullet('trashed', null, 'data', 'REJECTED')];
    expect(groupStories(stories, bullets).loose.map(b => b.id)).toEqual(['hand', 'gone']);
  });
});

describe('weakFit and lensChoice', () => {
  it('a lens the story does not carry is a weak fit', () => {
    expect(weakFit(story('s1', ['backend']), 'data')).toBe(true);
    expect(weakFit(story('s1', ['backend']), 'backend')).toBe(false);
  });

  it('counts live wordings in the lens and flags weak fit', () => {
    const s = story('s1', ['backend']);
    const wordings = [bullet('b1', 's1', 'backend'), bullet('b2', 's1', 'backend', 'REJECTED')];
    expect(lensChoice(s, wordings, 'backend')).toEqual({ count: 1, weakFit: false });
    expect(lensChoice(s, wordings, 'data')).toEqual({ count: 0, weakFit: true });
  });
});

describe('bankCounts', () => {
  it('warns under three stories and reports full at the cap', () => {
    expect(bankCounts([story('a', []), story('b', [])], 12)).toEqual({ stories: 2, cap: 12, under: true, full: false });
    const twelve = Array.from({ length: 12 }, (_, i) => story(`s${i}`, []));
    expect(bankCounts(twelve, 12)).toEqual({ stories: 12, cap: 12, under: false, full: true });
  });
});

describe('refitNeeded', () => {
  it('counts only live wordings outside the usable bands', () => {
    const bullets = [
      bullet('ok', 's1', 'backend'),
      bullet('long', 's1', 'backend'),
      bullet('trashed', 's1', 'backend', 'REJECTED'),
    ];
    bullets[0].text = 'x'.repeat(80);   // one line: fine
    bullets[1].text = 'x'.repeat(120);  // dead zone: needs refit
    bullets[2].text = 'x'.repeat(120);  // trashed: ignored
    expect(refitNeeded(bullets, CFG)).toBe(1);
  });
});
