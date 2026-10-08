/* Story → lens wordings. Generate finds stories (one bullet each); per story, get more lenses. */
import { useEffect, useMemo, useRef, useState } from 'react';
import { type Project } from '../../lib/api';
import { STORY_CAP } from '../stories/storyFixtures';
import { PROJECT_FX } from '../project-split/data';
import { BY_ID, LENSES, POOL, SEED, type Lens } from './data';

export type Status = 'BULLET' | 'APPROVED';
export type Bullet = { id: string; storyId: string; lens: Lens; text: string; status: Status; tags: string[] };
type Bank = { stories: string[]; bullets: Bullet[] };

export const wid = (storyId: string, lens: Lens) => `${storyId}:${lens}`;
const make = (storyId: string, lens: Lens, status: Status = 'BULLET'): Bullet =>
  ({ id: wid(storyId, lens), storyId, lens, text: BY_ID[storyId].fits[lens] ?? '', status, tags: [] });

const seed = (): Bank => ({
  stories: SEED.map(s => s.id),
  bullets: SEED.flatMap(s => s.lenses.map(l => make(s.id, l, s.approved?.includes(l) ? 'APPROVED' : 'BULLET'))),
});

export const fits = (storyId: string, lens: Lens) => !!BY_ID[storyId]?.fits[lens];

export function useStoryFlow() {
  const [bank, setBank] = useState<Bank>(seed);
  const [project, setProject] = useState<Project>(PROJECT_FX);
  const [newIds, setNewIds] = useState<Set<string>>(new Set());
  /** Wording ids being written, and how many stories are being found. */
  const [pending, setPending] = useState<Set<string>>(new Set());
  const [finding, setFinding] = useState(0);
  const [last, setLast] = useState<{ added: number; full?: boolean } | null>(null);
  const [removed, setRemoved] = useState<{ name: string; prev: Bank } | null>(null);
  const timers = useRef<number[]>([]);
  const undoTimer = useRef<number>();
  useEffect(() => () => { timers.current.forEach(clearTimeout); clearTimeout(undoTimer.current); }, []);
  const later = (ms: number, fn: () => void) => { timers.current.push(window.setTimeout(fn, ms)); };

  const { stories, bullets } = bank;
  const byStory = useMemo(() => {
    const m = new Map<string, Bullet[]>(stories.map(id => [id, []]));
    for (const b of bullets) m.get(b.storyId)?.push(b);
    for (const ws of m.values()) ws.sort((a, b) => LENSES.findIndex(l => l.slug === a.lens) - LENSES.findIndex(l => l.slug === b.lens));
    return m;
  }, [stories, bullets]);

  const used = stories.length + finding;
  const usable = [...byStory.values()].filter(ws => ws.length > 0).length;
  const room = Math.max(0, STORY_CAP - stories.length);
  const left = POOL.filter(s => !stories.includes(s.id)).length;

  const wordings = (storyId: string) => byStory.get(storyId) ?? [];
  const has = (storyId: string, lens: Lens) => bullets.some(b => b.id === wid(storyId, lens));
  const isPending = (storyId: string, lens: Lens) => pending.has(wid(storyId, lens));
  /** Lenses this story fits and hasn't got (or isn't getting) yet. */
  const open = (storyId: string) => LENSES.map(l => l.slug).filter(l => fits(storyId, l) && !has(storyId, l) && !isPending(storyId, l));
  /** The one wording that prints: approved first, then the arriving lens, then the first. */
  const printed = (storyId: string) => {
    const ws = wordings(storyId);
    return ws.find(w => w.status === 'APPROVED') ?? ws.find(w => w.lens === BY_ID[storyId].best) ?? ws[0] ?? null;
  };

  const setBullets = (fn: (bs: Bullet[]) => Bullet[]) => setBank(b => ({ ...b, bullets: fn(b.bullets) }));
  const toggle = (id: string) => setBullets(bs => bs.map(b => (b.id === id ? { ...b, status: b.status === 'APPROVED' ? 'BULLET' : 'APPROVED' } : b)));
  const patch = (id: string, p: Partial<Bullet>) => setBullets(bs => bs.map(b => (b.id === id ? { ...b, ...p } : b)));
  const setField = (k: keyof Project, v: string) => setProject(p => ({ ...p, [k]: v }));

  /** Trash a wording; the story goes too when it was its last. Undo restores both. */
  function remove(id: string) {
    const b = bullets.find(x => x.id === id);
    if (!b) return;
    const lone = bullets.filter(x => x.storyId === b.storyId).length === 1;
    setRemoved({ name: `${BY_ID[b.storyId].title} · ${LENSES.find(l => l.slug === b.lens)?.name}`, prev: bank });
    setBank(k => ({ stories: lone ? k.stories.filter(s => s !== b.storyId) : k.stories, bullets: k.bullets.filter(x => x.id !== id) }));
    clearTimeout(undoTimer.current);
    undoTimer.current = window.setTimeout(() => setRemoved(null), 5000);
  }
  function undo() {
    if (!removed) return;
    setBank(removed.prev);
    setRemoved(null);
  }

  /** Project-level: find new stories, one bullet each in its best lens. Never repeats. */
  function generate() {
    if (finding > 0) return;
    if (room === 0) { setLast({ added: 0, full: true }); return; }
    const found = POOL.filter(s => !stories.includes(s.id)).slice(0, Math.min(2, room));
    setLast(null);
    if (found.length === 0) { later(500, () => setLast({ added: 0 })); return; }
    setFinding(found.length);
    setNewIds(new Set());
    found.forEach((s, i) => later(650 * (i + 1), () => {
      const b = make(s.id, s.best);
      setBank(k => ({ stories: [s.id, ...k.stories], bullets: [b, ...k.bullets] }));
      setNewIds(n => new Set([...n, s.id, b.id]));
      setFinding(f => f - 1);
      if (i === found.length - 1) setLast({ added: found.length });
    }));
  }

  /** Per story: write wordings for the chosen lenses. */
  function getLenses(storyId: string, lenses: Lens[]) {
    const go = lenses.filter(l => fits(storyId, l) && !has(storyId, l) && !isPending(storyId, l));
    if (go.length === 0) return;
    setPending(p => new Set([...p, ...go.map(l => wid(storyId, l))]));
    go.forEach((l, i) => later(700 + i * 380, () => {
      const b = make(storyId, l);
      setBullets(bs => (bs.some(x => x.id === b.id) ? bs : [...bs, b]));
      setNewIds(n => new Set([...n, b.id]));
      setPending(p => { const n = new Set(p); n.delete(b.id); return n; });
    }));
  }

  return {
    project, setField, stories, bullets, wordings, has, isPending, open, printed,
    used, usable, room, left, finding, last, newIds, pending, removed,
    toggle, patch, remove, undo, generate, getLenses,
  };
}

export type SF = ReturnType<typeof useStoryFlow>;
