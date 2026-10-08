import { useCallback, useEffect, useState } from 'react';
import { api, type Bullet, type RefitResponse, type Story, type StoriesResponse } from '../lib/api';
import { STORY_CAP } from '../lib/config';
import { useBulletPreview } from './useBulletPreview';

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
    await setStatus(b.id, b.status === 'APPROVED' ? 'PENDING' : 'APPROVED');
    await load();
  }

  async function edit(b: Bullet, text: string, tags: string[]) {
    await api.put<Bullet>(`/api/bullets/${b.id}`, { text, tags });
    await load();
  }

  /** Soft delete: the wording goes to REJECTED and stays there until Undo. */
  async function trash(b: Bullet) {
    setUndo({ id: b.id, prev: b.status });
    await setStatus(b.id, 'REJECTED');
    await load();
  }

  async function restore() {
    if (!undo) return;
    const u = undo;
    setUndo(null);
    await setStatus(u.id, u.prev);
    await load();
  }

  async function add(text: string, tags: string[], category: string) {
    await api.post<Bullet>(`/api/projects/${projectId}/bullets`, { text, tags, category });
    await load();
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
