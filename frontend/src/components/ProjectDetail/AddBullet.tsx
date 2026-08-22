import { useState } from 'react';
import { CATEGORIES } from '../../lib/api';

export function AddBullet({ onSave, onCancel }: {
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
    <div className="bullet" style={{ marginBottom: 16 }}>
      <div className="bullet__rank">NEW</div>
      <div className="stack-sm" style={{ width: '100%' }}>
        <textarea
          className="field__textarea"
          value={text}
          onChange={e => { setText(e.target.value); setErr(null); }}
          placeholder="Reduced latency by 47ms by rewriting the query planner."
          style={{ minHeight: 80 }}
          autoFocus
        />
        <input
          className="field__input"
          value={tagsStr}
          onChange={e => setTagsStr(e.target.value)}
          placeholder="backend, performance (optional)"
        />
        <select
          className="field__input"
          value={category}
          onChange={e => setCategory(e.target.value)}
        >
          {CATEGORIES.map(c => (
            <option key={c.slug} value={c.slug}>{c.label} — {c.blurb}</option>
          ))}
        </select>
        {err && <div className="err">{err}</div>}
        <div className="row">
          <button className="btn btn--sm" onClick={submit}>SAVE</button>
          <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}
