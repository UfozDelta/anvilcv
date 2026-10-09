import { useState } from 'react';
import { UndoBar } from '../ledger/shared';
import { EditBullet } from './EditBullet';
import { WordingRow } from './WordingRow';
import { CATEGORIES, type Bullet, type GenerationConfig } from '../../lib/api';
import { groupStories, isLive, refitNeeded } from '../../lib/storyBank';
import type { useStoryBank } from '../../hooks/useStoryBank';
import '../../styles/story.css';

type SB = ReturnType<typeof useStoryBank>;

/** Bullets tab: the bank grouped under each story, lens sub-groups inside, then loose wordings. */
export function StoryBullets({ cfg, sb }: { cfg: GenerationConfig; sb: SB }) {
  const [editing, setEditing] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [note, setNote] = useState<string | null>(null);
  const { groups, loose } = groupStories(sb.stories, sb.bullets);
  const live = sb.bullets.filter(isLive);
  const refit = refitNeeded(sb.bullets, cfg);

  const flip = (id: string) => setOpen(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });

  // Editing is available on every wording, whatever its status or lens (see WordingRow).
  const row = (b: Bullet) => editing === b.id ? (
    <EditBullet key={b.id} bullet={b} cfg={cfg} onCancel={() => setEditing(null)}
      onSave={async (text, tags) => { await sb.edit(b, text, tags); setEditing(null); }} />
  ) : (
    <WordingRow key={b.id} b={b} cfg={cfg} onApprove={() => sb.approve(b)}
      onEdit={() => setEditing(b.id)} onTrash={() => sb.trash(b)} />
  );

  async function doRefit() {
    setNote(null);
    const r = await sb.refit();
    setNote(`Checked ${r.checked}; rewrote ${r.rewritten}.`);
  }

  return (
    <div className="sf-bank">
      <div className="row" style={{ gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
        <button type="button" className="btn btn--sm" onClick={() => setAdding(a => !a)}>{adding ? '✕ Cancel' : '＋ Add'}</button>
        <button type="button" className="btn btn--sm" disabled={sb.pdf.busy || live.length === 0}
          onClick={() => sb.pdf.preview(live.map(b => b.id))}>{sb.pdf.busy ? 'Rendering…' : 'Render PDF'}</button>
        {refit > 0 && (
          <button type="button" className="btn btn--sm" onClick={doRefit}>Refit {refit}</button>
        )}
      </div>
      {note && <div className="label muted" style={{ marginBottom: 10 }}>{note}</div>}
      {sb.error && <div className="err" style={{ marginBottom: 10 }}>{sb.error}</div>}
      {sb.pdf.err && <div className="err" style={{ marginBottom: 10 }}>{sb.pdf.err}</div>}
      {sb.pdf.url && (
        <iframe src={sb.pdf.url} title="bullet render" className="sf-render" />
      )}
      {adding && (
        <AddWording onCancel={() => setAdding(false)}
          onSave={async (text, category) => { await sb.add(text, [], category); setAdding(false); }} />
      )}

      {live.length === 0 && !adding && (
        <div className="sf-empty"><span>No bullets yet. Use Generate, or add one by hand.</span></div>
      )}

      {groups.map(g => {
        const expanded = open.has(g.story.id);
        return (
          <section key={g.story.id} className="sf-group">
            <h2 className="sf-group__title">
              <button type="button" className="sf-group__name" aria-expanded={expanded} onClick={() => flip(g.story.id)}>
                {g.story.title}<span className="sf-group__caret" aria-hidden="true">{expanded ? '−' : '+'}</span>
              </button>
              <span className="sf-group__lenses">
                {g.lenses.map(l => <span key={l.lens} className="sf-lens" data-lens={l.lens}>{l.label}</span>)}
              </span>
              <span className="sf-group__n">{g.wordings.length}<span className="sr-only"> wordings</span></span>
            </h2>
            {expanded && (
              <ul className="sf-ev" aria-label="Evidence">
                {g.story.evidence.map((q, i) => <li key={i}>{q}</li>)}
              </ul>
            )}
            {g.lenses.map(l => (
              <div key={l.lens} className="sf-lg" data-lens={l.lens}>
                <div className="sf-lg__k">
                  {l.label}
                  {l.wordings.length > 1 && <span className="sf-lg__n">{l.wordings.length}</span>}
                  {l.weakFit && <span className="sf-lg__weak"> · weak fit</span>}
                </div>
                {l.wordings.map(row)}
              </div>
            ))}
          </section>
        );
      })}

      {loose.length > 0 && (
        <section className="sf-group">
          <h2 className="sf-group__title"><span>Other bullets</span><span className="sf-group__n">{loose.length}</span></h2>
          {loose.map(row)}
        </section>
      )}

      <UndoBar name={sb.undo ? 'Wording trashed' : null} onUndo={sb.restore} />
    </div>
  );
}

function AddWording({ onSave, onCancel }: { onSave: (text: string, category: string) => Promise<void>; onCancel: () => void }) {
  const [text, setText] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0].slug);
  const [busy, setBusy] = useState(false);
  const save = async () => {
    if (!text.trim()) return;
    setBusy(true);
    try { await onSave(text.trim(), category); } finally { setBusy(false); }
  };
  return (
    <div className="panel panel--inset stack-sm" style={{ marginBottom: 16 }}>
      <textarea className="field__textarea" aria-label="New bullet" value={text} onChange={e => setText(e.target.value)} rows={3} />
      <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
        <select className="field__input" aria-label="Lens" value={category} onChange={e => setCategory(e.target.value)}>
          {CATEGORIES.map(c => <option key={c.slug} value={c.slug}>{c.label}</option>)}
        </select>
        <button type="button" className="btn btn--acid btn--sm" disabled={busy || !text.trim()} onClick={save}>Save</button>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
