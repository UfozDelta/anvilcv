// The write steps of the story bank, kept free of React so they can be tested directly.
import type { Bullet } from './api';

export type WordingStatus = Bullet['status'];

/** What Undo needs to put a trashed wording back. */
export interface UndoState {
  id: string;
  prev: WordingStatus;
}

/**
 * Soft delete: the wording moves to REJECTED. Resolves to the undo state only once the server has
 * confirmed the change; a failed PATCH rejects and no undo state exists.
 */
export async function trashWording(
  b: Bullet,
  setStatus: (id: string, status: WordingStatus) => Promise<unknown>,
): Promise<UndoState> {
  await setStatus(b.id, 'REJECTED');
  return { id: b.id, prev: b.status };
}

/**
 * Runs one write. On failure it reports the message and returns false, so the caller leaves its
 * state alone and only closes an editor on true.
 */
export async function attempt(work: () => Promise<void>, onError: (message: string) => void): Promise<boolean> {
  try {
    await work();
    return true;
  } catch (e) {
    onError(e instanceof Error && e.message ? e.message : 'Something went wrong. Try again.');
    return false;
  }
}
