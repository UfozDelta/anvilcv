/* Shared pieces for the three /lab/story-flow takes. */
import { useState } from 'react';
import { estimatedLines } from '../../lib/bulletLength';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { UndoBar } from '../../components/ledger/shared';
import { EditBulletForm } from '../projectDetail/EditBulletForm';
import { Meter } from '../project-split/parts';
import { REPO_MAP } from '../project-split/data';
import { Fit, Spin, Trash } from '../workspace/parts';
import { BY_ID, LENS_OF, LENSES, type Lens } from './data';
import { wid, type Bullet, type SF } from './model';

/* ── Head: name, cap meter, project Generate ── */

export function Head({ sf, onGenerate }: { sf: SF; onGenerate: () => void }) {
  const busy = sf.finding > 0;
  const tip = sf.room === 0 ? 'Bank full' : sf.left === 0 ? 'No new stories left' : 'Find new stories, one bullet each';
  return (
    <header className="ps-head">
      <div className="ps-head__id">
        <div className="eyebrow">← Projects</div>
        <h1 className="display ps-head__name">{sf.project.name}</h1>
      </div>
      <div className="ps-head__tools">
        <Meter used={sf.stories.length} usable={sf.usable} />
        <button type="button" className="btn btn--sm btn--acid sf-gen" onClick={onGenerate} disabled={busy} title={tip}>
          {busy ? <Spin /> : '✦'} Generate
        </button>
        <span className="sf-last" aria-live="polite">
          {sf.last && (sf.last.full ? <span title="Bank full">Full</span>
            : sf.last.added > 0 ? <span data-good title={`${sf.last.added} new stories`}>+{sf.last.added}</span>
            : <span title="Nothing new found">0 new</span>)}
        </span>
      </div>
    </header>
  );
}

/* ── Lens tag ── */

export function LensTag({ lens, big }: { lens: Lens; big?: boolean }) {
  const l = LENS_OF[lens];
  return <span className="sf-lens" data-lens={lens} data-big={big || undefined} title={l.tip}>{l.name}</span>;
}

/* ── Evidence: quote count, expands to the quotes ── */

export function EvidenceChip({ id, open, onToggle }: { id: string; open: boolean; onToggle: () => void }) {
  const ev = BY_ID[id].evidence;
  return (
    <button type="button" className="sf-ev" aria-expanded={open} onClick={e => { e.stopPropagation(); onToggle(); }}
      title={ev.map(q => `${q.src}: ${q.text}`).join('\n')}>
      ❝ {ev.length}
    </button>
  );
}

export function Quotes({ id }: { id: string }) {
  return (
    <ul className="sf-quotes">
      {BY_ID[id].evidence.map(q => (
        <li key={q.src}><code>{q.src}</code><q>{q.text}</q></li>
      ))}
    </ul>
  );
}

/* ── One wording row, wide ── */

function Toggle({ sf, b }: { sf: SF; b: Bullet }) {
  const on = b.status === 'APPROVED';
  return (
    <button className="brow__toggle" aria-pressed={on} title={on ? 'Unapprove' : 'Approve'}
      onClick={e => { e.stopPropagation(); sf.toggle(b.id); }}>
      <span className={`brow__state ${on ? 'brow__state--in' : 'brow__state--out'}`}>{on ? '✓ APPROVED' : 'BULLET'}</span>
      <span className="brow__rank">{estimatedLines(b.text)}L</span>
    </button>
  );
}

export function WordingRow({ sf, b, onEdit }: { sf: SF; b: Bullet; onEdit: () => void }) {
  const prints = sf.printed(b.storyId)?.id === b.id;
  const many = sf.wordings(b.storyId).length > 1;
  return (
    <div className={`brow ps-row wt-row sf-row${b.status === 'APPROVED' ? ' is-in' : ''}`} data-new={sf.newIds.has(b.id) || undefined}>
      <Toggle sf={sf} b={b} />
      <div className="brow__text wt-text"><RichText text={b.text} /></div>
      <div className="wt-side">
        <span className="wt-side__meta">
          <LensTag lens={b.lens} />
          {many && prints && <span className="sf-prints" title="Prints by default; one wording per resume">▶</span>}
          <Fit text={b.text} />
        </span>
        <span className="ws-acts wt-acts">
          <button className="minibtn" onClick={onEdit}>Edit</button>
          <Trash onClick={() => sf.remove(b.id)} />
        </span>
      </div>
    </div>
  );
}

function Writing({ lens }: { lens: Lens }) {
  return (
    <div className="sf-writing" aria-live="polite">
      <Spin /> <LensTag lens={lens} />
    </div>
  );
}

/** A story's wordings, alternatives split by "or"; pending lenses as placeholders. */
export function Wordings({ sf, id }: { sf: SF; id: string }) {
  const [editing, setEditing] = useState<string | null>(null);
  const ws = sf.wordings(id);
  const wait = LENSES.map(l => l.slug).filter(l => sf.pending.has(wid(id, l)));
  const items: React.ReactNode[] = [
    ...ws.map(b => (editing === b.id ? (
      <EditBulletForm key={b.id}
        bullet={{ ...b, status: b.status === 'APPROVED' ? 'APPROVED' : 'PENDING', projectId: '', category: b.lens, createdAt: '', updatedAt: '' }}
        onSave={(text, tags) => { sf.patch(b.id, { text, tags }); setEditing(null); }} onCancel={() => setEditing(null)} />
    ) : (
      <WordingRow key={b.id} sf={sf} b={b} onEdit={() => setEditing(b.id)} />
    ))),
    ...wait.map(l => <Writing key={`w-${l}`} lens={l} />),
  ];
  return (
    <div className="sf-alts" data-many={items.length > 1 || undefined}>
      {items.map((node, i) => (
        <div key={i} className="sf-alt">
          {i > 0 && <span className="sf-or" title="One wording prints per resume">or</span>}
          {node}
        </div>
      ))}
    </div>
  );
}

/* ── Description and Repo (as in /lab/workspace) ── */

export function DescriptionPane({ sf }: { sf: SF }) {
  return (
    <label className="field">
      <div className="field__label">Description</div>
      <textarea className="field__textarea" value={sf.project.description} style={{ minHeight: 180 }}
        onChange={e => sf.setField('description', e.target.value)} />
      <div className="ps-count">{sf.project.description.length}c</div>
    </label>
  );
}

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
