import { describe, it, expect } from 'vitest';
import type { Bullet, Project, RankedBullet } from './api';
import { groupRankedByProject } from './groupBullets';

// ---- test data builders ----

function project(id: string, kind: Project['kind'], name = id): Project {
  return { id, kind, name, description: '', createdAt: '2026-01-01' };
}

function bullet(id: string, projectId: string): Bullet {
  return { id, projectId, text: `text-${id}`, tags: [], category: 'general',
           createdAt: '2026-01-01', updatedAt: '2026-01-01' };
}

function ranked(bulletId: string, rank: number): RankedBullet {
  return { bulletId, rank, why: '' };
}

/** Build the bullets/projects lookup maps from arrays. */
function maps(bs: Bullet[], ps: Project[]) {
  const bullets: Record<string, Bullet> = {};
  bs.forEach(b => { bullets[b.id] = b; });
  const projectById: Record<string, Project> = {};
  ps.forEach(p => { projectById[p.id] = p; });
  return { bullets, projectById };
}

describe('groupRankedByProject', () => {
  it('splits bullets into experience and projects sections by project.kind', () => {
    const ps = [project('exp1', 'EXPERIENCE'), project('proj1', 'PROJECT')];
    const bs = [bullet('b1', 'exp1'), bullet('b2', 'proj1')];
    const { bullets, projectById } = maps(bs, ps);
    const ranking = [ranked('b1', 1), ranked('b2', 2)];

    const out = groupRankedByProject(ranking, bullets, projectById);

    expect(out.experience.map(g => g.key)).toEqual(['exp1']);
    expect(out.projects.map(g => g.key)).toEqual(['proj1']);
  });

  it('orders project blocks by best (first-appearing) rank', () => {
    const ps = [project('pA', 'PROJECT'), project('pB', 'PROJECT')];
    const bs = [bullet('a1', 'pA'), bullet('a2', 'pA'), bullet('b1', 'pB')];
    const { bullets, projectById } = maps(bs, ps);
    // pB's best bullet (rank 1) appears before pA's best (rank 2) -> pB first.
    const ranking = [ranked('b1', 1), ranked('a1', 2), ranked('a2', 3)];

    const out = groupRankedByProject(ranking, bullets, projectById);

    expect(out.projects.map(g => g.key)).toEqual(['pB', 'pA']);
  });

  it('preserves rank order of bullets within a group', () => {
    const ps = [project('pA', 'PROJECT')];
    const bs = [bullet('a1', 'pA'), bullet('a2', 'pA'), bullet('a3', 'pA')];
    const { bullets, projectById } = maps(bs, ps);
    const ranking = [ranked('a1', 1), ranked('a2', 2), ranked('a3', 3)];

    const out = groupRankedByProject(ranking, bullets, projectById);

    expect(out.projects[0].items.map(r => r.bulletId)).toEqual(['a1', 'a2', 'a3']);
  });

  it('buckets a bullet with unknown projectId into an "Other" group in projects', () => {
    const ps = [project('pA', 'PROJECT')];
    const bs = [bullet('a1', 'pA')]; // b-orphan intentionally absent from bullets map
    const { bullets, projectById } = maps(bs, ps);
    const ranking = [ranked('a1', 1), ranked('orphan', 2)];

    const out = groupRankedByProject(ranking, bullets, projectById);

    const other = out.projects.find(g => g.key === '__other__');
    expect(other).toBeDefined();
    expect(other!.project).toBeNull();
    expect(other!.items.map(r => r.bulletId)).toEqual(['orphan']);
  });

  it('treats a bullet whose project was deleted as project:null (Other)', () => {
    // bullet resolves, but its projectId is missing from projectById (deleted project).
    const bs = [bullet('a1', 'goneProject')];
    const { bullets, projectById } = maps(bs, []);
    const ranking = [ranked('a1', 1)];

    const out = groupRankedByProject(ranking, bullets, projectById);

    expect(out.experience).toEqual([]);
    expect(out.projects).toHaveLength(1);
    expect(out.projects[0].project).toBeNull();
  });

  it('returns empty sections for empty ranking', () => {
    const out = groupRankedByProject([], {}, {});
    expect(out).toEqual({ experience: [], projects: [] });
  });

  it('groups everything as Other when bullets map is empty (load race state)', () => {
    const ps = [project('pA', 'PROJECT')];
    const { projectById } = maps([], ps);
    const ranking = [ranked('a1', 1), ranked('a2', 2)];

    // bullets={} -> no projectId resolvable -> single Other group, no throw.
    const out = groupRankedByProject(ranking, {}, projectById);

    expect(out.experience).toEqual([]);
    expect(out.projects).toHaveLength(1);
    expect(out.projects[0].key).toBe('__other__');
    expect(out.projects[0].items).toHaveLength(2);
  });
});
