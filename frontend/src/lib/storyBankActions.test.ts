import { describe, it, expect } from 'vitest';
import type { Bullet } from './api';
import { attempt, trashWording } from './storyBankActions';

const wording = (status: Bullet['status']): Bullet =>
  ({ id: 'b1', projectId: 'p', text: 'Cut latency.', tags: [], category: 'backend', status, createdAt: '', updatedAt: '' });

describe('trashWording', () => {
  it('moves the wording to REJECTED and returns the undo state once confirmed', async () => {
    const calls: [string, string][] = [];
    const undo = await trashWording(wording('APPROVED'), async (id, status) => { calls.push([id, status]); });
    expect(calls).toEqual([['b1', 'REJECTED']]);
    expect(undo).toEqual({ id: 'b1', prev: 'APPROVED' });
  });

  it('rejects when the write fails, so no undo state is produced', async () => {
    await expect(trashWording(wording('PENDING'), async () => { throw new Error('server down'); })).rejects.toThrow('server down');
  });
});

describe('a failed trash', () => {
  it('leaves no undo state and reports the error', async () => {
    let undo: unknown = null;
    const errors: string[] = [];
    const ok = await attempt(async () => {
      undo = await trashWording(wording('PENDING'), async () => { throw new Error('PATCH failed'); });
    }, m => errors.push(m));
    expect(ok).toBe(false);
    expect(undo).toBeNull();
    expect(errors).toEqual(['PATCH failed']);
  });
});

describe('attempt', () => {
  it('returns true and reports nothing on success', async () => {
    const errors: string[] = [];
    expect(await attempt(async () => {}, m => errors.push(m))).toBe(true);
    expect(errors).toEqual([]);
  });

  it('falls back to a plain message for non-Error failures', async () => {
    const errors: string[] = [];
    expect(await attempt(async () => { throw 'nope'; }, m => errors.push(m))).toBe(false);
    expect(errors).toEqual(['Something went wrong. Try again.']);
  });
});
