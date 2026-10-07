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

/**
 * Bank bullets of one project that have no row yet: the ones a manual pick can add beyond the
 * LLM-ranked shortlist. REJECTED bullets never go on a page (same filter as the backend's
 * selectable bank). Rank is unused for these rows.
 */
export function bankRows(
  projectId: string,
  bullets: Record<string, Bullet>,
  shown: Set<string>,
): RankedBullet[] {
  return Object.values(bullets)
    .filter(b => b.projectId === projectId && b.status !== 'REJECTED' && !shown.has(b.id))
    .map(b => ({ bulletId: b.id, rank: 0, why: '' }));
}

/**
 * Selection order is page order: the renderer groups entries and their bullets by first
 * appearance in the submitted ids. A pick lands after the last selected bullet of its own
 * project, not at the end of the list, so re-including a bullet doesn't move its whole entry.
 */
export function insertSelected(order: string[], bid: string, projectOf: (id: string) => string | undefined): string[] {
  const pid = projectOf(bid);
  let at = -1;
  order.forEach((id, i) => { if (projectOf(id) === pid) at = i; });
  if (at < 0) return [...order, bid];
  return [...order.slice(0, at + 1), bid, ...order.slice(at + 1)];
}

/** Swap `bid` with the nearest selected bullet of the same project in direction `dir`. */
export function moveWithinProject(order: string[], bid: string, dir: -1 | 1,
                                  projectOf: (id: string) => string | undefined): string[] {
  const i = order.indexOf(bid);
  if (i < 0) return order;
  const pid = projectOf(bid);
  for (let j = i + dir; j >= 0 && j < order.length; j += dir) {
    if (projectOf(order[j]) !== pid) continue;
    const next = [...order];
    [next[i], next[j]] = [next[j], next[i]];
    return next;
  }
  return order;
}

/** A group's rows as the page shows them: included bullets in page order, then the rest in rank order. */
export function pageOrder(items: RankedBullet[], order: string[]): RankedBullet[] {
  const pos = new Map(order.map((id, i) => [id, i]));
  const inPage = items.filter(r => pos.has(r.bulletId))
    .sort((a, b) => pos.get(a.bulletId)! - pos.get(b.bulletId)!);
  return [...inPage, ...items.filter(r => !pos.has(r.bulletId))];
}
