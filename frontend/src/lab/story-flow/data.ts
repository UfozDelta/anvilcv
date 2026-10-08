/**
 * Fictional data for /lab/story-flow, on the invented "Quayside" project.
 * A story = one piece of real work with evidence. `fits` = the wording per lens it supports;
 * a missing lens means the story doesn't fit it.
 */

export type Lens = 'ai-ml' | 'backend' | 'data' | 'general';

export const LENSES: { slug: Lens; name: string; abbr: string; tip: string }[] = [
  { slug: 'ai-ml', name: 'AI/ML', abbr: 'ML', tip: 'Models, training, prediction' },
  { slug: 'backend', name: 'Backend', abbr: 'BE', tip: 'APIs, services, storage' },
  { slug: 'data', name: 'Data Eng', abbr: 'DE', tip: 'Pipelines, parsing, ingestion' },
  { slug: 'general', name: 'General', abbr: 'GN', tip: 'Any role: outcome first' },
];
export const LENS_OF = Object.fromEntries(LENSES.map(l => [l.slug, l])) as Record<Lens, (typeof LENSES)[number]>;

export type Quote = { src: string; text: string };

export type StoryFx = {
  id: string;
  title: string;
  glyph: string;
  evidence: Quote[];
  /** Lens of the one bullet the story arrives with. */
  best: Lens;
  fits: Partial<Record<Lens, string>>;
};

export const STORIES: StoryFx[] = [
  {
    id: 'eta', title: 'ETA from vessel positions', glyph: '◆', best: 'ai-ml',
    evidence: [
      { src: 'internal/eta/project.go', text: '// project arrival from last AIS ping, speed and heading' },
      { src: 'commit 8c1e0d2', text: 'eta: per-route swell correction, winter error < 2 min' },
    ],
    fits: {
      'ai-ml': 'Trained a per-route correction on past crossings that keeps ferry ETAs within **2 minutes** in winter swell.',
      backend: 'Replaced timetable lookups with a Go service that projects ETAs from vessel position, speed and heading.',
      data: 'Rebuilt arrival estimates from live **AIS position pings**, cutting ETA error from **9 minutes to under 2**.',
      general: 'Cut ferry arrival-time error from **9 minutes to under 2** for three island routes.',
    },
  },
  {
    id: 'feed', title: 'Surviving silent feeds', glyph: '▲', best: 'backend',
    evidence: [
      { src: 'internal/feeds/poller.go', text: 'if silentFor > 3*interval { fallbackToTimetable() }' },
      { src: 'README.md', text: 'The operator feed goes quiet for 10-20 minutes most mornings.' },
    ],
    fits: {
      backend: 'Kept the departures board live through daily **10-20 minute** feed outages with a timetable fallback.',
      data: 'Detected silent operator feeds within **3 polls** and back-filled gaps once they recovered.',
      general: 'Kept a public departures board accurate through daily outages of the operator’s data feed.',
    },
  },
  {
    id: 'alerts', title: 'Cancellation alerts', glyph: '●', best: 'backend',
    evidence: [
      { src: 'internal/alerts/notify.go', text: 'LISTEN cancellations; fan out in batches of 500' },
    ],
    fits: {
      backend: 'Pushed cancellation alerts to **4,000 subscribers** within **60 seconds** using Postgres LISTEN/NOTIFY.',
      general: 'Built cancellation alerts that reach **4,000** passengers within a minute.',
    },
  },
  {
    id: 'parser', title: 'Three operator formats, one schema', glyph: '■', best: 'data',
    evidence: [
      { src: 'internal/feeds/parse/', text: 'gtfs.go · csv_legacy.go · xml_harbour.go' },
      { src: 'commit 31aa9f0', text: 'parse: normalise all three feeds into departures table' },
    ],
    fits: {
      data: 'Normalised **three operator feed formats** (GTFS, CSV, XML) into one departures schema.',
      backend: 'Wrote pluggable parsers so a new operator format ships without touching the poller.',
      general: 'Merged three ferry operators’ timetables into one consistent source.',
    },
  },
  {
    id: 'kiosk', title: 'Offline pier kiosk', glyph: '▼', best: 'general',
    evidence: [
      { src: 'web/board/sw.ts', text: 'cache departures for 30 min; show stale badge' },
    ],
    fits: {
      general: 'Shipped a pier kiosk that keeps showing departures through **30 minutes** of lost signal.',
      backend: 'Served a cached departures snapshot so the kiosk degrades to stale data, never a blank screen.',
    },
  },
  {
    id: 'deploy', title: 'Zero-downtime releases', glyph: '✚', best: 'backend',
    evidence: [
      { src: 'deploy/fly.toml', text: 'strategy = "bluegreen"' },
    ],
    fits: {
      backend: 'Moved releases to blue-green deploys with health checks, so the board never drops during a ship.',
      general: 'Released weekly with no visible downtime for passengers.',
    },
  },
];

/** What the project-level Generate can still find, in order. */
export const POOL: StoryFx[] = [
  {
    id: 'weather', title: 'Wind delay flags', glyph: '◇', best: 'ai-ml',
    evidence: [
      { src: 'internal/eta/weather.go', text: 'flag sailing if gust forecast > 35 kn' },
    ],
    fits: {
      'ai-ml': 'Flagged likely delays from forecast gusts, catching **7 of 10** weather-hit sailings a day early.',
      data: 'Joined hourly wind forecasts to each sailing to score delay risk.',
      general: 'Warned passengers about weather delays a day ahead.',
    },
  },
  {
    id: 'replay', title: 'Feed replay for debugging', glyph: '✜', best: 'data',
    evidence: [
      { src: 'cmd/replay/main.go', text: 'replay captures/<date>/*.raw through poller' },
    ],
    fits: {
      data: 'Kept **30 days** of raw feed captures, letting parser fixes be checked against real outages.',
      backend: 'Added a replay mode that pushes recorded captures through the live poller code path.',
      general: 'Made any bad morning reproducible locally from recorded data.',
    },
  },
  {
    id: 'ratelimit', title: 'Public API limits', glyph: '⬣', best: 'backend',
    evidence: [
      { src: 'internal/api/limit.go', text: 'token bucket per key, 60 req/min' },
    ],
    fits: {
      backend: 'Added per-key token-bucket limits so partner traffic never starves the public board.',
      general: 'Opened the departures data to partner apps without risking the main board.',
    },
  },
];

export const ALL: StoryFx[] = [...STORIES, ...POOL];
export const BY_ID: Record<string, StoryFx> = Object.fromEntries(ALL.map(s => [s.id, s]));

/** Bank at load: story id → lenses present (first is the arriving one) and approvals. */
export const SEED: { id: string; lenses: Lens[]; approved?: Lens[] }[] = [
  { id: 'eta', lenses: ['ai-ml', 'data', 'general'], approved: ['data'] },
  { id: 'feed', lenses: ['backend', 'general'] },
  { id: 'alerts', lenses: ['backend'], approved: ['backend'] },
  { id: 'parser', lenses: ['data'] },
  { id: 'kiosk', lenses: ['general'] },
  { id: 'deploy', lenses: ['backend'] },
];
