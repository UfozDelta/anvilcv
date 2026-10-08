/* Bullets tab: the whole bank, grouped under the story each bullet achieves. */
import { useEffect, useState } from 'react';
import { BY_ID, LENS_OF, LENSES } from './data';
import { lensOf, type SF } from './model';
import { EditRow, LensTag, PaneStatus, WordingRow, Writing } from './parts';

export function BulletsTab({ sf, focus, onGenerate }: { sf: SF; focus: string | null; onGenerate: () => void }) {
  const [editing, setEditing] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<string>>(new Set());
  const flip = (id: string) => setOpen(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  const writing = [...new Set([...sf.pending].map(id => LENS_OF[lensOf(id)].name))];

  useEffect(() => {
    if (focus) document.getElementById(`sf-s-${focus}`)?.scrollIntoView({ block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, [focus]);

  if (sf.stories.length === 0) {
    return (
      <div className="sf-empty">
        <span>No bullets</span>
        <button type="button" className="btn btn--sm btn--acid" onClick={onGenerate}>✦ Generate</button>
      </div>
    );
  }

  return (
    <div className="sf-bank">
      <PaneStatus busy={writing.length ? `Writing ${writing.join(', ')}…` : null} />
      {sf.stories.map(id => {
        const s = BY_ID[id];
        const ws = sf.wordings(id);
        const wait = sf.pendingOf(id);
        const ev = open.has(id);
        return (
          <section key={id} id={`sf-s-${id}`} className="sf-group">
            <h2 className="sf-group__title">
              <button type="button" className="sf-group__name" aria-expanded={ev} aria-controls={`sf-ev-${id}`} onClick={() => flip(id)}>
                {s.title}<span className="sf-group__caret" aria-hidden="true">{ev ? '−' : '+'}</span>
              </button>
              <span className="sf-group__lenses">
                {LENSES.filter(l => ws.some(b => b.lens === l.slug)).map(l => <LensTag key={l.slug} lens={l.slug} />)}
              </span>
              <span className="sf-group__n">{ws.length}<span className="sr-only"> bullet{ws.length === 1 ? '' : 's'}</span></span>
            </h2>
            {ev && (
              <ul className="sf-ev" id={`sf-ev-${id}`} aria-label="Evidence">
                {s.evidence.map(q => <li key={q.src}><span className="sf-ev__src">{q.src}</span> {q.text}</li>)}
              </ul>
            )}
            <ul className="sf-ws">
              {LENSES.map(l => {
                const lw = ws.filter(b => b.lens === l.slug);
                const lp = wait.filter(w => lensOf(w) === l.slug);
                if (lw.length + lp.length === 0) return null;
                return (
                  <li key={l.slug} className="sf-lg" data-lens={l.slug}>
                    <div className="sf-lg__k">{l.name}{lw.length > 1 && <span className="sf-lg__n">{lw.length}</span>}</div>
                    <ul className="sf-ws">
                      {lw.map(b => (editing === b.id ? (
                        <EditRow key={b.id} b={b} onSave={text => { sf.patch(b.id, { text }); setEditing(null); }} onCancel={() => setEditing(null)} />
                      ) : (
                        <WordingRow key={b.id} sf={sf} b={b} onEdit={() => setEditing(b.id)} />
                      )))}
                      {lp.map(w => <Writing key={w} lens={l.slug} />)}
                    </ul>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
