/* Shared pieces for /lab/story-flow. */
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { UndoBar } from '../../components/ledger/shared';
import { Fit, Spin, Trash } from '../../components/ledger/parts';
import { charCount, FIT_LABEL, fitOf, needsRefit } from '../../lib/bulletLength';
import { STORY_CAP } from '../../lib/config';
import { LAB_CFG } from '../fixtures';
import { REPO_MAP } from '../project-split/data';
import { LENS_OF, type Lens } from './data';
import { type Bullet, type SF } from './model';

/* ── Head: name, story count ── */

export function Head({ sf }: { sf: SF }) {
  return (
    <header className="sf-head">
      <div className="sf-head__id">
        <Link to="/lab/lists" className="eyebrow sf-back">← Projects</Link>
        <h1 className="display sf-head__name">{sf.project.name}</h1>
      </div>
      <div className="sf-head__tools">
        <StoryCount n={sf.stories.length} />
      </div>
    </header>
  );
}

/** Plain story count; ⚠ when thin, "Full" only at the cap. */
function StoryCount({ n }: { n: number }) {
  const thin = n < 3, full = n >= STORY_CAP;
  return (
    <span className="sf-count" data-thin={thin || undefined}>
      <b>{n}</b> {n === 1 ? 'story' : 'stories'}{thin && ' ⚠ few'}{full && <span className="sf-count__full">Full</span>}
    </span>
  );
}

/* ── Description: clamped, edit in place ── */

export function DescBlock({ sf }: { sf: SF }) {
  const [edit, setEdit] = useState(false);
  const [more, setMore] = useState(false);
  const [clamped, setClamped] = useState(false);
  const text = useRef<HTMLParagraphElement>(null);
  const d = sf.project.description;

  // More/Less only when the 2-line clamp actually hides text; re-check on resize.
  useLayoutEffect(() => {
    const el = text.current;
    if (!el || more) return;
    const check = () => setClamped(el.scrollHeight > el.clientHeight + 1);
    check();
    const ro = new ResizeObserver(check);
    ro.observe(el);
    return () => ro.disconnect();
  }, [d, more, edit]);

  if (edit) {
    return (
      <div className="sf-desc" data-edit>
        <textarea className="field__textarea" aria-label="Description" value={d} autoFocus style={{ minHeight: 140 }}
          onChange={e => sf.setField('description', e.target.value)} />
        <div className="sf-desc__acts">
          <span className="sf-desc__len">{d.length}c</span>
          <button type="button" className="minibtn" onClick={() => setEdit(false)}>Done</button>
        </div>
      </div>
    );
  }
  if (!d.trim()) {
    return (
      <div className="sf-desc" data-empty>
        <button type="button" className="sf-desc__add" onClick={() => setEdit(true)}>Add description</button>
      </div>
    );
  }
  return (
    <div className="sf-desc">
      <p ref={text} className="sf-desc__text" data-open={more || undefined}>{d}</p>
      <div className="sf-desc__acts">
        {(clamped || more) && <button type="button" className="minibtn" aria-expanded={more} onClick={() => setMore(m => !m)}>{more ? 'Less' : 'More'}</button>}
        <button type="button" className="minibtn" onClick={() => setEdit(true)}>Edit</button>
      </div>
    </div>
  );
}

/* ── Lens tag ── */

export function LensTag({ lens, big }: { lens: Lens; big?: boolean }) {
  const l = LENS_OF[lens];
  return <span className="sf-lens" data-lens={lens} data-big={big || undefined}>{l.name}</span>;
}

/* ── One wording row: text, then a quiet side column ── */

export function WordingRow({ sf, b, onEdit }: { sf: SF; b: Bullet; onEdit: () => void }) {
  const on = b.status === 'APPROVED';
  return (
    <li className="sf-b" data-on={on || undefined} data-new={sf.newIds.has(b.id) || undefined}>
      <div className="sf-b__text"><RichText text={b.text} /></div>
      <div className="sf-b__side">
        <span className="sf-b__meta">
          <Fit text={b.text} cfg={LAB_CFG} />
        </span>
        <span className="sf-b__acts">
          <button type="button" className="sf-b__ok" aria-pressed={on} onClick={() => sf.toggle(b.id)}>{on ? '✓ Approved' : 'Approve'}</button>
          <button type="button" className="minibtn" onClick={onEdit}>Edit</button>
          <Trash onClick={() => sf.remove(b.id)} />
        </span>
      </div>
    </li>
  );
}

/** The same row, editing its text in place. Esc cancels, Ctrl/⌘+Enter saves. */
export function EditRow({ b, onSave, onCancel }: { b: Bullet; onSave: (text: string) => void; onCancel: () => void }) {
  const [t, setT] = useState(b.text);
  const fit = fitOf(t, LAB_CFG);
  const save = () => { if (t.trim()) onSave(t.trim()); };
  return (
    <li className="sf-b" data-edit data-on={b.status === 'APPROVED' || undefined}>
      <textarea className="field__textarea sf-b__input" aria-label="Bullet text" value={t} autoFocus rows={3}
        onChange={e => setT(e.target.value)}
        onKeyDown={e => {
          if (e.key === 'Escape') onCancel();
          if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); save(); }
        }} />
      <div className="sf-b__side">
        <span className="sf-b__meta">
          <span className="ps-fit" data-bad={needsRefit(fit) || undefined}>{needsRefit(fit) && `⚠ ${FIT_LABEL[fit]} · `}{charCount(t)}c</span>
        </span>
        <span className="sf-b__acts">
          <button type="button" className="minibtn" disabled={!t.trim()} onClick={save}>Save</button>
          <button type="button" className="minibtn" onClick={onCancel}>Cancel</button>
        </span>
      </div>
    </li>
  );
}

export function Writing({ lens }: { lens: Lens }) {
  return (
    <li className="sf-writing">
      <Spin label={`Writing ${LENS_OF[lens].name}`} /> Writing…
    </li>
  );
}

/**
 * One polite live region per pane: says what is being written, then "Done" (or `done`) once it settles.
 * Stays mounted so the first message is announced.
 */
export function PaneStatus({ busy, done = 'Done' }: { busy: string | null; done?: string }) {
  const was = useRef(false);
  const [msg, setMsg] = useState('');
  useEffect(() => {
    if (busy) { was.current = true; setMsg(busy); }
    else if (was.current) { was.current = false; setMsg(done); }
  }, [busy, done]);
  return <p role="status" className="sr-only">{msg}</p>;
}

/* ── Repo (as in /lab/workspace) ── */

export function RepoPane({ sf }: { sf: SF }) {
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const p = sf.project;
  const repo = (p.githubUrl ?? '').replace('https://github.com/', '');
  const toggle = (name: string) => setPicked(s => {
    const n = new Set(s);
    n.has(name) ? n.delete(name) : n.add(name);
    return n;
  });
  return (
    <div className="stack-sm">
      <div className="sf-tools">
        <span className="label sf-repo">
          <a href={p.githubUrl ?? '#'} target="_blank" rel="noreferrer">{repo}</a> · {p.repoBranch} @ {p.repoCommitSha?.slice(0, 7)}
        </span>
        <span className="row" style={{ gap: 8 }}>
          <button type="button" className="minibtn">↻ Pull</button>
          <button type="button" className="minibtn">Change</button>
        </span>
      </div>
      <RepoMapView map={REPO_MAP} picked={picked} onToggle={toggle} onOpenFile={() => {}} />
    </div>
  );
}

export function Undo({ sf }: { sf: SF }) {
  return <UndoBar name={sf.removed?.name ?? null} onUndo={sf.undo} />;
}

/** Placeholder rows while the project Generate is finding stories. */
export function Finding({ n }: { n: number }) {
  return <>{Array.from({ length: n }, (_, i) => <div key={i} className="sf-finding"><Spin label="Finding a story" /></div>)}</>;
}
