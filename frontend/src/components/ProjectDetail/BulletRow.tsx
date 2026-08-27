import type { Bullet, GenerationConfig } from '../../lib/api';
import { markdownBoldToHtml } from '../../lib/markdown';
import { charCount, fitOf, fitHint, needsRefit, FIT_LABEL } from '../../lib/bulletLength';

export function BulletRow({ bullet, index, onEdit, onDelete, onToggleApprove, categoryLabel, cfg }: {
  bullet: Bullet;
  index: number;
  onEdit: () => void;
  onDelete: () => void;
  onToggleApprove?: () => void;
  categoryLabel?: { label: string; blurb: string };
  cfg: GenerationConfig;
}) {
  const approved = bullet.status === 'APPROVED';
  const fit = fitOf(bullet.text, cfg);
  const bad = needsRefit(fit);
  return (
    <div className="bullet">
      <div className="bullet__rank">#{String(index + 1).padStart(2, '0')}</div>
      <div style={{ width: '100%' }}>
        <div className="bullet__text" dangerouslySetInnerHTML={{ __html: markdownBoldToHtml(bullet.text) }} />
        {fit !== 'OFF' && (
          <div
            title={fitHint(bullet.text, cfg)}
            style={{
              marginTop: 6, fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.12em',
              color: bad ? 'var(--rust)' : 'var(--muted)',
            }}
          >
            {FIT_LABEL[fit]} · {charCount(bullet.text)}c
          </div>
        )}
        {categoryLabel && (
          <div style={{ marginTop: 6, fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--muted)', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
            {categoryLabel.label} — <span style={{ textTransform: 'none', letterSpacing: '0.04em' }}>{categoryLabel.blurb}</span>
          </div>
        )}
        <div className="bullet__tags" style={{ marginTop: 6 }}>
          {bullet.tags.map(t => <span key={t} className="tag">{t}</span>)}
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <button className="btn btn--ghost btn--sm" onClick={onEdit}>EDIT</button>
          <button className="btn btn--ghost btn--sm btn--rust" onClick={onDelete}>DELETE</button>
          {onToggleApprove && (
            <button
              className="btn btn--ghost btn--sm"
              style={approved ? { textDecoration: 'underline', textUnderlineOffset: 4 } : undefined}
              onClick={onToggleApprove}
            >
              {approved ? 'APPROVED' : 'APPROVE'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
