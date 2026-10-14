import { describe, expect, it } from 'vitest';
import { deriveSteps } from './guide';
import type { Progress } from './store';

const fresh = (over: Partial<Progress['status']> = {}, rest: Partial<Progress> = {}): Progress => ({
  status: { onboarded: false, entries: 0, bullets: 0, approvedBullets: 0, applications: 0, downloaded: false, ...over },
  profileDone: false, experiences: 0, projects: 0, firstEntryPath: null, firstApplicationPath: null, ...rest,
});
const next = (p: Progress, road: Parameters<typeof deriveSteps>[2], field = 'software') =>
  deriveSteps(p, field, road).find((s) => !s.done)?.id;

describe('deriveSteps', () => {
  it('starts each road at its own first step', () => {
    expect(next(fresh(), 'import')).toBe('import');
    expect(next(fresh(), 'manual')).toBe('profile');
    expect(next(fresh(), 'skip')).toBe('entry');
  });

  it('is driven by what exists, not by tips seen', () => {
    const p = fresh({ entries: 2, bullets: 6, approvedBullets: 2 });
    expect(next(p, 'import')).toBe('profile');
    expect(next({ ...p, profileDone: true }, 'import')).toBe('approve');
    expect(next(fresh({ entries: 1, bullets: 4 }), 'skip')).toBe('approve');
  });

  it('needs three approved bullets, then a tailored page, then the download', () => {
    const base = { entries: 1, bullets: 5, approvedBullets: 3 };
    expect(next(fresh(base, { profileDone: true }), 'manual')).toBe('tailor');
    expect(next(fresh({ ...base, applications: 1 }, { profileDone: true }), 'manual')).toBe('page');
    expect(next(fresh({ ...base, applications: 1, downloaded: true }, { profileDone: true }), 'manual')).toBeUndefined();
  });

  it('words the entry step for the field', () => {
    const body = (field: string) => deriveSteps(fresh(), field, 'skip')[0].body;
    expect(body('mechanical')).toContain('Formula SAE');
    expect(body('software')).not.toContain('Formula SAE');
  });

  it('counts approvals out of three without overshooting', () => {
    const approve = deriveSteps(fresh({ entries: 1, approvedBullets: 7 }), 'software', 'manual').find((s) => s.id === 'approve')!;
    expect(approve.done).toBe(true);
    expect(approve.body).toContain('3 of 3');
  });
});
