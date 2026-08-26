import type { Bullet } from '../../lib/api';
import type { BulletGroup } from '../../lib/groupBullets';
import { RankedBulletRow } from './RankedBulletRow';

export function BulletGroupSection({ g, open, selectedIds, expandedWhys, bullets, onToggleOpen, onToggleSelect, onToggleWhy }: {
  g: BulletGroup;
  open: boolean;
  selectedIds: Set<string>;
  expandedWhys: Set<string>;
  bullets: Record<string, Bullet>;
  onToggleOpen: () => void;
  onToggleSelect: (bulletId: string) => void;
  onToggleWhy: (bulletId: string) => void;
}) {
  const name = g.project?.name ?? 'Other';
  const selCount = g.items.filter(r => selectedIds.has(r.bulletId)).length;
  return (
    <div style={{ marginBottom: 14 }}>
      <button
        type="button"
        className="row row--between row--centered"
        aria-expanded={open}
        onClick={onToggleOpen}
        style={{ width: '100%', marginBottom: 4, background: 'none', border: 'none', padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', textAlign: 'left' }}
      >
        <span className="label" style={{ fontWeight: 700 }}>{open ? '▾' : '▸'} {name}</span>
        <span className="label muted">{selCount}/{g.items.length}</span>
      </button>
      {open && g.items.map(r => (
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
    </div>
  );
}
