import type { Bullet, RankedBullet } from '../../lib/api';

export function RankedBulletRow({ r, bullet, isSelected, whyOpen, onToggleSelect, onToggleWhy }: {
  r: RankedBullet;
  bullet: Bullet | undefined;
  isSelected: boolean;
  whyOpen: boolean;
  onToggleSelect: () => void;
  onToggleWhy: () => void;
}) {
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
        <div className="bullet__text">{bullet?.text || <em className="muted">— bullet missing —</em>}</div>
        {isSelected && bullet && (
          <div className="bullet__tags" style={{ marginTop: 4 }}>
            {bullet.tags.map(t => <span key={t} className="tag">{t}</span>)}
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
