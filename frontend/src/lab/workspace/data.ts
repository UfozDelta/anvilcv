/**
 * Fictional data for /lab/workspace. "Quayside" and everything in it is invented.
 * Each story lists one wording per lens it fits; the bank starts with some of them,
 * Generate writes the rest (or brings in a reserve story with all of its lenses).
 */

export type Status = 'BULLET' | 'APPROVED';

export type StoryFx = {
  id: string;
  title: string;
  tags: string[];
  /** lens slug → the wording for that lens. */
  fits: Record<string, string>;
  /** Lenses already in the bank at load, with their status. */
  seed?: Record<string, Status>;
};

/** Short lens names for chips and matrix columns. Order = column order. */
export const LENSES: { slug: string; short: string; abbr: string }[] = [
  { slug: 'backend', short: 'Backend', abbr: 'BE' },
  { slug: 'data', short: 'Data', abbr: 'DA' },
  { slug: 'systems', short: 'Systems', abbr: 'SY' },
  { slug: 'devops', short: 'Infra', abbr: 'IN' },
  { slug: 'frontend', short: 'Frontend', abbr: 'FE' },
  { slug: 'ai-ml', short: 'AI / ML', abbr: 'ML' },
  { slug: 'security', short: 'Security', abbr: 'SE' },
  { slug: 'comms', short: 'Comms', abbr: 'CO' },
];
export const SHORT: Record<string, string> = Object.fromEntries(LENSES.map(l => [l.slug, l.short]));

export const STORIES: StoryFx[] = [
  {
    id: 's-eta', title: 'ETA from vessel positions', tags: ['ais', 'go'],
    seed: { data: 'APPROVED', backend: 'BULLET', 'ai-ml': 'BULLET' },
    fits: {
      data: 'Rebuilt arrival estimates from live **AIS position pings**, cutting ETA error from **9 minutes to under 2**.',
      backend: 'Replaced timetable lookups with a Go service that projects ETAs from vessel position, speed and heading.',
      'ai-ml': 'Trained a per-route correction on past crossings that keeps ferry ETAs within **2 minutes** in winter swell.',
      systems: 'Streamed AIS pings through a single Go projector that updates every open board within a second.',
    },
  },
  {
    id: 's-feed', title: 'Board survives feed outages', tags: ['resilience'],
    seed: { backend: 'BULLET', systems: 'BULLET' },
    fits: {
      backend: 'Kept the departures board live through daily **10-20 minute** operator feed outages by serving the last good snapshot.',
      systems: 'Designed a staleness-aware poller that degrades to cached snapshots instead of blanking the board.',
      data: 'Versioned every operator feed snapshot so the board can fall back to the last consistent state.',
      devops: 'Added feed-silence alarms that page within **3 minutes** and clear themselves when data returns.',
    },
  },
  {
    id: 's-alerts', title: 'Cancellation alerts in under a minute', tags: ['push'],
    seed: { backend: 'APPROVED', comms: 'BULLET', systems: 'BULLET' },
    fits: {
      backend: 'Shipped push alerts that reach subscribers within **60 seconds** of a cancellation notice.',
      comms: 'Delivered cancellation notices by web push and SMS, falling back to SMS when a push goes unread.',
      systems: 'Built cancellation push alerts across **140 commits**, delivering notices within 60 seconds.',
      frontend: 'Built a one-tap subscribe flow on the board so commuters get alerts for their own route.',
    },
  },
  {
    id: 's-kiosk', title: 'Offline pier kiosk', tags: ['react', 'offline'],
    seed: { frontend: 'BULLET' },
    fits: {
      frontend: 'Made the pier kiosk board work offline by caching the day’s departures in a service worker.',
      systems: 'Reconciled the kiosk’s cached sailings with the live API on reconnect without double-showing changes.',
      devops: 'Shipped the kiosk as a locked-down PWA that updates itself overnight over the pier’s spotty link.',
    },
  },
  {
    id: 's-parser', title: 'One parser for three timetables', tags: ['pdf'],
    seed: { data: 'BULLET' },
    fits: {
      data: 'Wrote one timetable parser that reads three operators’ differently laid-out PDF schedules into a shared format.',
      backend: 'Exposed parsed timetables through one typed API so the board never handles operator formats.',
      'ai-ml': 'Used layout detection to find timetable tables in operator PDFs, dropping hand-tuned page offsets.',
    },
  },
  {
    id: 's-deploy', title: 'Zero-downtime deploys', tags: ['fly.io'],
    seed: { devops: 'BULLET' },
    fits: {
      devops: 'Set up blue/green deploys on a single Fly.io machine so the live board never drops during a release.',
      backend: 'Added health-checked cutover so API releases swap only once the new build serves departures.',
      systems: 'Drained open board connections during cutover so no client sees a gap mid-release.',
    },
  },
  {
    id: 's-ratelimit', title: 'Rate limits after a scraper spike', tags: ['api'],
    seed: { security: 'BULLET', backend: 'BULLET' },
    fits: {
      security: 'Added per-client rate limits after a scraper hit the API **40 times a second**, keeping the board responsive.',
      backend: 'Moved the departures endpoint behind token-bucket limits and cached responses, absorbing a 40 rps scraper.',
      devops: 'Put the public API behind an edge cache that serves **95%** of departures requests.',
      systems: 'Shed scraper load with per-client token buckets while keeping kiosk and board traffic untouched.',
    },
  },
];

/** What Generate can still find, in order. */
export const RESERVE: StoryFx[] = [
  {
    id: 's-fanout', title: 'Alert fan-out to 4,000 subscribers', tags: ['load-test'],
    fits: {
      systems: 'Load-tested alert fan-out to **4,000 subscribers** ahead of the summer timetable.',
      backend: 'Batched push delivery so one cancellation reaches **4,000 subscribers** in under a minute.',
      comms: 'Grouped alert sends by route and channel, cutting SMS spend by **40%** at peak.',
      devops: 'Ran the fan-out load test in CI against a staging copy before every timetable change.',
    },
  },
  {
    id: 's-notify', title: 'Postgres instead of managed Redis', tags: ['postgres'],
    fits: {
      devops: 'Retired a managed Redis queue in favour of Postgres LISTEN/NOTIFY, removing one paid service.',
      backend: 'Moved alert dispatch onto Postgres LISTEN/NOTIFY, keeping one datastore for the whole service.',
      systems: 'Made alert dispatch idempotent on top of Postgres notifications so a restart never double-sends.',
      data: 'Kept alerts, sailings and subscribers in one Postgres schema with **18** forward-only migrations.',
    },
  },
  {
    id: 's-weather', title: 'Weather-delay warnings', tags: ['forecast'],
    fits: {
      data: 'Joined the marine forecast to each sailing to flag likely delays when winds pass force 6.',
      'ai-ml': 'Scored delay risk per sailing from forecast wind and past crossings, warning passengers early.',
      frontend: 'Showed delay risk on the board as a plain badge commuters can read from across the pier.',
    },
  },
  {
    id: 's-a11y', title: 'Readable board for low vision', tags: ['a11y'],
    fits: {
      frontend: 'Reworked the board’s contrast and type scale after feedback from low-vision passengers.',
      comms: 'Added spoken departure announcements on the kiosk for passengers who cannot read the board.',
    },
  },
  {
    id: 's-bilingual', title: 'Bilingual board and alerts', tags: ['i18n'],
    fits: {
      frontend: 'Shipped every board label in two languages from one message catalogue.',
      comms: 'Sent cancellation alerts in each subscriber’s chosen language.',
      backend: 'Stored alert templates per locale so operators edit one notice and both languages go out.',
    },
  },
];

/** Older bullets from before stories. Each counts as its own story. */
export const LOOSE = [
  { id: 'l-1', lens: 'backend', text: 'Built a ferry departures web app with Go, PostgreSQL and React.', status: 'APPROVED' as Status, tags: ['go', 'react'] },
  { id: 'l-2', lens: 'devops', text: 'Wrote unit tests for the timetable module.', status: 'BULLET' as Status, tags: ['tests'] },
];
