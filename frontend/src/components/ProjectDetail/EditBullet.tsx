import { useState } from 'react';
import type { Bullet, GenerationConfig } from '../../lib/api';
import { charCount, fitOf, fitHint, needsRefit, FIT_LABEL } from '../../lib/bulletLength';

export function EditBullet({ bullet, onSave, onCancel, cfg }: {
  bullet: Bullet;
  onSave: (text: string, tags: string[]) => void;
  onCancel: () => void;
  cfg: GenerationConfig;
}) {
  const [text, setText] = useState(bullet.text);
  const [tagsStr, setTagsStr] = useState(bullet.tags.join(', '));
  // Live, so an edit that would push the bullet onto an extra rendered line says so as it happens
  // rather than at PDF time.
  const fit = fitOf(text, cfg);
  const hint = fitHint(text, cfg);
  return (
    <div className="bullet">
      <div className="bullet__rank">EDIT</div>
      <div className="stack-sm">
        <textarea className="field__textarea" value={text} onChange={e => setText(e.target.value)} style={{ minHeight: 80 }} />
        {fit !== 'OFF' && (
          <div style={{
            fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.12em',
            color: needsRefit(fit) ? 'var(--rust)' : 'var(--muted)',
          }}>
            {FIT_LABEL[fit]} · {charCount(text)}c{hint ? ` — ${hint}` : ''}
          </div>
        )}
        <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)} placeholder="backend, ai-ml" />
        <div className="row">
          <button className="btn btn--sm" onClick={() => onSave(text, tagsStr.split(',').map(s => s.trim()).filter(Boolean))}>SAVE</button>
          <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}
