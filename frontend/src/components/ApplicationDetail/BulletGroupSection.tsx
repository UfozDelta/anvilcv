import type { Bullet, BulletVerdict, GenerationConfig, Project } from '../../lib/api';
import type { BulletGroup } from '../../lib/groupBullets';
import { estimatedLines } from '../../lib/bulletLength';
import { RankedBulletRow } from './RankedBulletRow';
import { EditProjectHeader } from './EditProjectHeader';

export function BulletGroupSection({ g, open, selectedIds, expandedWhys, bullets, verdicts, previewing, previewBusy, editingId, cfg, editingProjectId, lockedIds, onToggleOpen, onToggleSelect, onToggleWhy, onPreview, onEdit, onCancelEdit, onSaveBullet, onEditProject, onCancelEditProject, onSaveProject, onToggleLock }: {
  g: BulletGroup;
  open: boolean;
  selectedIds: Set<string>;
  expandedWhys: Set<string>;
  bullets: Record<string, Bullet>;
  verdicts: Record<string, BulletVerdict>;
  previewing: boolean;
  previewBusy: boolean;
  editingId: string | null;
  cfg: GenerationConfig;
  editingProjectId: string | null;
  lockedIds: Set<string>;
  onToggleOpen: () => void;
  onToggleSelect: (bulletId: string) => void;
  onToggleWhy: (bulletId: string) => void;
  onPreview: (bulletIds: string[]) => void;
  onEdit: (bulletId: string) => void;
  onCancelEdit: () => void;
  onSaveBullet: (bullet: Bullet, text: string, tags: string[]) => void;
  onEditProject: (projectId: string) => void;
  onCancelEditProject: () => void;
  onSaveProject: (project: Project, patch: Partial<Project>) => void;
  onToggleLock: (bulletId: string) => void;
}) {
  const name = g.project?.name ?? 'Other';
  const selected = g.items.filter(r => selectedIds.has(r.bulletId));
  // Rendered-line share this group contributes to the one-page budget.
  const lines = selected.reduce((n, r) => n + estimatedLines(bullets[r.bulletId]?.text ?? ''), 0);
  const headerEditing = g.project && editingProjectId === g.project.id;
  return (
    <div style={{ marginBottom: 14 }}>
      {headerEditing && g.project ? (
        <EditProjectHeader
          project={g.project}
          onCancel={onCancelEditProject}
          onSave={patch => onSaveProject(g.project!, patch)}
        />
      ) : (
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
          {g.project && (
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              style={{ fontSize: 10, padding: '2px 6px' }}
              onClick={() => onEditProject(g.project!.id)}
            >
              EDIT
            </button>
          )}
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
      )}
      {open && g.items.map(r => (
        <RankedBulletRow
          key={r.bulletId}
          r={r}
          bullet={bullets[r.bulletId]}
          isSelected={selectedIds.has(r.bulletId)}
          whyOpen={expandedWhys.has(r.bulletId)}
          verdict={verdicts[r.bulletId]}
          editing={editingId === r.bulletId}
          cfg={cfg}
          locked={lockedIds.has(r.bulletId)}
          onToggleSelect={() => onToggleSelect(r.bulletId)}
          onToggleWhy={() => onToggleWhy(r.bulletId)}
          onEdit={() => onEdit(r.bulletId)}
          onCancelEdit={onCancelEdit}
          onSaveBullet={(text, tags) => {
            const b = bullets[r.bulletId];
            if (b) onSaveBullet(b, text, tags);
          }}
          onToggleLock={() => onToggleLock(r.bulletId)}
        />
      ))}
    </div>
  );
}
