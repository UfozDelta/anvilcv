import { useState } from 'react';
import type { Bullet } from '../../lib/api';
import { charCount, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { LAB_CFG } from '../fixtures';

export function EditBulletForm({ bullet, onSave, onCancel }: {
  bullet: Bullet;
  onSave: (text: string, tags: string[]) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(bullet.text);
  const [tagsStr, setTagsStr] = useState(bullet.tags.join(', '));
  const fit = fitOf(text, LAB_CFG);
  const hint = fitHint(text, LAB_CFG);
  return (
    <div className="panel panel--inset stack-sm" style={{ marginBottom: 12 }}>
      <div className="label">EDIT BULLET</div>
      <textarea className="field__textarea" value={text} onChange={e => setText(e.target.value)} style={{ minHeight: 70 }} autoFocus />
      {fit !== 'OFF' && (
        <div style={{ fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.1em', color: needsRefit(fit) ? 'var(--rust)' : 'var(--muted)' }}>
          {FIT_LABEL[fit]} · {charCount(text)}c{hint ? ` — ${hint}` : ''}
        </div>
      )}
      <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)} placeholder="backend, ai-ml" />
      <div className="row">
        <button className="btn btn--acid btn--sm" onClick={() => onSave(text, tagsStr.split(',').map(s => s.trim()).filter(Boolean))}>Save</button>
        <button className="btn btn--ghost btn--sm" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
