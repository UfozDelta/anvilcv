import { useEffect, useMemo, useRef, useState } from 'react';
import { CATEGORIES, type Project } from '../../lib/api';
import {
  DISMISSED, isVanity, LOOSE, RESERVE, STORIES, STORY_CAP,
  type Evidence, type StoryFx, type WStatus,
} from '../stories/storyFixtures';
import { GLYPHS, LOOSE_META, PROJECT_FX, STORY_TAGS } from './data';

export type SBullet = {
  id: string;
  storyId: string | null;
  lens: string;
  text: string;
  status: WStatus;
  tags: string[];
};

export type StoryMeta = { id: string; title: string; evidence: Evidence[]; glyph: string };

export type Group = { slug: string; label: string; rows: SBullet[] };

const ALL_STORIES = [...STORIES, ...RESERVE, ...DISMISSED];
const STORY: Record<string, StoryMeta> = Object.fromEntries(
  ALL_STORIES.map((s, i) => [s.id, { id: s.id, title: s.title, evidence: s.evidence, glyph: GLYPHS[i % GLYPHS.length] }]),
);

const toBullets = (s: StoryFx): SBullet[] => s.byLens.map(w => ({
  id: w.id, storyId: s.id, lens: w.lens ?? 'backend', text: w.text, status: w.status, tags: STORY_TAGS[s.id] ?? [],
}));

function seed(): SBullet[] {
  return [
    ...[...STORIES, ...DISMISSED].flatMap(toBullets),
    ...LOOSE.map(b => ({ id: b.id, storyId: null, lens: LOOSE_META[b.id].lens, text: b.text, status: b.status, tags: LOOSE_META[b.id].tags })),
  ];
}

/** A pending activity count is held back; approved stays. Same rule as /lab/stories. */
export const selectable = (b: SBullet) => b.status !== 'REJECTED' && (b.status === 'APPROVED' || !isVanity(b.text));

export function storyOf(id: string | null): StoryMeta | null {
  return id ? STORY[id] ?? null : null;
}

export function useSplitBank() {
  const [bullets, setBullets] = useState<SBullet[]>(seed);
  const [project, setProject] = useState<Project>(PROJECT_FX);
  const [selectedId, setSelectedId] = useState<string | null>('a-eta-1');
  const [hoverStory, setHoverStory] = useState<string | null>(null);
  const [newIds, setNewIds] = useState<Set<string>>(new Set());
  const [run, setRun] = useState<{ added: number; dropped: number; full?: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [source, setSource] = useState<string | null>(null);
  const timer = useRef<number>();
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const byStory = useMemo(() => {
    const m = new Map<string, SBullet[]>();
    for (const b of bullets) if (b.storyId) m.set(b.storyId, [...(m.get(b.storyId) ?? []), b]);
    return m;
  }, [bullets]);

  /** The one wording a resume prints per story: approved first, then the first usable. */
  const printed = useMemo(() => {
    const out = new Set<string>();
    for (const ws of byStory.values()) {
      const pick = ws.find(w => w.status === 'APPROVED' && selectable(w)) ?? ws.find(selectable);
      if (pick) out.add(pick.id);
    }
    for (const b of bullets) if (!b.storyId && selectable(b)) out.add(b.id);
    return out;
  }, [byStory, bullets]);

  const liveStories = [...byStory.entries()].filter(([, ws]) => ws.some(w => w.status !== 'REJECTED'));
  const looseLive = bullets.filter(b => !b.storyId && b.status !== 'REJECTED');
  const used = liveStories.length + looseLive.length;
  const usable = liveStories.filter(([, ws]) => ws.some(selectable)).length + looseLive.filter(selectable).length;

  const groups: Group[] = useMemo(() => CATEGORIES
    .map(c => ({
      slug: c.slug, label: c.label,
      rows: bullets.filter(b => b.lens === c.slug)
        .sort((x, y) => Number(newIds.has(y.id)) - Number(newIds.has(x.id))),
    }))
    .filter(g => g.rows.length > 0), [bullets, newIds]);

  const siblings = (b: SBullet) => (b.storyId ? (byStory.get(b.storyId) ?? []) : [b]);
  const selected = bullets.find(b => b.id === selectedId) ?? null;

  /** Bullets whose story cites the focused source (a context field or a repo subsystem). */
  const cites = (b: SBullet) => {
    if (!source) return false;
    const ev = storyOf(b.storyId)?.evidence ?? [];
    return ev.some(e => e.from === source);
  };

  const patch = (id: string, p: Partial<SBullet>) => setBullets(bs => bs.map(b => (b.id === id ? { ...b, ...p } : b)));
  const remove = (id: string) => { setBullets(bs => bs.filter(b => b.id !== id)); if (selectedId === id) setSelectedId(null); };
  const add = (text: string, tags: string[], lens: string) => {
    const id = `new-${Date.now()}`;
    setBullets(bs => [{ id, storyId: null, lens, text, status: 'PENDING', tags }, ...bs]);
    setNewIds(new Set([id]));
  };
  const setField = (k: keyof Project, v: string) => setProject(p => ({ ...p, [k]: v }));

  function generate() {
    if (busy) return;
    const room = Math.max(0, STORY_CAP - used);
    if (room === 0) { setRun({ added: 0, dropped: 0, full: true }); return; }
    setBusy(true);
    timer.current = window.setTimeout(() => {
      const have = new Set(bullets.map(b => b.storyId));
      const add = RESERVE.filter(s => !have.has(s.id)).slice(0, Math.min(2, room));
      const fresh = add.flatMap(toBullets);
      setBullets(bs => [...fresh, ...bs]);
      setNewIds(new Set(fresh.map(b => b.id)));
      setRun({ added: add.length, dropped: 2 });
      setBusy(false);
    }, 1100);
  }

  return {
    bullets, groups, project, setField, selected, selectedId, setSelectedId,
    hoverStory, setHoverStory, printed, siblings, used, usable, newIds,
    run, busy, generate, patch, remove, add, source, setSource, cites,
  };
}

export type Bank = ReturnType<typeof useSplitBank>;
