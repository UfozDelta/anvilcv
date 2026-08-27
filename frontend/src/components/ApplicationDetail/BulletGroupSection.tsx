import type { Bullet } from '../../lib/api';
import type { BulletGroup } from '../../lib/groupBullets';
import { estimatedLines } from '../../lib/bulletLength';
import { RankedBulletRow } from './RankedBulletRow';

export function BulletGroupSection({ g, open, selectedIds, expandedWhys, bullets, previewing, previewBusy, onToggleOpen, onToggleSelect, onToggleWhy, onPreview }: {
  g: BulletGroup;
  open: boolean;
  selectedIds: Set<string>;
  expandedWhys: Set<string>;
  bullets: Record<string, Bullet>;
  previewing: boolean;
  previewBusy: boolean;
  onToggleOpen: () => void;
  onToggleSelect: (bulletId: string) => void;
  onToggleWhy: (bulletId: string) => void;
  onPreview: (bulletIds: string[]) => void;
}) {
  const name = g.project?.name ?? 'Other';
  const selected = g.items.filter(r => selectedIds.has(r.bulletId));
  // Rendered-line share this group contributes to the one-page budget.
  const lines = selected.reduce((n, r) => n + estimatedLines(bullets[r.bulletId]?.text ?? ''), 0);
  return (
    <div style={{ marginBottom: 14 }}>
      <div className="row row--between row--centered" style={{ marginBottom: 4, gap: 8 }}>
        <button
          type="button"
          className="label"
          aria-expanded={open}
          onClick={onToggleOpen}
          style={{ flex: 1, minWidth: 0, fontWeight: 700, background: 'none', border: 'none', padding: 0, cursor: 'pointer', font: 'inherit', color: 'inherit', textAlign: 'left' }}
        >
          {open ? '▾' : '▸'} {name}
        </button>
        <span className="label muted" style={{ whiteSpace: 'nowrap' }}>~{lines}L · {selected.length}/{g.items.length}</span>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          style={{ fontSize: 10, padding: '2px 6px', ...(previewing ? { textDecoration: 'underline', textUnderlineOffset: 4 } : {}) }}
          disabled={selected.length === 0 || previewBusy}
          title={selected.length === 0 ? 'No bullets included' : 'Render just these bullets'}
          onClick={() => onPreview(selected.map(r => r.bulletId))}
        >
          {previewBusy ? '...' : 'TEST'}
        </button>
      </div>
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
