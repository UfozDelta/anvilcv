import type { Bullet } from '../../lib/api';
import type { BulletGroup } from '../../lib/groupBullets';
import { RankedBulletRow } from './RankedBulletRow';

export function BulletGroupSection({ g, open, selectedIds, expandedWhys, bullets, groupCap, onToggleOpen, onToggleSelect, onToggleWhy }: {
  g: BulletGroup;
  open: boolean;
  selectedIds: Set<string>;
  expandedWhys: Set<string>;
  bullets: Record<string, Bullet>;
  groupCap: number;
  onToggleOpen: () => void;
  onToggleSelect: (bulletId: string) => void;
  onToggleWhy: (bulletId: string) => void;
}) {
  const visible = open ? g.items : g.items.slice(0, groupCap);
  const hidden = g.items.length - visible.length;
  const name = g.project?.name ?? 'Other';
  const selCount = g.items.filter(r => selectedIds.has(r.bulletId)).length;
  return (
    <div style={{ marginBottom: 14 }}>
      <div className="row row--between row--centered" style={{ marginBottom: 4 }}>
        <span className="label" style={{ fontWeight: 700 }}>{name}</span>
        <span className="label muted">{selCount}/{g.items.length}</span>
      </div>
      {visible.map(r => (
        <RankedBulletRow
          key={r.bulletId}
          r={r}
          bullet={bullets[r.bulletId]}
          isSelected={selectedIds.has(r.bulletId)}
          whyOpen={expandedWhys.has(r.bulletId)}
          onToggleSelect={() => onToggleSelect(r.bulletId)}
          onToggleWhy={() => onToggleWhy(r.bulletId)}
        />
      ))}
      {(hidden > 0 || open) && g.items.length > groupCap && (
        <button
          className="btn btn--ghost btn--sm"
          style={{ marginTop: 4, width: '100%', fontSize: 10 }}
          onClick={onToggleOpen}
        >
          {open ? `SHOW LESS` : `+${hidden} MORE`}
        </button>
      )}
    </div>
  );
}
