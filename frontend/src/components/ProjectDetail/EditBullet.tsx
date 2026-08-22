import { useState } from 'react';
import type { Bullet } from '../../lib/api';

export function EditBullet({ bullet, onSave, onCancel }: {
  bullet: Bullet;
  onSave: (text: string, tags: string[]) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(bullet.text);
  const [tagsStr, setTagsStr] = useState(bullet.tags.join(', '));
  return (
    <div className="bullet">
      <div className="bullet__rank">EDIT</div>
      <div className="stack-sm">
        <textarea className="field__textarea" value={text} onChange={e => setText(e.target.value)} style={{ minHeight: 80 }} />
        <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)} placeholder="backend, ai-ml" />
        <div className="row">
          <button className="btn btn--sm" onClick={() => onSave(text, tagsStr.split(',').map(s => s.trim()).filter(Boolean))}>SAVE</button>
          <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}
