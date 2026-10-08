// Pure helpers for the project's story bank: grouping, counts and fit checks. No React here.
import { CATEGORIES, type Bullet, type GenerationConfig, type Story } from './api';
import { fitOf, needsRefit } from './bulletLength';

/** The four lenses, in display order. */
export const LENS_ORDER: string[] = CATEGORIES.map(c => c.slug);

/** A bank under this many stories gets the warning in the page header. */
export const STORY_MIN = 3;

/** REJECTED is the trash: kept on the server, hidden everywhere in the UI. */
export const isLive = (b: Bullet) => b.status !== 'REJECTED';

export const lensLabel = (slug: string) => CATEGORIES.find(c => c.slug === slug)?.label ?? slug;

const lensIndex = (slug: string) => {
  const i = LENS_ORDER.indexOf(slug);
  return i < 0 ? LENS_ORDER.length : i;
};

/** A story carries a lens only if it was tagged with it. Asking for another lens is a weak fit, not an error. */
export const weakFit = (story: Story, lens: string) => !story.lenses.includes(lens);

export interface LensGroup {
  lens: string;
  label: string;
  wordings: Bullet[];
  weakFit: boolean;
}

export interface StoryGroup {
  story: Story;
  wordings: Bullet[];
  lenses: LensGroup[];
}

/**
 * The bank as the Bullets tab shows it: stories in API order, each with its live wordings
 * sorted by lens, and those wordings split into lens sub-groups. Live wordings that belong to
 * no listed story (hand-written, or a story that is gone) come back as `loose`.
 */
export function groupStories(stories: Story[], bullets: Bullet[]): { groups: StoryGroup[]; loose: Bullet[] } {
  const live = bullets.filter(isLive);
  const known = new Set(stories.map(s => s.id));
  const groups: StoryGroup[] = [];
  for (const story of stories) {
    const wordings = live
      .filter(b => b.storyId === story.id)
      .sort((a, b) => lensIndex(a.category) - lensIndex(b.category));
    if (wordings.length === 0) continue;
    const lenses: LensGroup[] = [];
    for (const b of wordings) {
      const last = lenses[lenses.length - 1];
      if (last && last.lens === b.category) last.wordings.push(b);
      else lenses.push({ lens: b.category, label: lensLabel(b.category), wordings: [b], weakFit: weakFit(story, b.category) });
    }
    groups.push({ story, wordings, lenses });
  }
  const loose = live.filter(b => !b.storyId || !known.has(b.storyId));
  return { groups, loose };
}

/** Header counts: stories are the live ones; the bank is full at the cap. */
export function bankCounts(stories: Story[], cap: number) {
  return { stories: stories.length, cap, under: stories.length < STORY_MIN, full: stories.length >= cap };
}

/** For the Generate tab's lens picker: how many wordings the story already has in a lens, and whether it is a weak fit. */
export function lensChoice(story: Story, wordings: Bullet[], lens: string) {
  return { count: wordings.filter(b => b.category === lens && isLive(b)).length, weakFit: weakFit(story, lens) };
}

/** Live wordings whose length the refit pass can act on. Refit is shown only when this is above zero. */
export function refitNeeded(bullets: Bullet[], cfg: GenerationConfig): number {
  return bullets.filter(b => isLive(b) && needsRefit(fitOf(b.text, cfg))).length;
}
