import type { Bullet } from '../../lib/api';
import { markdownBoldToHtml } from '../../lib/markdown';

export function BulletRow({ bullet, index, onEdit, onDelete }: {
  bullet: Bullet;
  index: number;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="bullet">
      <div className="bullet__rank">#{String(index + 1).padStart(2, '0')}</div>
      <div>
        <div className="bullet__text" dangerouslySetInnerHTML={{ __html: markdownBoldToHtml(bullet.text) }} />
        <div className="bullet__tags">
          {bullet.tags.map(t => <span key={t} className="tag">{t}</span>)}
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <button className="btn btn--ghost btn--sm" onClick={onEdit}>EDIT</button>
          <button className="btn btn--ghost btn--sm btn--rust" onClick={onDelete}>DELETE</button>
        </div>
      </div>
    </div>
  );
}
