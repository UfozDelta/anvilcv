/* Bullets tab: the whole bank, grouped under the story each bullet achieves. */
import { useEffect, useState } from 'react';
import { EditBulletForm } from '../projectDetail/EditBulletForm';
import { BY_ID, LENSES } from './data';
import { lensOf, type SF } from './model';
import { LensTag, WordingRow, Writing } from './parts';

export function BulletsTab({ sf, focus, onGenerate }: { sf: SF; focus: string | null; onGenerate: () => void }) {
  const [editing, setEditing] = useState<string | null>(null);

  useEffect(() => {
    if (focus) document.getElementById(`sf-s-${focus}`)?.scrollIntoView({ block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, [focus]);

  if (sf.stories.length === 0) {
    return (
      <div className="sf-empty">
        <span>No bullets</span>
        <button className="btn btn--sm btn--acid" onClick={onGenerate}>✦ Generate</button>
      </div>
    );
  }

  return (
    <div className="sf-bank">
      {sf.stories.map(id => {
        const s = BY_ID[id];
        const ws = sf.wordings(id);
        const wait = sf.pendingOf(id);
        return (
          <section key={id} id={`sf-s-${id}`} className="sf-group" data-new={sf.newIds.has(id) || undefined}>
            <h3 className="sf-group__title">
              <span className="sf-glyph" aria-hidden="true">{s.glyph}</span>
              <span className="sf-group__name" tabIndex={0} title={s.evidence.map(q => `${q.src}: ${q.text}`).join('\n')}>{s.title}</span>
              <span className="sf-group__lenses">
                {LENSES.filter(l => ws.some(b => b.lens === l.slug)).map(l => <LensTag key={l.slug} lens={l.slug} />)}
              </span>
              <span className="sf-group__n" title={`${ws.length} bullet${ws.length === 1 ? '' : 's'}`}>{ws.length}</span>
            </h3>
            <ul className="sf-ws">
              {LENSES.map(l => {
                const lw = ws.filter(b => b.lens === l.slug);
                const lp = wait.filter(w => lensOf(w) === l.slug);
                if (lw.length + lp.length === 0) return null;
                return (
                  <li key={l.slug} className="sf-lg" data-lens={l.slug}>
                    <div className="sf-lg__k" title={l.tip}>{l.name}{lw.length > 1 && <span className="sf-lg__n">{lw.length}</span>}</div>
                    <ul className="sf-ws">
                      {lw.map(b => (editing === b.id ? (
                        <li key={b.id} className="sf-b-edit">
                          <EditBulletForm
                            bullet={{ ...b, status: b.status === 'APPROVED' ? 'APPROVED' : 'PENDING', projectId: '', category: b.lens, createdAt: '', updatedAt: '' }}
                            onSave={(text, tags) => { sf.patch(b.id, { text, tags }); setEditing(null); }} onCancel={() => setEditing(null)} />
                        </li>
                      ) : (
                        <WordingRow key={b.id} sf={sf} b={b} onEdit={() => setEditing(b.id)} />
                      )))}
                      {lp.map(w => <Writing key={w} />)}
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
