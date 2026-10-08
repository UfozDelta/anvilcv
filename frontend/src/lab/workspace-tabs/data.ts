/**
 * Fictional data for /lab/workspace-tabs, on top of /lab/workspace's "Quayside" fixtures.
 * Adds what Generate can find (POOL), the repo areas each item belongs to, and one-line summaries.
 */
import { GLYPHS } from '../project-split/data';
import { RESERVE, STORIES, type StoryFx } from '../workspace/data';

/** The UI word for a "story", per variant, so the user can compare. */
export const TERMS = [
  { one: 'Highlight', many: 'Highlights' },
  { one: 'Win', many: 'Wins' },
  { one: 'Feature', many: 'Features' },
];
export type Term = (typeof TERMS)[number];

const EXTRA: StoryFx[] = [
  {
    id: 's-replay', title: 'Feed replay for debugging', tags: ['tooling'],
    fits: {
      systems: 'Recorded raw operator feeds so any bad morning can be replayed locally against the poller.',
      data: 'Kept **30 days** of raw feed captures, letting parser fixes be checked against real outages.',
      backend: 'Added a replay mode to the poller that pushes recorded captures through the live code path.',
    },
  },
  {
    id: 's-canary', title: 'Release smoke checks', tags: ['ci'],
    fits: {
      devops: 'Gated every release on smoke checks that query live departures before traffic moves over.',
      backend: 'Wrote end-to-end checks that compare API departures with the operator feed after each deploy.',
    },
  },
  {
    id: 's-apikeys', title: 'Scoped keys for partner apps', tags: ['auth'],
    fits: {
      security: 'Issued scoped, revocable API keys to **2 partner apps** instead of one shared token.',
      backend: 'Added per-key quotas to the public API so partner traffic never starves the board.',
      comms: 'Wrote the partner API guide and onboarding notes for the harbour office.',
    },
  },
];

/** What Generate can find, in order. */
export const POOL: StoryFx[] = [...RESERVE, ...EXTRA];
export const ALL: StoryFx[] = [...STORIES, ...POOL];
export const GLYPHS_X = [...GLYPHS, '◇', '✜', '⬣'];

/** One line per findable item. */
export const LINE: Record<string, string> = {
  's-fanout': 'Batched pushes reach 4,000 subscribers in a minute.',
  's-notify': 'Alert queue moved from Redis onto Postgres.',
  's-weather': 'Forecast wind flags likely delays per sailing.',
  's-a11y': 'Contrast and type reworked for low vision.',
  's-bilingual': 'Every label and alert in two languages.',
  's-replay': 'Recorded feeds replay a bad morning locally.',
  's-canary': 'Smoke checks gate each release.',
  's-apikeys': 'Partners get scoped, revocable keys.',
};

/** Repo areas: what the project contains. `items` = everything that belongs there, in the bank or findable. */
export type Area = { name: string; modules: string[]; files: number; purpose: string; items: string[] };
export const AREAS: Area[] = [
  { name: 'feed-poller', modules: ['internal/feeds'], files: 12, purpose: 'Reads operator feeds, survives silence.', items: ['s-feed', 's-parser', 's-replay'] },
  { name: 'eta-model', modules: ['internal/eta'], files: 7, purpose: 'Arrival estimates from vessel position.', items: ['s-eta', 's-weather'] },
  { name: 'alerts', modules: ['internal/alerts'], files: 9, purpose: 'Cancellation push alerts.', items: ['s-alerts', 's-fanout'] },
  { name: 'store', modules: ['db/migrations'], files: 18, purpose: 'One Postgres schema.', items: ['s-notify'] },
  { name: 'api', modules: ['internal/api'], files: 8, purpose: 'Public departures API.', items: ['s-ratelimit', 's-apikeys'] },
  { name: 'web-board', modules: ['web/board'], files: 31, purpose: 'Board and offline kiosk.', items: ['s-kiosk', 's-a11y', 's-bilingual'] },
  { name: 'deploy', modules: ['deploy'], files: 4, purpose: 'Zero-downtime releases.', items: ['s-deploy', 's-canary'] },
];
export const AREA_OF: Record<string, string> = Object.fromEntries(AREAS.flatMap(a => a.items.map(id => [id, a.name])));

/** A lens with fewer bullets than this is a gap. */
export const THIN = 2;
