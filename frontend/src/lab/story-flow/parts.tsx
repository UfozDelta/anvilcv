/* Shared pieces for /lab/story-flow. */
import { useState } from 'react';
import { FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { UndoBar } from '../../components/ledger/shared';
import { Meter } from '../project-split/parts';
import { REPO_MAP } from '../project-split/data';
import { LAB_CFG } from '../fixtures';
import { Spin, Trash } from '../workspace/parts';
import { LENS_OF, type Lens } from './data';
import { type Bullet, type SF } from './model';

/* ── Head: name, cap meter ── */

export function Head({ sf }: { sf: SF }) {
  return (
    <header className="ps-head">
      <div className="ps-head__id">
        <div className="eyebrow">← Projects</div>
        <h1 className="display ps-head__name">{sf.project.name}</h1>
      </div>
      <div className="ps-head__tools">
        <Meter used={sf.stories.length} usable={sf.usable} />
      </div>
    </header>
  );
}

/* ── Description: clamped, edit in place ── */

export function DescBlock({ sf }: { sf: SF }) {
  const [edit, setEdit] = useState(false);
  const [more, setMore] = useState(false);
  const d = sf.project.description;
  if (edit) {
    return (
      <div className="sf-desc" data-edit>
        <textarea className="field__textarea" aria-label="Description" value={d} autoFocus style={{ minHeight: 140 }}
          onChange={e => sf.setField('description', e.target.value)} />
        <div className="sf-desc__acts">
          <span className="ps-count">{d.length}c</span>
          <button className="minibtn" onClick={() => setEdit(false)}>Done</button>
        </div>
      </div>
    );
  }
  return (
    <div className="sf-desc">
      <p className="sf-desc__text" data-open={more || undefined} title="Description">{d || '—'}</p>
      <div className="sf-desc__acts">
        <button className="minibtn" aria-expanded={more} onClick={() => setMore(m => !m)}>{more ? 'Less' : 'More'}</button>
        <button className="minibtn" onClick={() => setEdit(true)}>Edit</button>
      </div>
    </div>
  );
}

/* ── Lens tag ── */

export function LensTag({ lens, big }: { lens: Lens; big?: boolean }) {
  const l = LENS_OF[lens];
  return <span className="sf-lens" data-lens={lens} data-big={big || undefined} title={l.tip}>{l.name}</span>;
}

/* ── One wording row: the text leads, metadata stays quiet ── */

/** Length mark, only when the wording won't fit its line budget. */
function FitMark({ text }: { text: string }) {
  const fit = fitOf(text, LAB_CFG);
  if (fit === 'OFF' || !needsRefit(fit)) return null;
  return <span className="sf-b__fit" title={fitHint(text, LAB_CFG)} aria-label={FIT_LABEL[fit]}>!</span>;
}

export function WordingRow({ sf, b, onEdit }: { sf: SF; b: Bullet; onEdit: () => void }) {
  const on = b.status === 'APPROVED';
  const prints = sf.wordings(b.storyId).length > 1 && sf.printed(b.storyId)?.id === b.id;
  return (
    <li className="sf-b" data-on={on || undefined} data-new={sf.newIds.has(b.id) || undefined}>
      <button type="button" className="sf-b__dot" aria-pressed={on} aria-label="Approved"
        title={on ? 'Approved: click to unapprove' : 'Approve'} onClick={() => sf.toggle(b.id)}>
        <svg viewBox="0 0 12 12" width="10" height="10" aria-hidden="true"><path d="M2.5 6.2l2.3 2.3 4.7-5" fill="none" stroke="currentColor" strokeWidth="1.8" /></svg>
      </button>
      <p className="sf-b__text">
        {prints && <span className="sf-b__prints" title="Prints by default; one wording per resume" aria-label="Prints">▶</span>}
        <RichText text={b.text} />
      </p>
      <div className="sf-b__meta">
        <FitMark text={b.text} />
        <LensTag lens={b.lens} />
        <span className="sf-b__acts">
          <button type="button" className="minibtn" title="Edit" aria-label="Edit" onClick={onEdit}>
            <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true"><path d="M3 13l.6-2.6L10.8 3.2l2 2-7.2 7.2L3 13zM9.6 4.4l2 2" fill="none" stroke="currentColor" strokeWidth="1.5" /></svg>
          </button>
          <Trash onClick={() => sf.remove(b.id)} />
        </span>
      </div>
    </li>
  );
}

export function Writing({ lens }: { lens: Lens }) {
  return (
    <li className="sf-writing" aria-live="polite">
      <Spin /> <LensTag lens={lens} />
    </li>
  );
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
      <div className="ps-tools">
        <span className="label ws-repo" title="Linked repo, branch and pinned commit">
          <a href={p.githubUrl ?? '#'} target="_blank" rel="noreferrer">{repo}</a> · {p.repoBranch} @ {p.repoCommitSha?.slice(0, 7)}
        </span>
        <span className="row" style={{ gap: 6 }}>
          <button className="minibtn" title="Pull latest commit">↻ Pull</button>
          <button className="minibtn" title="Link another repo">Change</button>
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
  return <>{Array.from({ length: n }, (_, i) => <div key={i} className="sf-finding"><Spin /></div>)}</>;
}
