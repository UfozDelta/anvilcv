import type { Bullet, BulletVerdict, GenerationConfig, RankedBullet } from '../../lib/api';
import { estimatedLines } from '../../lib/bulletLength';
import { RichText } from '../RichText';
import { EditBullet } from '../ProjectDetail/EditBullet';

export function RankedBulletRow({ r, bullet, isSelected, whyOpen, verdict, editing, cfg, locked, isNew, onToggleSelect, onToggleWhy, onEdit, onCancelEdit, onSaveBullet, onToggleLock }: {
  r: RankedBullet;
  bullet: Bullet | undefined;
  isSelected: boolean;
  whyOpen: boolean;
  verdict?: BulletVerdict;
  editing: boolean;
  cfg: GenerationConfig;
  locked: boolean;
  isNew?: boolean;
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
  // What this bullet costs against the one-page budget, shown on the row that spends it.
  const cost = estimatedLines(bullet?.text ?? '');
  return (
    <div className={`brow ${isSelected ? 'is-in' : 'is-out'}${isNew ? ' is-new' : ''}`}>
      {/* A real button with the state written out — not a dimmed div you have to decode. */}
      <button
        className="brow__toggle"
        onClick={onToggleSelect}
        aria-pressed={isSelected}
        title={isSelected ? 'Click to exclude from the page' : 'Click to include on the page'}
      >
        <span className={`brow__state ${isSelected ? 'brow__state--in' : 'brow__state--out'}`}>
          {isSelected ? '✓ IN' : 'OUT'}
        </span>
        <span className="brow__rank">rank {r.rank} · {cost}L</span>
      </button>

      <div>
        <div className="brow__text">
          {locked && <span title="Locked — survives auto-pick" style={{ marginRight: 5 }}>🔒</span>}
          {isNew && <span className="vchip vchip--keep" style={{ marginRight: 5 }}>new</span>}
          {bullet ? <RichText text={bullet.text} /> : <em className="muted">— bullet missing —</em>}
        </div>

        <div className="brow__under">
          {/* The verdict stays visible whether or not the bullet is included — it is the
              reason you would change your mind either way. */}
          {verdict && (
            <span className={`vchip vchip--${verdict.verdict}`} title={verdict.reason}>
              {verdict.verdict}
            </span>
          )}
          {bullet?.tags.map(t => <span key={t} className="kw">{t}</span>)}

          {bullet && (
            <div className="brow__actions">
              {r.why && (
                <button className={`minibtn ${whyOpen ? 'is-on' : ''}`} onClick={onToggleWhy}>
                  Why {whyOpen ? '↑' : '↓'}
                </button>
              )}
              <button className={`minibtn ${locked ? 'is-on' : ''}`} onClick={onToggleLock}>
                {locked ? 'Unlock' : 'Lock'}
              </button>
              <button className="minibtn" onClick={onEdit}>Edit</button>
            </div>
          )}
        </div>

        {whyOpen && r.why && (
          <div className="brow__why">
            <b>Why it ranked {r.rank}</b>
            {r.why}
            {verdict && (
              <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--soft)' }}>
                <b>Recruiter pass said “{verdict.verdict}”</b>
                {verdict.reason}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
