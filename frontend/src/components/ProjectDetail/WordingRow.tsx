import { RichText } from '../RichText';
import { Fit, Trash } from '../ledger/parts';
import type { Bullet, GenerationConfig } from '../../lib/api';
import '../../styles/story.css';

/**
 * One wording in the bank. Edit is always rendered, whatever the status or lens: a bullet's text
 * must stay editable. Approve toggles APPROVED; Trash is a soft delete.
 */
export function WordingRow({ b, cfg, onApprove, onEdit, onTrash }: {
  b: Bullet;
  cfg: GenerationConfig;
  onApprove: () => void;
  onEdit: () => void;
  onTrash: () => void;
}) {
  const on = b.status === 'APPROVED';
  return (
    <div className="sf-b" data-on={on || undefined}>
      <div className="sf-b__text"><RichText text={b.text} /></div>
      <div className="sf-b__side">
        <span className="sf-b__meta">
          {b.angle && <span className="kw" data-angle={b.angle}>{b.angle}</span>}
          <Fit text={b.text} cfg={cfg} />
        </span>
        <span className="sf-b__acts">
          <button type="button" className="sf-b__ok" aria-pressed={on} onClick={onApprove}>{on ? '✓ Approved' : 'Approve'}</button>
          <button type="button" className="minibtn" onClick={onEdit}>Edit</button>
          <Trash onClick={onTrash} label="Trash" />
        </span>
      </div>
    </div>
  );
}
