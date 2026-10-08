import { useState } from 'react';
import { CATEGORIES } from '../../lib/api';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { UndoBar } from '../../components/ledger/shared';
import { LAB_CFG } from '../fixtures';
import { isVanity } from '../stories/storyFixtures';
import { AddBulletForm } from '../projectDetail/AddBulletForm';
import { EditBulletForm } from '../projectDetail/EditBulletForm';
import { Meter } from '../project-split/parts';
import { REPO_MAP } from '../project-split/data';
import { LENSES, SHORT } from './data';
import { storyOf, type Run, type WBullet, type WS } from './model';

const LENS = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));
const plain = (t: string) => t.replace(/\*\*/g, '');

/* ── Head ── */

export function Head({ ws, children }: { ws: WS; children?: React.ReactNode }) {
  return (
    <header className="ps-head">
      <div className="ps-head__id">
        <div className="eyebrow">← Projects</div>
        <h1 className="display ps-head__name">{ws.project.name}</h1>
      </div>
      <div className="ps-head__tools">
        <Meter used={ws.used} usable={ws.usable} />
        {children}
      </div>
    </header>
  );
}

import { Spin, Trash } from '../../components/ledger/parts';
export { Spin, Trash };

/* ── Row pieces ── */

export function Fit({ text }: { text: string }) {
  const fit = fitOf(text, LAB_CFG);
  if (fit === 'OFF') return null;
  const bad = needsRefit(fit);
  const lines = estimatedLines(text);
  return (
    <span className="ps-fit" data-bad={bad || undefined} title={bad ? fitHint(text, LAB_CFG) : `${lines} line${lines === 1 ? '' : 's'}`}>
      {bad && '⚠ '}{FIT_LABEL[fit]} · {charCount(text)}c
    </span>
  );
}

export function StoryMark({ ws, bullet }: { ws: WS; bullet: WBullet }) {
  const s = storyOf(bullet.storyId);
  if (!s) return null;
  const n = ws.siblings(bullet).length;
  return (
    <>
      <span className="ps-glyph" title={`${s.title} · ${n} wording${n === 1 ? '' : 's'}, one prints`}
        onMouseEnter={() => ws.setHoverStory(s.id)} onMouseLeave={() => ws.setHoverStory(null)}>
        {s.glyph}{n > 1 && <sup>{n}</sup>}
      </span>
      {ws.printed.has(bullet.id) && n > 1 && <span className="ps-prints" title="Prints" aria-label="Prints">▶</span>}
    </>
  );
}

/** Two states only: a bullet, or approved. */
export function StatusToggle({ ws, bullet }: { ws: WS; bullet: WBullet }) {
  const on = bullet.status === 'APPROVED';
  return (
    <button className="brow__toggle" aria-pressed={on} title={on ? 'Unapprove' : 'Approve'}
      onClick={e => { e.stopPropagation(); ws.toggle(bullet.id); }}>
      <span className={`brow__state ${on ? 'brow__state--in' : 'brow__state--out'}`}>{on ? '✓ APPROVED' : 'BULLET'}</span>
      <span className="brow__rank">{estimatedLines(bullet.text)}L</span>
    </button>
  );
}

export function Row({ ws, bullet, onEdit, showLens }: { ws: WS; bullet: WBullet; onEdit?: () => void; showLens?: boolean }) {
  const sib = !!bullet.storyId && ws.hoverStory === bullet.storyId;
  const cls = ['brow', 'ps-row', bullet.status === 'APPROVED' ? 'is-in' : '', ws.selectedId === bullet.id ? 'is-sel' : ''].filter(Boolean).join(' ');
  return (
    <div className={cls} data-sib={sib || undefined} data-new={ws.newIds.has(bullet.id) || undefined} onClick={() => ws.setSelectedId(bullet.id)}>
      <StatusToggle ws={ws} bullet={bullet} />
      <div style={{ minWidth: 0 }}>
        <div className="brow__text"><RichText text={bullet.text} /></div>
        <div className="ps-meta">
          {showLens && <span className="ws-lens" title={LENS[bullet.lens]?.label}>{SHORT[bullet.lens]}</span>}
          <Fit text={bullet.text} />
          <StoryMark ws={ws} bullet={bullet} />
          {isVanity(bullet.text) && bullet.status !== 'APPROVED' && <span className="ps-fit" data-bad title="Activity count: held back unless approved">#</span>}
          {bullet.tags.map(t => <span key={t} className="ps-tag">{t}</span>)}
          <span className="ws-acts" onClick={e => e.stopPropagation()}>
            {onEdit && <button className="minibtn" onClick={onEdit}>Edit</button>}
            <Trash onClick={() => ws.remove(bullet.id)} />
          </span>
        </div>
      </div>
    </div>
  );
}

/* ── Bullets ── */

type Group = { key: string; label: React.ReactNode; title?: string; rows: WBullet[] };

function lensGroups(ws: WS): Group[] {
  const fresh = (b: WBullet) => Number(ws.newIds.has(b.id));
  return LENSES.map(l => ({
    key: l.slug, label: LENS[l.slug].label, title: LENS[l.slug].blurb,
    rows: ws.bullets.filter(b => b.lens === l.slug).sort((x, y) => fresh(y) - fresh(x)),
  })).filter(g => g.rows.length > 0);
}

function storyGroups(ws: WS): Group[] {
  const order = LENSES.map(l => l.slug);
  const gs: Group[] = ws.stories.map(s => ({
    key: s.id, label: <>{s.glyph} {s.title}</>, title: s.title,
    rows: ws.bullets.filter(b => b.storyId === s.id).sort((x, y) => order.indexOf(x.lens) - order.indexOf(y.lens)),
  }));
  if (ws.loose.length) gs.push({ key: 'loose', label: '○ Loose', title: 'Bullets without a story', rows: ws.loose });
  return gs;
}

export function BulletsPane({ ws, switchable }: { ws: WS; switchable?: boolean }) {
  const [by, setBy] = useState<'lens' | 'story'>('lens');
  const [filter, setFilter] = useState<string | null>(null);
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const groups = by === 'lens' ? lensGroups(ws) : storyGroups(ws);
  const lensChips = lensGroups(ws);
  const shown = by === 'lens' && filter ? groups.filter(g => g.key === filter) : groups;
  const toggle = (k: string) => setClosed(s => {
    const n = new Set(s);
    n.has(k) ? n.delete(k) : n.add(k);
    return n;
  });

  return (
    <div>
      <div className="ps-tools">
        {switchable && (
          <div className="ws-seg" role="group" aria-label="Group by">
            <button aria-pressed={by === 'lens'} onClick={() => setBy('lens')}>Lens</button>
            <button aria-pressed={by === 'story'} onClick={() => setBy('story')}>Story</button>
          </div>
        )}
        {by === 'lens' && (
          <div className="ps-chips">
            <button className="minibtn" data-on={filter === null || undefined} aria-pressed={filter === null} onClick={() => setFilter(null)}>All</button>
            {lensChips.map(g => (
              <button key={g.key} className="minibtn" data-on={filter === g.key || undefined} aria-pressed={filter === g.key} title={g.title}
                onClick={() => setFilter(f => (f === g.key ? null : g.key))}>
                {SHORT[g.key]} <span className="ps-chips__n">{g.rows.length}</span>
              </button>
            ))}
          </div>
        )}
        <button className="minibtn" style={{ marginLeft: 'auto' }} onClick={() => { setAdding(a => !a); setEditing(null); }}>{adding ? '✕ Cancel' : '+ Add'}</button>
      </div>

      {adding && <AddBulletForm onSave={(t, tg, c) => { ws.add(t, tg, c); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {shown.map(g => {
        const open = !closed.has(g.key);
        return (
          <section key={g.key} className="ps-group">
            <div className="ps-group__head" role="button" tabIndex={0} aria-expanded={open} title={g.title} onClick={() => toggle(g.key)}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(g.key); } }}>
              <span className="label ws-ghead">{open ? '▾' : '▸'} {g.label}</span>
              <span className="label muted">{g.rows.length}</span>
            </div>
            {open && g.rows.map(r => (editing === r.id ? (
              <EditBulletForm key={r.id}
                bullet={{ ...r, status: r.status === 'APPROVED' ? 'APPROVED' : 'PENDING', projectId: '', category: r.lens, createdAt: '', updatedAt: '' }}
                onSave={(text, tags) => { ws.patch(r.id, { text, tags }); setEditing(null); }} onCancel={() => setEditing(null)} />
            ) : (
              <Row key={r.id} ws={ws} bullet={r} showLens={by === 'story'} onEdit={() => { setEditing(r.id); setAdding(false); }} />
            )))}
          </section>
        );
      })}
    </div>
  );
}

/* ── Generate ── */

/** Compact run result: +stories, +wordings, then wordings per lens. */
export function RunChips({ run }: { run: Run | null }) {
  if (!run) return null;
  if (run.full) return <span className="ps-run" aria-live="polite"><span className="ps-run__c" data-bad title="Bank full: 12 stories">Full</span></span>;
  const total = Object.values(run.perLens).reduce((a, b) => a + b, 0);
  return (
    <span className="ps-run ws-run" aria-live="polite">
      {run.kind === 'new' && <span className="ps-run__c" data-good title={`${run.stories.length} new stories`}>+{run.stories.length} ◆</span>}
      <span className="ps-run__c" data-good={run.kind === 'fill' || undefined} title={`${total} new wordings`}>+{total}</span>
      {LENSES.filter(l => run.perLens[l.slug]).map(l => (
        <span key={l.slug} className="ps-run__c ws-run__lens" title={`${run.perLens[l.slug]} ${l.short}`}>{l.abbr} {run.perLens[l.slug]}</span>
      ))}
    </span>
  );
}

/** Many lenses, multi-select. Count = wordings already in the bank for that lens. */
export function LensPicker({ ws, picked, setPicked }: { ws: WS; picked: Set<string>; setPicked: (s: Set<string>) => void }) {
  const all = picked.size === LENSES.length;
  const flip = (slug: string) => {
    const n = new Set(picked);
    n.has(slug) ? n.delete(slug) : n.add(slug);
    setPicked(n);
  };
  return (
    <div className="ws-lenses">
      {LENSES.map(l => {
        const n = ws.bullets.filter(b => b.lens === l.slug).length;
        return (
          <label key={l.slug} className="ws-lens-opt" data-on={picked.has(l.slug) || undefined} title={LENS[l.slug].blurb}>
            <input type="checkbox" checked={picked.has(l.slug)} onChange={() => flip(l.slug)} />
            <span className="ws-lens-opt__box" aria-hidden="true">{picked.has(l.slug) ? '✓' : ''}</span>
            <span className="ws-lens-opt__name">{l.short}</span>
            <span className="ws-lens-opt__n" title={`${n} in bank`}>{n}</span>
          </label>
        );
      })}
      <button type="button" className="minibtn ws-lenses__all" onClick={() => setPicked(new Set(all ? [] : LENSES.map(l => l.slug)))}>
        {all ? 'None' : 'All'}
      </button>
    </div>
  );
}

/** The Generate surface: pick lenses, run, watch stories arrive. Also tops up existing stories. */
export function GenPanel({ ws }: { ws: WS }) {
  const [picked, setPicked] = useState<Set<string>>(() => new Set(LENSES.map(l => l.slug)));
  const lenses = LENSES.map(l => l.slug).filter(l => picked.has(l));
  const open = lenses.reduce((n, l) => n + ws.open[l], 0);
  const run = ws.run;
  return (
    <div className="ws-gen">
      <LensPicker ws={ws} picked={picked} setPicked={setPicked} />
      <div className="ws-gen__bar">
        <button className="btn btn--sm btn--acid ps-gen__btn" disabled={ws.busy || lenses.length === 0}
          title={ws.room === 0 ? 'Bank full' : `New stories, up to ${Math.min(3, ws.room)}`} onClick={() => ws.generate(lenses)}>
          {ws.busy && run?.kind === 'new' ? <Spin /> : '✦'} New stories
        </button>
        <button className="btn btn--sm ws-gen__fill" disabled={ws.busy || open === 0}
          title={`${open} more wordings for stories you have`} onClick={() => ws.fill({ lenses })}>
          {ws.busy && run?.kind === 'fill' ? <Spin /> : '+'} More lenses <span className="ps-chips__n">{open}</span>
        </button>
        <RunChips run={run} />
      </div>
      {run?.kind === 'new' && (run.stories.length > 0 || run.live) && (
        <ol className="ws-arrivals">
          {run.stories.map(id => {
            const s = storyOf(id)!;
            const got = ws.bullets.filter(b => b.storyId === id);
            return (
              <li key={id} className="ws-arrival">
                <span className="ps-glyph">{s.glyph}</span>
                <span className="ws-arrival__t" title={s.title}>{s.title}</span>
                <span className="ws-arrival__lenses">
                  {got.map(b => <span key={b.id} className="ws-lens" title={plain(b.text)}>{SHORT[b.lens]}</span>)}
                </span>
              </li>
            );
          })}
          {run.live && <li className="ws-arrival ws-arrival--wait"><Spin /></li>}
        </ol>
      )}
    </div>
  );
}

/* ── Sources ── */

export function DescriptionPane({ ws }: { ws: WS }) {
  return (
    <label className="field">
      <div className="field__label">Description</div>
      <textarea className="field__textarea" value={ws.project.description} style={{ minHeight: 180 }}
        onChange={e => ws.setField('description', e.target.value)} />
      <div className="ps-count">{ws.project.description.length}c</div>
    </label>
  );
}

export function RepoPane({ ws }: { ws: WS }) {
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const p = ws.project;
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

export function Undo({ ws }: { ws: WS }) {
  const r = ws.removed?.b;
  const s = r ? storyOf(r.storyId) : null;
  return <UndoBar name={r ? (s ? `${s.glyph} ${SHORT[r.lens]}` : 'bullet') : null} onUndo={ws.undo} />;
}
