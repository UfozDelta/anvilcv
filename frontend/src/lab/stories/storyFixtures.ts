/**
 * Fictional data for /lab/stories. "Quayside" and everything in it is invented.
 *
 * Every story carries both variants' wordings so the page can switch between them on the
 * same bank: `byLens` is what the shipped model writes (one wording per story-lens, two of
 * different length for a single-lens story), `general` is variant B (one general wording,
 * sometimes a short/long pair). `lenses` is what findStories tagged — picked lenses in A,
 * auto-detected chips in B.
 */

export type WStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type Wording = {
  id: string;
  text: string;
  status: WStatus;
  /** Variant A: the lens this wording was written for. */
  lens?: string;
  /** Variant B: the length variant, when a story has two. */
  form?: 'short' | 'long';
};

export type Evidence = { quote: string; from: string };

export type StoryFx = {
  id: string;
  title: string;
  evidence: Evidence[];
  lenses: string[];
  byLens: Wording[];
  general: Wording[];
};

export type LooseBullet = { id: string; text: string; status: WStatus };

export const LENS_LABEL: Record<string, string> = {
  'ai-ml': 'AI / ML',
  backend: 'Backend',
  frontend: 'Frontend',
  data: 'Data',
  security: 'Security',
  devops: 'Infra',
  systems: 'Systems',
  comms: 'Comms',
};

export const PROJECT = {
  name: 'Quayside',
  line: 'Live ferry departures for three island routes',
};

/** What generateBank sends: the project's fields (sourceRequest) and its repo map. */
export const SOURCE_FIELDS: { label: string; filled: boolean }[] = [
  { label: 'Description', filled: true },
  { label: 'Context', filled: true },
  { label: 'Tech stack', filled: true },
  { label: 'Your role', filled: true },
  { label: 'Scale & impact', filled: true },
  { label: 'Hardest problem', filled: true },
  { label: 'Technical decisions', filled: true },
  { label: 'Ownership', filled: false },
  { label: 'User impact', filled: false },
  { label: 'Security posture', filled: false },
];

export const REPO = {
  sha: '4f2c9ab',
  subsystems: [
    { name: 'feed-poller', lenses: ['backend', 'data'] },
    { name: 'eta-model', lenses: ['data', 'ai-ml'] },
    { name: 'alerts', lenses: ['backend', 'comms', 'systems'] },
    { name: 'web-board', lenses: ['frontend'] },
    { name: 'deploy', lenses: ['devops'] },
  ],
};

export const STORY_CAP = 12;
export const MAX_NEW_STORIES = 8;

const w = (id: string, text: string, status: WStatus, extra: Partial<Wording> = {}): Wording =>
  ({ id, text, status, ...extra });

/** The seven live stories of the healthy bank. */
export const STORIES: StoryFx[] = [
  {
    id: 's-eta',
    title: 'ETA from vessel positions instead of the printed timetable',
    evidence: [
      { quote: 'ETA error dropped from 9 minutes to under 2 after switching to AIS position pings', from: 'Scale & impact' },
      { quote: 'the timetable assumes calm water; winter crossings run 10-15 minutes late', from: 'Hardest problem' },
    ],
    lenses: ['data', 'backend'],
    byLens: [
      w('a-eta-1', 'Rebuilt arrival estimates from live **AIS position pings**, cutting ETA error from **9 minutes to under 2**.', 'APPROVED', { lens: 'data' }),
      w('a-eta-2', 'Replaced timetable lookups with a Go service that projects ETAs from vessel position, speed and heading, keeping estimates within 2 minutes on winter crossings.', 'PENDING', { lens: 'backend' }),
    ],
    general: [
      w('b-eta-1', 'Rebuilt arrival estimates from live **AIS position pings**, cutting ETA error from **9 minutes to under 2**.', 'APPROVED', { form: 'short' }),
      w('b-eta-2', 'Replaced printed-timetable ETAs with estimates projected from live AIS position pings, cutting arrival error from **9 minutes to under 2** on winter crossings.', 'PENDING', { form: 'long' }),
    ],
  },
  {
    id: 's-feed',
    title: 'Board stays live when the operator feed goes silent',
    evidence: [
      { quote: "the operator's XML feed goes silent for 10-20 minutes most mornings", from: 'Context' },
      { quote: 'falls back to the last good snapshot plus position-based ETAs', from: 'repo map: feed-poller' },
    ],
    lenses: ['backend', 'systems'],
    byLens: [
      w('a-feed-1', 'Kept the departures board live through daily **10-20 minute** operator feed outages by serving the last good snapshot with position-based ETAs.', 'PENDING', { lens: 'backend' }),
      w('a-feed-2', 'Designed a staleness-aware poller that degrades to cached snapshots instead of blanking the board.', 'PENDING', { lens: 'systems' }),
    ],
    general: [
      w('b-feed-1', 'Kept the departures board live through daily **10-20 minute** operator feed outages by serving the last good snapshot with position-based ETAs.', 'PENDING'),
    ],
  },
  {
    id: 's-alerts',
    title: 'Cancellation alerts reach subscribers within a minute',
    evidence: [
      { quote: 'push alerts for cancellations go out within 60 seconds of the operator notice', from: 'Scale & impact' },
    ],
    lenses: ['backend'],
    byLens: [
      w('a-alerts-1', 'Shipped push alerts that reach subscribers within **60 seconds** of a cancellation notice.', 'APPROVED', { lens: 'backend' }),
      w('a-alerts-2', 'Built cancellation push alerts across **140 commits**, delivering notices to subscribers within 60 seconds of the operator update.', 'PENDING', { lens: 'backend' }),
    ],
    general: [
      w('b-alerts-1', 'Shipped push alerts that reach subscribers within **60 seconds** of a cancellation notice.', 'APPROVED'),
    ],
  },
  {
    id: 's-kiosk',
    title: 'Offline-first board for the pier kiosk',
    evidence: [
      { quote: 'the pier kiosk loses signal at high tide, so the board caches the day in a service worker', from: 'Technical decisions' },
    ],
    lenses: ['frontend'],
    byLens: [
      w('a-kiosk-1', 'Made the pier kiosk board work offline by caching the day’s departures in a service worker.', 'PENDING', { lens: 'frontend' }),
      w('a-kiosk-2', 'Built an offline-first React departures board for a pier kiosk that loses signal at high tide, caching the day’s sailings in a service worker and reconciling on reconnect.', 'PENDING', { lens: 'frontend' }),
    ],
    general: [
      w('b-kiosk-1', 'Made the pier kiosk board work offline by caching the day’s departures in a service worker.', 'PENDING', { form: 'short' }),
      w('b-kiosk-2', 'Built an offline-first departures board for a pier kiosk that loses signal at high tide, caching the day’s sailings and reconciling on reconnect.', 'PENDING', { form: 'long' }),
    ],
  },
  {
    id: 's-parser',
    title: 'One timetable parser for three operators’ PDFs',
    evidence: [
      { quote: 'three operators publish timetables as PDFs with different layouts', from: 'Description' },
    ],
    lenses: ['data'],
    byLens: [
      w('a-parser-1', 'Wrote one timetable parser that reads three operators’ differently laid-out PDF schedules into a shared format.', 'PENDING', { lens: 'data' }),
      w('a-parser-2', 'Normalised three operators’ PDF timetables into one schema.', 'REJECTED', { lens: 'data' }),
    ],
    general: [
      w('b-parser-1', 'Wrote one timetable parser that reads three operators’ differently laid-out PDF schedules into a shared format.', 'PENDING'),
    ],
  },
  {
    id: 's-deploy',
    title: 'Zero-downtime deploys on a single small machine',
    evidence: [
      { quote: 'blue/green on one Fly.io machine so the board never drops during a deploy', from: 'repo map: deploy' },
    ],
    lenses: ['devops'],
    byLens: [
      w('a-deploy-1', 'Set up blue/green deploys on a single Fly.io machine so the live board never drops during a release.', 'PENDING', { lens: 'devops' }),
      w('a-deploy-2', 'Ran blue/green releases on one machine with health-checked cutover, keeping the public board up through every deploy.', 'PENDING', { lens: 'devops' }),
    ],
    general: [
      w('b-deploy-1', 'Set up blue/green deploys on a single Fly.io machine so the live board never drops during a release.', 'PENDING'),
    ],
  },
  {
    id: 's-ratelimit',
    title: 'Rate-limited the public API after a scraper spike',
    evidence: [
      { quote: 'a scraper pulled the departures endpoint 40 times a second for an afternoon', from: 'Hardest problem' },
    ],
    lenses: ['security', 'backend'],
    byLens: [
      w('a-rl-1', 'Added per-client rate limits after a scraper hit the API **40 times a second**, keeping the board responsive.', 'PENDING', { lens: 'security' }),
      w('a-rl-2', 'Moved the departures endpoint behind token-bucket limits and cached responses, absorbing a 40 requests-per-second scraper.', 'PENDING', { lens: 'backend' }),
    ],
    general: [
      w('b-rl-1', 'Added per-client rate limits after a scraper hit the API **40 times a second**, keeping the board responsive.', 'PENDING'),
    ],
  },
];

/** What a generate run can still find: added in order, up to the bank's room. */
export const RESERVE: StoryFx[] = [
  {
    id: 's-a11y',
    title: 'Readable board for low-vision passengers',
    evidence: [{ quote: 'raised contrast and type size after feedback from the harbour office', from: 'Context' }],
    lenses: ['frontend'],
    byLens: [
      w('a-a11y-1', 'Reworked the board’s contrast and type scale after harbour office feedback from low-vision passengers.', 'PENDING', { lens: 'frontend' }),
      w('a-a11y-2', 'Raised board contrast and type size for low-vision passengers.', 'PENDING', { lens: 'frontend' }),
    ],
    general: [w('b-a11y-1', 'Reworked the board’s contrast and type scale after harbour office feedback from low-vision passengers.', 'PENDING')],
  },
  {
    id: 's-fanout',
    title: 'Alert fan-out load-tested to 4,000 subscribers',
    evidence: [{ quote: 'load-tested alert fan-out to 4,000 subscribers before the summer timetable', from: 'Scale & impact' }],
    lenses: ['systems'],
    byLens: [
      w('a-fanout-1', 'Load-tested alert fan-out to **4,000 subscribers** ahead of the summer timetable.', 'PENDING', { lens: 'systems' }),
      w('a-fanout-2', 'Batched push delivery and load-tested the alert pipeline at 4,000 subscribers before the busiest season.', 'PENDING', { lens: 'systems' }),
    ],
    general: [w('b-fanout-1', 'Load-tested alert fan-out to **4,000 subscribers** ahead of the summer timetable.', 'PENDING')],
  },
  {
    id: 's-weather',
    title: 'Weather-delay notes from the marine forecast',
    evidence: [{ quote: 'flags likely delays when the marine forecast passes force 6', from: 'Technical decisions' }],
    lenses: ['data'],
    byLens: [
      w('a-weather-1', 'Flagged likely sailing delays on the board when the marine forecast passes force 6.', 'PENDING', { lens: 'data' }),
      w('a-weather-2', 'Joined the marine forecast to each sailing to warn passengers of likely delays before they reach the pier.', 'PENDING', { lens: 'data' }),
    ],
    general: [w('b-weather-1', 'Flagged likely sailing delays on the board when the marine forecast passes force 6.', 'PENDING')],
  },
  {
    id: 's-notify',
    title: 'Dropped managed Redis for Postgres notifications',
    evidence: [{ quote: 'replaced the managed Redis queue with Postgres LISTEN/NOTIFY', from: 'Technical decisions' }],
    lenses: ['devops', 'backend'],
    byLens: [
      w('a-notify-1', 'Retired a managed Redis queue in favour of Postgres LISTEN/NOTIFY, removing one paid service.', 'PENDING', { lens: 'devops' }),
      w('a-notify-2', 'Moved alert dispatch onto Postgres LISTEN/NOTIFY, keeping one datastore for the whole service.', 'PENDING', { lens: 'backend' }),
    ],
    general: [w('b-notify-1', 'Retired a managed Redis queue in favour of Postgres LISTEN/NOTIFY, removing one paid service.', 'PENDING')],
  },
  {
    id: 's-bilingual',
    title: 'Bilingual board and alerts',
    evidence: [{ quote: 'every board label and alert ships in two languages', from: 'Description' }],
    lenses: ['frontend', 'comms'],
    byLens: [
      w('a-bi-1', 'Shipped every board label and push alert in two languages from one message catalogue.', 'PENDING', { lens: 'frontend' }),
      w('a-bi-2', 'Sent cancellation alerts in each subscriber’s chosen language.', 'PENDING', { lens: 'comms' }),
    ],
    general: [w('b-bi-1', 'Shipped every board label and push alert in two languages from one message catalogue.', 'PENDING')],
  },
];

/** Stories whose every wording was rejected: kept so the next run doesn't find them again. */
export const DISMISSED: StoryFx[] = [
  {
    id: 's-ci',
    title: 'CI pipeline with lint and tests',
    evidence: [{ quote: 'GitHub Actions runs lint and tests on every push', from: 'Context' }],
    lenses: ['devops'],
    byLens: [
      w('a-ci-1', 'Set up a CI pipeline that lints and tests every push.', 'REJECTED', { lens: 'devops' }),
      w('a-ci-2', 'Configured GitHub Actions to run linting and the test suite on each push to main.', 'REJECTED', { lens: 'devops' }),
    ],
    general: [w('b-ci-1', 'Set up a CI pipeline that lints and tests every push.', 'REJECTED')],
  },
  {
    id: 's-login',
    title: 'Magic-link sign-in for subscribers',
    evidence: [{ quote: 'subscribers sign in with an emailed link', from: 'Description' }],
    lenses: ['security'],
    byLens: [
      w('a-login-1', 'Added passwordless magic-link sign-in for alert subscribers.', 'REJECTED', { lens: 'security' }),
    ],
    general: [w('b-login-1', 'Added passwordless magic-link sign-in for alert subscribers.', 'REJECTED')],
  },
];

/** Bullets from before stories existed (storyId null). Each counts as its own story. */
export const LOOSE: LooseBullet[] = [
  { id: 'l-1', text: 'Built a ferry departures web app with Go, PostgreSQL and React.', status: 'APPROVED' },
  { id: 'l-2', text: 'Wrote unit tests for the timetable module.', status: 'PENDING' },
];

export type RunSummary = {
  label: string;
  /** Null in variant B: nothing was picked. */
  lenses: string[] | null;
  bankFull: boolean;
  found: number;
  kept: string[];
  overlaps: { title: string; repeats: string }[];
  noEvidence: number;
  written: number;
  repairs: { kind: string; note: string }[];
  nearDuplicates: number;
};

/** The run that produced the current bank. */
export const LAST_RUN: RunSummary = {
  label: 'Last run, 2 days ago',
  lenses: ['backend', 'data', 'frontend', 'devops'],
  bankFull: false,
  found: 6,
  kept: ['Board stays live when the operator feed goes silent', 'Offline-first board for the pier kiosk', 'Zero-downtime deploys on a single small machine'],
  overlaps: [{ title: 'Departures board survives feed outages', repeats: 'Board stays live when the operator feed goes silent' }],
  noEvidence: 2,
  written: 7,
  repairs: [
    { kind: 'Vanity count', note: '“across 140 commits” cut from a wording, then rewritten' },
    { kind: 'Padding', note: 'trailing “ensuring a seamless experience” removed' },
  ],
  nearDuplicates: 1,
};

export function isVanity(text: string) {
  return /\b\d[\d,]*\s+(commits?|lines of code|files|tests|migrations|tables)\b/i.test(text);
}
