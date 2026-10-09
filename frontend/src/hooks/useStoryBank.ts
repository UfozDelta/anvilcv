import { useCallback, useEffect, useState } from 'react';
import { api, type Bullet, type RefitResponse, type Story, type StoriesResponse } from '../lib/api';
import { STORY_CAP } from '../lib/config';
import { useBulletPreview } from './useBulletPreview';
import { attempt, trashWording } from '../lib/storyBankActions';

/** Load, edit and job wiring for one project's story bank. Every write reloads, so counts and stories stay true. */
export function useStoryBank(projectId: string) {
  const [stories, setStories] = useState<Story[]>([]);
  const [cap, setCap] = useState(STORY_CAP);
  const [bullets, setBullets] = useState<Bullet[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  /** The last trashed wording, so Undo can put its status back. */
  const [undo, setUndo] = useState<{ id: string; prev: Bullet['status'] } | null>(null);
  const pdf = useBulletPreview();

  const load = useCallback(async () => {
    try {
      const [s, b] = await Promise.all([
        api.get<StoriesResponse>(`/api/projects/${projectId}/stories`),
        api.get<Bullet[]>(`/api/projects/${projectId}/bullets`),
      ]);
      setStories(s.stories);
      setCap(s.cap);
      setBullets(b);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load the bank.');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => { load(); }, [load]);

  async function setStatus(id: string, status: Bullet['status']) {
    await api.patch<Bullet>(`/api/bullets/${id}/status`, { status });
  }

  /** Approve toggles between APPROVED and PENDING. */
  async function approve(b: Bullet) {
    await attempt(async () => {
      await setStatus(b.id, b.status === 'APPROVED' ? 'PENDING' : 'APPROVED');
      await load();
    }, setError);
  }

  /** Resolves true when saved, so the editor closes only then. */
  async function edit(b: Bullet, text: string, tags: string[]): Promise<boolean> {
    return attempt(async () => {
      await api.put<Bullet>(`/api/bullets/${b.id}`, { text, tags });
      await load();
    }, setError);
  }

  /** Soft delete. The undo state is recorded only after the server confirms the trash. */
  async function trash(b: Bullet) {
    await attempt(async () => {
      setUndo(await trashWording(b, setStatus));
      await load();
    }, setError);
  }

  async function restore() {
    const u = undo;
    if (!u) return;
    await attempt(async () => {
      await setStatus(u.id, u.prev);
      setUndo(null);
      await load();
    }, setError);
  }

  /** Resolves true when saved, so the add form closes only then. */
  async function add(text: string, tags: string[], category: string): Promise<boolean> {
    return attempt(async () => {
      await api.post<Bullet>(`/api/projects/${projectId}/bullets`, { text, tags, category });
      await load();
    }, setError);
  }

  async function refit(): Promise<RefitResponse> {
    const r = await api.post<RefitResponse>(`/api/projects/${projectId}/bullets/refit`);
    setBullets(r.bullets);
    return r;
  }

  /** Job wiring for EventStream: the page passes these straight through. */
  const newStoriesJob = (subsystems: string[] = []) => ({
    submitUrl: `/api/projects/${projectId}/stories/submit`,
    submitBody: { subsystems },
  });

  const wordingsJob = (storyId: string, lenses: string[], subsystems: string[] = []) => ({
    submitUrl: `/api/projects/${projectId}/stories/${storyId}/wordings/submit`,
    submitBody: { lenses, subsystems },
  });

  const pollUrl = (jobId: string) => `/api/projects/jobs/${jobId}/progress`;

  return {
    loading, error, stories, cap, bullets, undo, pdf, load,
    approve, edit, trash, restore, add, refit,
    newStoriesJob, wordingsJob, pollUrl,
  };
}
