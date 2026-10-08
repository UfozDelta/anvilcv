/* The wider Bullets tab, shared by all three variants. */
import { useState } from 'react';
import { CATEGORIES } from '../../lib/api';
import { RichText } from '../../components/RichText';
import { UndoBar } from '../../components/ledger/shared';
import { isVanity } from '../stories/storyFixtures';
import { AddBulletForm } from '../projectDetail/AddBulletForm';
import { EditBulletForm } from '../projectDetail/EditBulletForm';
import { LENSES, SHORT } from '../workspace/data';
import { Fit, StatusToggle, Trash } from '../workspace/parts';
import { storyOf, type WBullet, type WS } from './model';
import type { Term } from './data';

export const LENS = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));
export const plain = (t: string) => t.replace(/\*\*/g, '');

export function Mark({ ws, bullet, term }: { ws: WS; bullet: WBullet; term: Term }) {
  const s = storyOf(bullet.storyId);
  if (!s) return null;
  const n = ws.siblings(bullet).length;
  return (
    <>
      <span className="ps-glyph wt-glyph" title={`${term.one}: ${s.title} · ${n} wording${n === 1 ? '' : 's'}, one prints`}
        onMouseEnter={() => ws.setHoverStory(s.id)} onMouseLeave={() => ws.setHoverStory(null)}>
        {s.glyph}{n > 1 && <sup>{n}</sup>}
      </span>
      {ws.printed.has(bullet.id) && n > 1 && <span className="ps-prints" title="Prints" aria-label="Prints">▶</span>}
    </>
  );
}

function Row({ ws, bullet, term, onEdit }: { ws: WS; bullet: WBullet; term: Term; onEdit: () => void }) {
  const cls = ['brow', 'ps-row', 'wt-row', bullet.status === 'APPROVED' ? 'is-in' : '', ws.selectedId === bullet.id ? 'is-sel' : ''].filter(Boolean).join(' ');
  return (
    <div className={cls} data-sib={(!!bullet.storyId && ws.hoverStory === bullet.storyId) || undefined}
      data-new={ws.newIds.has(bullet.id) || undefined} onClick={() => ws.setSelectedId(bullet.id)}>
      <StatusToggle ws={ws} bullet={bullet} />
      <div className="brow__text wt-text"><RichText text={bullet.text} /></div>
      <div className="wt-side">
        <span className="wt-side__meta">
          <Mark ws={ws} bullet={bullet} term={term} />
          <Fit text={bullet.text} />
          {isVanity(bullet.text) && bullet.status !== 'APPROVED' && <span className="ps-fit" data-bad title="Activity count: held back unless approved">#</span>}
        </span>
        {bullet.tags.length > 0 && <span className="wt-side__tags">{bullet.tags.map(t => <span key={t} className="ps-tag">{t}</span>)}</span>}
        <span className="ws-acts wt-acts" onClick={e => e.stopPropagation()}>
          <button className="minibtn" onClick={onEdit}>Edit</button>
          <Trash onClick={() => ws.remove(bullet.id)} />
        </span>
      </div>
    </div>
  );
}

export function WideBullets({ ws, term }: { ws: WS; term: Term }) {
  const [filter, setFilter] = useState<string | null>(null);
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const fresh = (b: WBullet) => Number(ws.newIds.has(b.id));
  const groups = LENSES.map(l => ({ l, rows: ws.bullets.filter(b => b.lens === l.slug).sort((x, y) => fresh(y) - fresh(x)) }))
    .filter(g => g.rows.length > 0);
  const shown = filter ? groups.filter(g => g.l.slug === filter) : groups;
  const flip = (k: string) => setClosed(s => { const n = new Set(s); n.has(k) ? n.delete(k) : n.add(k); return n; });

  return (
    <div className="wt-bullets">
      <div className="ps-tools wt-tools">
        <div className="ps-chips">
          <button className="minibtn" data-on={filter === null || undefined} aria-pressed={filter === null} onClick={() => setFilter(null)}>All</button>
          {groups.map(g => (
            <button key={g.l.slug} className="minibtn" data-on={filter === g.l.slug || undefined} aria-pressed={filter === g.l.slug}
              title={LENS[g.l.slug]?.blurb} onClick={() => setFilter(f => (f === g.l.slug ? null : g.l.slug))}>
              {g.l.short} <span className="ps-chips__n">{g.rows.length}</span>
            </button>
          ))}
        </div>
        <button className="minibtn" onClick={() => { setAdding(a => !a); setEditing(null); }}>{adding ? '✕ Cancel' : '+ Add'}</button>
      </div>

      {adding && <AddBulletForm onSave={(t, tg, c) => { ws.add(t, tg, c); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {shown.map(({ l, rows }) => {
        const open = !closed.has(l.slug);
        return (
          <section key={l.slug} className="wt-group">
            <div className="wt-group__head" role="button" tabIndex={0} aria-expanded={open} title={LENS[l.slug]?.blurb} onClick={() => flip(l.slug)}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flip(l.slug); } }}>
              <span className="wt-group__caret" aria-hidden="true">{open ? '▾' : '▸'}</span>
              <span className="wt-group__name">{LENS[l.slug]?.label ?? SHORT[l.slug]}</span>
              <span className="wt-group__n">{rows.length}</span>
            </div>
            {open && rows.map(r => (editing === r.id ? (
              <EditBulletForm key={r.id}
                bullet={{ ...r, status: r.status === 'APPROVED' ? 'APPROVED' : 'PENDING', projectId: '', category: r.lens, createdAt: '', updatedAt: '' }}
                onSave={(text, tags) => { ws.patch(r.id, { text, tags }); setEditing(null); }} onCancel={() => setEditing(null)} />
            ) : (
              <Row key={r.id} ws={ws} bullet={r} term={term} onEdit={() => { setEditing(r.id); setAdding(false); }} />
            )))}
          </section>
        );
      })}
    </div>
  );
}

export function Undo({ ws }: { ws: WS }) {
  const r = ws.removed?.b;
  const s = r ? storyOf(r.storyId) : null;
  return <UndoBar name={r ? (s ? `${s.glyph} ${SHORT[r.lens]}` : 'bullet') : null} onUndo={ws.undo} />;
}

/** Lens abbreviations an item fits; `on` ones filled. */
export function LensDots({ fits, on, title }: { fits: string[]; on?: (l: string) => boolean; title?: (l: string) => string }) {
  return (
    <span className="wt-dots">
      {LENSES.filter(l => fits.includes(l.slug)).map(l => (
        <span key={l.slug} className="wt-dot" data-on={on?.(l.slug) || undefined} title={title?.(l.slug) ?? l.short}>{l.abbr}</span>
      ))}
    </span>
  );
}
