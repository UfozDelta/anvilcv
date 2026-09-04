import type { Bullet, BulletVerdict, GenerationConfig, RankedBullet } from '../../lib/api';
import { EditBullet } from '../ProjectDetail/EditBullet';

export function RankedBulletRow({ r, bullet, isSelected, whyOpen, verdict, editing, cfg, locked, onToggleSelect, onToggleWhy, onEdit, onCancelEdit, onSaveBullet, onToggleLock }: {
  r: RankedBullet;
  bullet: Bullet | undefined;
  isSelected: boolean;
  whyOpen: boolean;
  verdict?: BulletVerdict;
  editing: boolean;
  cfg: GenerationConfig;
  locked: boolean;
  onToggleSelect: () => void;
  onToggleWhy: () => void;
  onEdit: () => void;
  onCancelEdit: () => void;
  onSaveBullet: (text: string, tags: string[]) => void;
  onToggleLock: () => void;
}) {
  if (editing && bullet) {
    return <EditBullet bullet={bullet} cfg={cfg} onCancel={onCancelEdit} onSave={onSaveBullet} />;
  }
  return (
    <div className="bullet" style={{ opacity: isSelected ? 1 : 0.45 }}>
      <div
        className={`bullet__rank ${isSelected ? 'bullet__rank--selected' : ''}`}
        onClick={onToggleSelect}
        title={isSelected ? 'Click to exclude' : 'Click to include'}
        style={{ cursor: 'pointer' }}
      >
        #{String(r.rank).padStart(2, '0')}
      </div>
      <div style={{ width: '100%' }}>
        <div className="bullet__text">
          {locked && <span title="Locked — survives REFIT SELECTION" style={{ marginRight: 6 }}>🔒</span>}
          {bullet?.text || <em className="muted">— bullet missing —</em>}
        </div>
        {isSelected && bullet && (
          <div className="bullet__tags" style={{ marginTop: 4 }}>
            {bullet.tags.map(t => <span key={t} className="tag">{t}</span>)}
            {verdict && (
              <span
                className={`tag ${verdict.verdict === 'keep' ? 'tag--acid' : verdict.verdict === 'drop' ? 'tag--rust' : ''}`}
                title={verdict.reason}
              >{verdict.verdict.toUpperCase()}</span>
            )}
          </div>
        )}
        {bullet && (
          <div className="row" style={{ marginTop: 4 }}>
            <button
              className="btn btn--ghost btn--sm"
              style={{ fontSize: 10, padding: '2px 6px', ...(locked ? { textDecoration: 'underline', textUnderlineOffset: 4 } : {}) }}
              onClick={onToggleLock}
            >
              {locked ? 'UNLOCK' : 'LOCK'}
            </button>
            <button
              className="btn btn--ghost btn--sm"
              style={{ fontSize: 10, padding: '2px 6px' }}
              onClick={onEdit}
            >
              EDIT
            </button>
          </div>
        )}
        {r.why && (
          <div style={{ marginTop: 4 }}>
            <button
              className="btn btn--ghost btn--sm"
              style={{ fontSize: 10, padding: '2px 6px' }}
              onClick={onToggleWhy}
            >
              WHY {whyOpen ? '↑' : '↓'}
            </button>
            {whyOpen && (
              <div className="bullet__why" style={{ marginTop: 4 }}>{r.why}</div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
