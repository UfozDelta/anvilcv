/* Shared pieces for /lab/story-flow. */
import { useState } from 'react';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { UndoBar } from '../../components/ledger/shared';
import { STORY_CAP } from '../stories/storyFixtures';
import { REPO_MAP } from '../project-split/data';
import { Fit, Spin, Trash } from '../workspace/parts';
import { LENS_OF, type Lens } from './data';
import { type Bullet, type SF } from './model';

/* ── Head: name, story count ── */

export function Head({ sf }: { sf: SF }) {
  return (
    <header className="ps-head">
      <div className="ps-head__id">
        <div className="eyebrow">← Projects</div>
        <h1 className="display ps-head__name">{sf.project.name}</h1>
      </div>
      <div className="ps-head__tools">
        <StoryCount n={sf.stories.length} />
      </div>
    </header>
  );
}

/** Plain story count; ⚠ when thin, "Full" only at the cap. */
function StoryCount({ n }: { n: number }) {
  const thin = n < 3, full = n >= STORY_CAP;
  return (
    <span className="sf-count" data-thin={thin || undefined}
      title={full ? `Bank full (${STORY_CAP})` : thin ? 'Few stories: entry may print short' : undefined}>
      <b>{n}</b> {n === 1 ? 'story' : 'stories'}{thin && ' ⚠'}{full && <span className="sf-count__full">Full</span>}
    </span>
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

/* ── One wording row: text, then a quiet side column ── */

export function WordingRow({ sf, b, onEdit }: { sf: SF; b: Bullet; onEdit: () => void }) {
  const on = b.status === 'APPROVED';
  return (
    <li className="sf-b" data-on={on || undefined} data-new={sf.newIds.has(b.id) || undefined}>
      <div className="sf-b__text"><RichText text={b.text} /></div>
      <div className="sf-b__side">
        <span className="sf-b__meta">
          <Fit text={b.text} />
        </span>
        <span className="sf-b__acts">
          <button type="button" className="sf-b__ok" aria-pressed={on} title={on ? 'Approved: click to unapprove' : 'Mark approved'}
            onClick={() => sf.toggle(b.id)}>{on ? '✓ Approved' : '✓ Approve'}</button>
          <button type="button" className="minibtn" onClick={onEdit}>Edit</button>
          <Trash onClick={() => sf.remove(b.id)} />
        </span>
      </div>
    </li>
  );
}

export function Writing() {
  return (
    <li className="sf-writing" aria-live="polite">
      <Spin /> Writing…
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
