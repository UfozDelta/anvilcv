import { useState } from 'react';
import { CATEGORIES } from '../../lib/api';

export function AddBulletForm({ onSave, onCancel }: {
  onSave: (text: string, tags: string[], category: string) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState('');
  const [tagsStr, setTagsStr] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0].slug);
  const [err, setErr] = useState<string | null>(null);

  function submit() {
    if (!text.trim()) { setErr('Text is required.'); return; }
    onSave(text.trim(), tagsStr.split(',').map(s => s.trim()).filter(Boolean), category);
  }

  return (
    <div className="panel panel--inset stack-sm" style={{ marginBottom: 20 }}>
      <div className="label">NEW BULLET</div>
      <textarea className="field__textarea" value={text} autoFocus style={{ minHeight: 70 }}
        onChange={e => { setText(e.target.value); setErr(null); }}
        placeholder="Reduced latency by 47ms by rewriting the query planner." />
      <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)}
        placeholder="backend, performance (optional)" />
      <select className="field__input" value={category} onChange={e => setCategory(e.target.value)}>
        {CATEGORIES.map(c => <option key={c.slug} value={c.slug}>{c.label} — {c.blurb}</option>)}
      </select>
      {err && <div className="err">{err}</div>}
      <div className="row">
        <button className="btn btn--acid btn--sm" onClick={submit}>Save</button>
        <button className="btn btn--ghost btn--sm" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
