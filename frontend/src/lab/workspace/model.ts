import { useEffect, useMemo, useRef, useState } from 'react';
import { type Project } from '../../lib/api';
import { isVanity, STORY_CAP } from '../stories/storyFixtures';
import { GLYPHS, PROJECT_FX } from '../project-split/data';
import { LENSES, LOOSE, RESERVE, STORIES, type Status, type StoryFx } from './data';

export type WBullet = { id: string; storyId: string | null; lens: string; text: string; status: Status; tags: string[] };
export type StoryMeta = { id: string; title: string; glyph: string; fits: string[] };
/** What the last Generate did: new stories, wordings written per lens. `live` while it streams. */
export type Run = { kind: 'new' | 'fill'; stories: string[]; perLens: Record<string, number>; full?: boolean; live?: boolean };

const ALL: StoryFx[] = [...STORIES, ...RESERVE];
const BY_ID: Record<string, StoryFx> = Object.fromEntries(ALL.map(s => [s.id, s]));
const META: Record<string, StoryMeta> = Object.fromEntries(
  ALL.map((s, i) => [s.id, { id: s.id, title: s.title, glyph: GLYPHS[i % GLYPHS.length], fits: LENSES.map(l => l.slug).filter(l => s.fits[l]) }]),
);

const wid = (storyId: string, lens: string) => `${storyId}:${lens}`;
const wording = (s: StoryFx, lens: string, status: Status = 'BULLET'): WBullet =>
  ({ id: wid(s.id, lens), storyId: s.id, lens, text: s.fits[lens], status, tags: s.tags });

function seed(): WBullet[] {
  return [
    ...STORIES.flatMap(s => Object.entries(s.seed ?? {}).map(([lens, st]) => wording(s, lens, st))),
    ...LOOSE.map(b => ({ ...b, storyId: null })),
  ];
}

/** An activity count is held back unless approved. */
export const selectable = (b: WBullet) => b.status === 'APPROVED' || !isVanity(b.text);
export const storyOf = (id: string | null): StoryMeta | null => (id ? META[id] ?? null : null);

const STEP_MS = 520;

export function useWorkspace() {
  const [bullets, setBullets] = useState<WBullet[]>(seed);
  const [project, setProject] = useState<Project>(PROJECT_FX);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [hoverStory, setHoverStory] = useState<string | null>(null);
  const [newIds, setNewIds] = useState<Set<string>>(new Set());
  const [pending, setPending] = useState<Set<string>>(new Set());
  const [run, setRun] = useState<Run | null>(null);
  const [removed, setRemoved] = useState<{ b: WBullet; index: number } | null>(null);
  const timers = useRef<number[]>([]);
  const undoTimer = useRef<number>();
  useEffect(() => () => { timers.current.forEach(clearTimeout); clearTimeout(undoTimer.current); }, []);
  const busy = pending.size > 0 || !!run?.live;

  const byStory = useMemo(() => {
    const m = new Map<string, WBullet[]>();
    for (const b of bullets) if (b.storyId) m.set(b.storyId, [...(m.get(b.storyId) ?? []), b]);
    return m;
  }, [bullets]);

  /** The one wording a resume prints per story: approved first, then the first usable. */
  const printed = useMemo(() => {
    const out = new Set<string>();
    for (const ws of byStory.values()) {
      const pick = ws.find(w => w.status === 'APPROVED') ?? ws.find(selectable);
      if (pick) out.add(pick.id);
    }
    for (const b of bullets) if (!b.storyId && selectable(b)) out.add(b.id);
    return out;
  }, [byStory, bullets]);

  const stories = [...byStory.keys()].map(id => META[id]);
  const loose = bullets.filter(b => !b.storyId);
  const used = stories.length + loose.length;
  const usable = [...byStory.values()].filter(ws => ws.some(selectable)).length + loose.filter(selectable).length;
  const room = Math.max(0, STORY_CAP - used);

  const has = (storyId: string, lens: string) => bullets.some(b => b.id === wid(storyId, lens));
  /** Lenses a story in the bank fits but has no wording for yet. */
  const missing = (storyId: string) => META[storyId].fits.filter(l => !has(storyId, l));
  const siblings = (b: WBullet) => (b.storyId ? (byStory.get(b.storyId) ?? []) : [b]);
  const selected = bullets.find(b => b.id === selectedId) ?? null;

  const patch = (id: string, p: Partial<WBullet>) => setBullets(bs => bs.map(b => (b.id === id ? { ...b, ...p } : b)));
  const toggle = (id: string) => setBullets(bs => bs.map(b => (b.id === id ? { ...b, status: b.status === 'APPROVED' ? 'BULLET' : 'APPROVED' } : b)));
  const setField = (k: keyof Project, v: string) => setProject(p => ({ ...p, [k]: v }));
  const add = (text: string, tags: string[], lens: string) => {
    const id = `new-${Date.now()}`;
    setBullets(bs => [{ id, storyId: null, lens, text, status: 'BULLET', tags }, ...bs]);
    setNewIds(new Set([id]));
  };

  /** Delete is the only way out; a short undo window brings it back. */
  function remove(id: string) {
    const index = bullets.findIndex(b => b.id === id);
    if (index < 0) return;
    setRemoved({ b: bullets[index], index });
    setBullets(bs => bs.filter(b => b.id !== id));
    if (selectedId === id) setSelectedId(null);
    clearTimeout(undoTimer.current);
    undoTimer.current = window.setTimeout(() => setRemoved(null), 5000);
  }
  function undo() {
    if (!removed) return;
    const { b, index } = removed;
    setBullets(bs => (bs.some(x => x.id === b.id) ? bs : [...bs.slice(0, index), b, ...bs.slice(index)]));
    setRemoved(null);
  }

  const later = (ms: number, fn: () => void) => { timers.current.push(window.setTimeout(fn, ms)); };

  /** New stories from the reserve, one wording per picked lens each fits. Streams in one story at a time. */
  function generate(lenses: string[]) {
    if (busy || lenses.length === 0) return;
    if (room === 0) { setRun({ kind: 'new', stories: [], perLens: {}, full: true }); return; }
    const have = new Set(byStory.keys());
    const found = RESERVE.filter(s => !have.has(s.id) && lenses.some(l => s.fits[l])).slice(0, Math.min(3, room));
    setRun({ kind: 'new', stories: [], perLens: {}, live: true });
    setNewIds(new Set());
    if (found.length === 0) { later(700, () => setRun(r => r && { ...r, live: false })); return; }
    found.forEach((s, i) => later(STEP_MS * (i + 1) + 300, () => {
      const fresh = lenses.filter(l => s.fits[l]).map(l => wording(s, l));
      setBullets(bs => [...fresh, ...bs]);
      setNewIds(n => new Set([...n, ...fresh.map(b => b.id)]));
      setRun(r => r && {
        ...r, stories: [...r.stories, s.id], live: i < found.length - 1,
        perLens: fresh.reduce((m, b) => ({ ...m, [b.lens]: (m[b.lens] ?? 0) + 1 }), r.perLens),
      });
    }));
  }

  /** More wordings for stories already in the bank: one story, one lens, one cell, or all. */
  function fill(opts: { storyId?: string; lenses?: string[] } = {}) {
    if (busy) return;
    const targets = stories
      .filter(s => !opts.storyId || s.id === opts.storyId)
      .flatMap(s => missing(s.id).filter(l => !opts.lenses || opts.lenses.includes(l)).map(l => ({ s: BY_ID[s.id], l })));
    if (targets.length === 0) return;
    setPending(new Set(targets.map(t => wid(t.s.id, t.l))));
    setRun({ kind: 'fill', stories: [], perLens: {}, live: true });
    later(700 + targets.length * 60, () => {
      const fresh = targets.map(t => wording(t.s, t.l));
      setBullets(bs => [...bs, ...fresh]);
      setNewIds(new Set(fresh.map(b => b.id)));
      setPending(new Set());
      setRun({ kind: 'fill', stories: [], perLens: fresh.reduce<Record<string, number>>((m, b) => ({ ...m, [b.lens]: (m[b.lens] ?? 0) + 1 }), {}) });
    });
  }

  /** Wordings Generate could still write for stories in the bank, per lens. */
  const open = Object.fromEntries(LENSES.map(l => [l.slug, stories.filter(s => s.fits.includes(l.slug) && !has(s.id, l.slug)).length]));

  return {
    bullets, project, setField, stories, loose, selected, selectedId, setSelectedId, hoverStory, setHoverStory,
    printed, siblings, used, usable, room, newIds, pending, run, busy, removed, open,
    has, missing, patch, toggle, add, remove, undo, generate, fill,
  };
}

export type WS = ReturnType<typeof useWorkspace>;
export { wid };
