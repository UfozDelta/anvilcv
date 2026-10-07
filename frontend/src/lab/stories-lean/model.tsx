import { useEffect, useRef, useState } from 'react';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { LAB_CFG } from '../fixtures';
import {
  DISMISSED, isVanity, LAST_RUN, LENS_LABEL, LOOSE, RESERVE, STORIES, STORY_CAP,
  type Evidence, type LooseBullet, type StoryFx, type WStatus, type Wording,
} from '../stories/storyFixtures';

export type Scenario = 'healthy' | 'thin' | 'full';
export type Lane = 'PENDING' | 'APPROVED' | 'REJECTED';
export type Run = { added: number; dropped: number; when: string; full?: boolean };

function seed(s: Scenario): { stories: StoryFx[]; loose: LooseBullet[] } {
  if (s === 'thin') return { stories: STORIES.slice(0, 2), loose: [] };
  if (s === 'full') return { stories: [...STORIES, ...RESERVE, ...DISMISSED], loose: LOOSE };
  return { stories: [...STORIES, ...DISMISSED], loose: LOOSE };
}

function initialStatus(): Record<string, WStatus> {
  const out: Record<string, WStatus> = {};
  for (const s of [...STORIES, ...RESERVE, ...DISMISSED]) for (const w of [...s.byLens, ...s.general]) out[w.id] = w.status;
  for (const b of LOOSE) out[b.id] = b.status;
  return out;
}

/** Same rule as /lab/stories: a pending activity count is held back, approved stays. */
const selectable = (status: WStatus, text: string) =>
  status !== 'REJECTED' && (status === 'APPROVED' || !isVanity(text));

const FIRST_RUN: Run = { added: LAST_RUN.kept.length, dropped: LAST_RUN.overlaps.length + LAST_RUN.noEvidence, when: '2d' };

export function useBank() {
  const [scenario, setScenarioRaw] = useState<Scenario>('healthy');
  const [bank, setBank] = useState(() => seed('healthy'));
  const [status, setStatus] = useState(initialStatus);
  const [lensesOn, setLensesOn] = useState(true);
  const [newIds, setNewIds] = useState<Set<string>>(new Set());
  const [run, setRun] = useState<Run>(FIRST_RUN);
  const [busy, setBusy] = useState(false);
  const timer = useRef<number>();
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const ws = (s: StoryFx): Wording[] => (lensesOn ? s.byLens : s.general);
  const laneOf = (s: StoryFx): Lane => {
    const st = ws(s).map(w => status[w.id]);
    if (st.every(x => x === 'REJECTED')) return 'REJECTED';
    return st.includes('APPROVED') ? 'APPROVED' : 'PENDING';
  };
  const live = bank.stories.filter(s => laneOf(s) !== 'REJECTED')
    .sort((x, y) => Number(newIds.has(y.id)) - Number(newIds.has(x.id)));
  const dismissed = bank.stories.filter(s => laneOf(s) === 'REJECTED');
  const usable = live.filter(s => ws(s).some(w => selectable(status[w.id], w.text))).length
    + bank.loose.filter(b => selectable(status[b.id], b.text)).length;
  const room = Math.max(0, STORY_CAP - live.length);

  /** The one wording a resume would print: approved first, then the first usable. */
  const prints = (s: StoryFx) => {
    const list = ws(s);
    return (list.find(w => status[w.id] === 'APPROVED' && selectable(status[w.id], w.text))
      ?? list.find(w => selectable(status[w.id], w.text)))?.id;
  };

  const setOne = (id: string, st: WStatus) => setStatus(m => ({ ...m, [id]: st }));
  const setStory = (s: StoryFx, st: WStatus) => setStatus(m => {
    const next = { ...m };
    for (const w of ws(s)) next[w.id] = st;
    return next;
  });

  function setScenario(s: Scenario) {
    window.clearTimeout(timer.current);
    setBusy(false);
    setScenarioRaw(s);
    setBank(seed(s));
    setStatus(initialStatus());
    setNewIds(new Set());
    setRun(FIRST_RUN);
  }

  function generate() {
    if (busy) return;
    if (room === 0) { setRun({ added: 0, dropped: 0, when: 'now', full: true }); return; }
    setBusy(true);
    timer.current = window.setTimeout(() => {
      const have = new Set(bank.stories.map(s => s.id));
      const add = RESERVE.filter(s => !have.has(s.id)).slice(0, Math.min(2, room));
      setBank(b => ({ ...b, stories: [...add, ...b.stories] }));
      setNewIds(new Set(add.map(s => s.id)));
      setRun({ added: add.length, dropped: 2, when: 'now' });
      setBusy(false);
    }, 1100);
  }

  return {
    scenario, setScenario, lensesOn, setLensesOn, status, ws, laneOf, prints,
    live, dismissed, loose: bank.loose, usable, newIds, run, busy, generate, setOne, setStory,
  };
}

export type Bank = ReturnType<typeof useBank>;

/* ── Small shared parts ── */

export function Meter({ used, usable }: { used: number; usable: number }) {
  const thin = usable < 3;
  return (
    <div className="sl-meter" data-thin={thin || undefined} title={`${used} of ${STORY_CAP} slots, ${usable} usable`}>
      <span className="sl-meter__cells" aria-hidden="true">
        {Array.from({ length: STORY_CAP }, (_, i) => <i key={i} data-on={i < used || undefined} />)}
      </span>
      <span className="sl-meter__n"><b>{used}</b>/{STORY_CAP}</span>
      <span className="sl-meter__use" title={thin ? 'Under 3 usable: entry may print short' : 'Usable on a resume'}>
        {thin ? '⚠' : '✓'} {usable}
      </span>
    </div>
  );
}

export function Lenses({ ids, general }: { ids: string[]; general: boolean }) {
  return (
    <span className="sl-lenses">
      {ids.map(l => <span key={l} className="sl-lens" data-auto={general || undefined} title={general ? 'Detected' : 'Lens'}>{LENS_LABEL[l]}</span>)}
    </span>
  );
}

/** A wording's own tag: its lens when lenses are on, short/long when off. */
export function WTag({ w }: { w: Wording }) {
  const label = w.lens ? LENS_LABEL[w.lens] : w.form === 'short' ? 'Short' : w.form === 'long' ? 'Long' : null;
  return label ? <span className="sl-wtag">{label}</span> : null;
}

export function Fit({ text }: { text: string }) {
  const fit = fitOf(text, LAB_CFG);
  const bad = needsRefit(fit);
  const vanity = isVanity(text);
  return (
    <>
      <span className="sl-fit" data-bad={bad || undefined} title={`${FIT_LABEL[fit]}, ${charCount(text)} chars${bad ? `. ${fitHint(text, LAB_CFG)}` : ''}`}>
        {bad ? '⚠ ' : ''}{estimatedLines(text)}L
      </span>
      {vanity && <span className="sl-fit" data-bad title="Activity count: held back unless approved">#</span>}
    </>
  );
}

export function Prints() {
  return <span className="sl-prints" title="Prints on the resume" aria-label="Prints">▶</span>;
}

/** ✓ / ✕ pair. Pressing the active one returns it to pending. */
export function Verdict({ value, onSet, label = 'wording' }: { value: WStatus; onSet: (s: WStatus) => void; label?: string }) {
  return (
    <span className="sl-verdict" role="group" aria-label={`${label} status`}>
      <button type="button" data-s="APPROVED" aria-pressed={value === 'APPROVED'} title="Approve"
        onClick={() => onSet(value === 'APPROVED' ? 'PENDING' : 'APPROVED')}>✓</button>
      <button type="button" data-s="REJECTED" aria-pressed={value === 'REJECTED'} title="Reject"
        onClick={() => onSet(value === 'REJECTED' ? 'PENDING' : 'REJECTED')}>✕</button>
    </span>
  );
}

/** Evidence stays folded: hover shows the quotes as a tooltip, a press opens them inline. */
export function Proof({ ev, open, onToggle }: { ev: Evidence[]; open: boolean; onToggle: () => void }) {
  if (ev.length === 0) return null;
  return (
    <button type="button" className="sl-proof" aria-expanded={open} onClick={onToggle}
      title={ev.map(e => `“${e.quote}” (${e.from})`).join('\n')}>
      “ {ev.length}
    </button>
  );
}

export function ProofBody({ ev }: { ev: Evidence[] }) {
  return (
    <ul className="sl-quotes">
      {ev.map(e => <li key={e.quote}><q>{e.quote}</q> <span>{e.from}</span></li>)}
    </ul>
  );
}

export function Dots({ ws, status }: { ws: Wording[]; status: Record<string, WStatus> }) {
  return (
    <span className="sl-dots" aria-label={ws.map(w => status[w.id].toLowerCase()).join(', ')}>
      {ws.map(w => <i key={w.id} data-s={status[w.id]} />)}
    </span>
  );
}

export function useToggleSet() {
  const [set, setSet] = useState<Set<string>>(new Set());
  const toggle = (id: string) => setSet(p => {
    const n = new Set(p);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  return [set, toggle] as const;
}
