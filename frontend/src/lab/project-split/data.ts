/** Fictional data for /lab/project-split. "Quayside" and everything in it is invented. */
import type { Project, RepoMap } from '../../lib/api';

export const PROJECT_FX: Project = {
  id: 'ps-quayside',
  kind: 'PROJECT',
  name: 'Quayside',
  description: 'Live ferry departures for three island routes: a departures board, a pier kiosk and cancellation alerts, built on the operators’ own feeds.',
  githubUrl: 'https://github.com/quayside-demo/quayside',
  repoBranch: 'main',
  repoCommitSha: '4f2c9ab71d0e',
  repoContextReady: true,
  techStack: 'Go, PostgreSQL, React, service workers, Fly.io',
  yourRole: 'Solo: backend, frontend and infra',
  ownership: 'Feed poller, ETA model, alert pipeline, kiosk board and deploys.',
  scaleImpact: 'ETA error from 9 min to under 2; alerts out within 60 s; 4,000 subscribers.',
  hardestProblem: 'The operator feed goes silent for 10-20 minutes most mornings.',
  technicalDecisions: 'Postgres LISTEN/NOTIFY over a managed queue; service-worker cache for the kiosk.',
  userImpact: 'Island commuters and the harbour office.',
  securityPosture: '',
  contextDescription: 'A Go poller reads three operator feeds and AIS pings into Postgres. An ETA model projects arrivals from position and heading. A React board and an offline kiosk read one API.',
  createdAt: '2026-06-01T00:00:00Z',
};

export const REPO_MAP: RepoMap = {
  sha: '4f2c9ab71d0e',
  facts: [
    { label: 'Go files', value: '64', source: 'file count under cmd/ and internal/' },
    { label: 'endpoints', value: '11', source: 'routes registered in internal/api' },
    { label: 'migrations', value: '18', source: 'files in db/migrations' },
    { label: 'operators', value: '3', source: 'feed adapters in internal/feeds' },
  ],
  modules: [
    { path: 'internal/feeds', files: 12, loc: 2140, languages: ['Go'], rank: 1, symbols: ['Poller', 'Snapshot'], routes: [], dependsOn: ['internal/store'], topFile: 'internal/feeds/poller.go', summary: 'Polls three operator feeds and keeps the last good snapshot.', purpose: 'Feeds drop out daily.' },
    { path: 'internal/eta', files: 7, loc: 1310, languages: ['Go'], rank: 2, symbols: ['Project', 'Track'], routes: [], dependsOn: ['internal/feeds'], topFile: 'internal/eta/project.go', summary: 'Projects arrivals from AIS position, speed and heading.', purpose: 'Timetables run late in winter.' },
    { path: 'internal/alerts', files: 9, loc: 1580, languages: ['Go'], rank: 3, symbols: ['Dispatch'], routes: ['POST /subscribe'], dependsOn: ['internal/store'], topFile: 'internal/alerts/dispatch.go', summary: 'Fans cancellation notices out to subscribers.', purpose: null },
    { path: 'internal/api', files: 8, loc: 960, languages: ['Go'], rank: 4, symbols: ['Router'], routes: ['GET /departures', 'GET /eta', 'POST /subscribe'], dependsOn: ['internal/eta'], topFile: 'internal/api/router.go', summary: 'Public departures API with per-client limits.', purpose: null },
    { path: 'web/board', files: 31, loc: 3820, languages: ['TypeScript'], rank: 5, symbols: ['Board', 'useDepartures'], routes: [], dependsOn: [], topFile: 'web/board/Board.tsx', summary: 'React departures board and offline kiosk build.', purpose: null },
    { path: 'deploy', files: 4, loc: 210, languages: ['TOML', 'Shell'], rank: 6, symbols: [], routes: [], dependsOn: [], topFile: 'deploy/fly.toml', summary: 'Blue/green release on one machine.', purpose: null },
  ],
  project: {
    overview: 'Live ferry departures, ETAs and cancellation alerts for three island routes.',
    audience: 'island commuters, harbour office',
    subsystems: [
      { name: 'feed-poller', purpose: 'Reads operator feeds, survives silence.', lenses: ['backend', 'data'], modules: ['internal/feeds'] },
      { name: 'eta-model', purpose: 'Arrival estimates from vessel position.', lenses: ['data', 'ai-ml'], modules: ['internal/eta'] },
      { name: 'alerts', purpose: 'Cancellation push alerts.', lenses: ['backend', 'comms', 'systems'], modules: ['internal/alerts'] },
      { name: 'web-board', purpose: 'Board and offline kiosk.', lenses: ['frontend'], modules: ['web/board'] },
      { name: 'deploy', purpose: 'Zero-downtime releases.', lenses: ['devops'], modules: ['deploy', 'internal/api'] },
    ],
    flows: [
      { name: 'Departure', steps: ['feed-poller', 'store', 'eta-model', 'api', 'web-board'] },
      { name: 'Cancellation', steps: ['feed-poller', 'alerts', 'push'] },
    ],
  },
};

/** Per-story tags (bullets carry the tags of their story). */
export const STORY_TAGS: Record<string, string[]> = {
  's-eta': ['ais', 'go'],
  's-feed': ['resilience'],
  's-alerts': ['push'],
  's-kiosk': ['react', 'offline'],
  's-parser': ['pdf'],
  's-deploy': ['fly.io'],
  's-ratelimit': ['api'],
  's-a11y': ['a11y'],
  's-fanout': ['load-test'],
  's-weather': ['forecast'],
  's-notify': ['postgres'],
  's-bilingual': ['i18n'],
};

/** Older bullets from before stories: lens and tags. */
export const LOOSE_META: Record<string, { lens: string; tags: string[] }> = {
  'l-1': { lens: 'backend', tags: ['go', 'react'] },
  'l-2': { lens: 'devops', tags: ['tests'] },
};

/** One glyph per story, so wordings of the same story are linkable at a glance. */
export const GLYPHS = ['◆', '●', '▲', '■', '★', '✚', '▼', '◐', '✱', '⬢', '◗', '⬟'];

/** Context fields: key, short label, tooltip, evidence source name, multiline. */
export const CONTEXT_FIELDS: { key: keyof Project; label: string; hint: string; src: string; long?: boolean }[] = [
  { key: 'techStack', label: 'Stack', hint: 'Tech stack', src: 'Tech stack' },
  { key: 'yourRole', label: 'Role', hint: 'Your role', src: 'Your role' },
  { key: 'ownership', label: 'Owned', hint: 'What you owned end-to-end', src: 'Ownership', long: true },
  { key: 'scaleImpact', label: 'Scale', hint: 'Scale & impact', src: 'Scale & impact' },
  { key: 'hardestProblem', label: 'Hardest', hint: 'Hardest problem solved', src: 'Hardest problem', long: true },
  { key: 'technicalDecisions', label: 'Decisions', hint: 'Key technical decisions', src: 'Technical decisions', long: true },
  { key: 'userImpact', label: 'Users', hint: 'Who it served & why it mattered', src: 'User impact' },
  { key: 'securityPosture', label: 'Security', hint: 'Security & compliance posture', src: 'Security posture', long: true },
  { key: 'contextDescription', label: 'Architecture', hint: 'Architecture overview', src: 'Context', long: true },
];
