import type { Bullet, Project, RankedBullet } from './api';

export interface BulletGroup {
  key: string;
  project: Project | null;
  items: RankedBullet[];
}

export interface GroupedBullets {
  experience: BulletGroup[];
  projects: BulletGroup[];
}

/**
 * Group rank-sorted bullets by owning project, split into two sections by
 * project.kind to mirror the PDF (Experience vs Projects).
 *
 * - Project block order = first appearance in `ranking` = best (lowest) rank
 *   within that project.
 * - Bullets within a group preserve their order in `ranking` (rank order, if the
 *   caller passed a rank-sorted array).
 * - A bullet whose project can't be resolved (missing from `bullets`, or whose
 *   projectId is missing from `projectById`) falls into an "Other" group, which
 *   lands in the `projects` section last.
 */
export function groupRankedByProject(
  ranking: RankedBullet[],
  bullets: Record<string, Bullet>,
  projectById: Record<string, Project>,
): GroupedBullets {
  const buckets = new Map<string, RankedBullet[]>(); // insertion order = best rank
  for (const r of ranking) {
    const pid = bullets[r.bulletId]?.projectId ?? '__other__';
    if (!buckets.has(pid)) buckets.set(pid, []);
    buckets.get(pid)!.push(r);
  }

  const experience: BulletGroup[] = [];
  const projects: BulletGroup[] = [];
  for (const [pid, items] of buckets) {
    const project = pid === '__other__' ? null : projectById[pid] ?? null;
    const group: BulletGroup = { key: pid, project, items };
    if (project?.kind === 'EXPERIENCE') experience.push(group);
    else projects.push(group); // PROJECT-kind + unknown/other fall here, other last
  }
  return { experience, projects };
}
