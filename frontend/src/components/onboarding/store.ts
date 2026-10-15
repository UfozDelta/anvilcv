import { useEffect, useState } from 'react';
import { api, type ApplicationSummary, type Project } from '../../lib/api';

/** How the user chose to fill their account at the welcome. */
/** `fast`: paste a résumé, confirm its bullets, tailor. The other three are the original welcome's roads. */
export type Road = 'import' | 'manual' | 'skip' | 'fast';

export interface Status {
  onboarded: boolean;
  entries: number;
  bullets: number;
  approvedBullets: number;
  applications: number;
  downloaded: boolean;
}

/** What the guide reads: the server's counts plus the few facts it needs to point at real pages. */
export interface Progress {
  status: Status;
  profileDone: boolean;
  experiences: number;
  projects: number;
  /** Where "open your first entry" goes, e.g. /experiences/<id>. */
  firstEntryPath: string | null;
  firstApplicationPath: string | null;
}

const ROAD_KEY = 'anvil.onboarding.road';

/** The road is a per-browser choice; a failed read means "skip", the one that assumes nothing. */
export function getRoad(): Road {
  try {
    const v = localStorage.getItem(ROAD_KEY);
    if (v === 'import' || v === 'manual' || v === 'skip' || v === 'fast') return v;
  } catch { /* storage blocked */ }
  return 'skip';
}

export function setRoad(road: Road) {
  try { localStorage.setItem(ROAD_KEY, road); } catch { /* storage blocked */ }
}

let current: Progress | null = null;
const listeners = new Set<(p: Progress | null) => void>();

function publish(p: Progress | null) {
  current = p;
  listeners.forEach((l) => l(p));
}

/** Forget the cache on sign-out so the next user does not inherit this user's progress. */
export function resetProgress() {
  publish(null);
}

const entryPath = (p: Project) => `/${p.kind === 'EXPERIENCE' ? 'experiences' : 'projects'}/${p.id}`;

export async function refreshProgress(): Promise<void> {
  try {
    const [status, profile, projects, apps] = await Promise.all([
      api.get<Status>('/api/onboarding'),
      api.get<{ name?: string; email?: string }>('/api/profile'),
      api.get<Project[]>('/api/projects'),
      api.get<ApplicationSummary[]>('/api/applications'),
    ]);
    const experiences = projects.filter((p) => p.kind === 'EXPERIENCE');
    const first = experiences[0] ?? projects[0];
    publish({
      status,
      profileDone: !!(profile.name?.trim() && profile.email?.trim()),
      experiences: experiences.length,
      projects: projects.length - experiences.length,
      firstEntryPath: first ? entryPath(first) : null,
      firstApplicationPath: apps[0] ? `/applications/${apps[0].id}` : null,
    });
  } catch { /* keep the last good reading; the guide just does not advance */ }
}

/** The user finished, or chose to hide the tips. */
export async function completeOnboarding() {
  if (current) publish({ ...current, status: { ...current.status, onboarded: true } });
  await api.post('/api/onboarding/complete').catch(() => undefined);
}

/** The first Download PDF click: the point the product has given them what they came for. */
export async function markDownloaded() {
  if (current?.status.downloaded) return;
  await api.post('/api/onboarding/downloaded').catch(() => undefined);
  void refreshProgress();
}

/** The user's progress through the first run; null until the first reading lands. Refreshes on its own while mounted. */
export function useProgress(refreshKey: string): Progress | null {
  const [p, setP] = useState<Progress | null>(current);
  useEffect(() => {
    listeners.add(setP);
    return () => { listeners.delete(setP); };
  }, []);
  // Each page change, then every few seconds: approving a bullet changes the count without any navigation.
  useEffect(() => {
    void refreshProgress();
    const t = window.setInterval(() => { if (!document.hidden) void refreshProgress(); }, 5000);
    return () => window.clearInterval(t);
  }, [refreshKey]);
  return p;
}
