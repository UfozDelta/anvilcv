import type { Bullet, BulletVerdict, GenerationConfig, Project, RankedBullet } from '../../lib/api';
import { bankRows, type BulletGroup } from '../../lib/groupBullets';
import { estimatedLines } from '../../lib/bulletLength';
import { RankedBulletRow } from './RankedBulletRow';
import { EditProjectHeader } from './EditProjectHeader';

export function BulletGroupSection({ g, open, selectedIds, expandedWhys, bullets, verdicts, previewing, previewBusy, refitBusy, editingId, cfg, editingProjectId, lockedIds, newIds, rankedIds, bankOpen, onToggleBank, onToggleOpen, onRefit, onToggleSelect, onToggleWhy, onPreview, onEdit, onCancelEdit, onSaveBullet, onEditProject, onCancelEditProject, onSaveProject, onToggleLock }: {
  g: BulletGroup;
  open: boolean;
  selectedIds: Set<string>;
  expandedWhys: Set<string>;
  bullets: Record<string, Bullet>;
  verdicts: Record<string, BulletVerdict>;
  previewing: boolean;
  previewBusy: boolean;
  /** Any refit is in flight — a second one would race the first onto the same selection. */
  refitBusy: boolean;
  editingId: string | null;
  cfg: GenerationConfig;
  editingProjectId: string | null;
  lockedIds: Set<string>;
  newIds?: Set<string>;
  rankedIds: Set<string>;
  bankOpen: boolean;
  onToggleBank: () => void;
  onToggleOpen: () => void;
  onRefit: (projectId: string) => void;
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
  // The rest of this entry's bank, for picking by hand beyond the ranked shortlist. Picking one
  // moves it into g.items (it becomes a selected orphan row), so it leaves this list.
  const extra = g.project ? bankRows(g.project.id, bullets, new Set(g.items.map(r => r.bulletId))) : [];
  const headerEditing = g.project && editingProjectId === g.project.id;
  const row = (r: RankedBullet) => (
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
      isNew={newIds?.has(r.bulletId)}
      unranked={!rankedIds.has(r.bulletId)}
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
  );
  return (
    <div style={{ marginBottom: 14 }}>
      {headerEditing && g.project ? (
        <EditProjectHeader
          project={g.project}
          onCancel={onCancelEditProject}
          onSave={patch => onSaveProject(g.project!, patch)}
        />
      ) : (
        <div
          className="grouphead"
          role="button"
          tabIndex={0}
          aria-expanded={open}
          onClick={onToggleOpen}
          onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onToggleOpen(); } }}
        >
          <div>
            <div className="grouphead__name">{open ? '▾' : '▸'} {name}</div>
            {/* Spelled out. "~4L · 3/7" needed a decoder ring. */}
            <div className="grouphead__sub">
              {selected.length} of {g.items.length} included · about {lines} line{lines === 1 ? '' : 's'}
            </div>
          </div>
          <div className="grouphead__right" onClick={e => e.stopPropagation()}>
            {/* Scoped re-pick. The "Other" bucket has no project to scope to, so it only
                ever gets the whole-page button in the action bar. */}
            <button
              type="button"
              className="minibtn"
              disabled={!g.project || refitBusy}
              title={
                g.project
                  ? 'Re-pick this entry from the bank. Every other entry stays exactly as it is.'
                  : 'These bullets have no project to re-pick against'
              }
              onClick={() => g.project && onRefit(g.project.id)}
            >
              Auto-pick this entry
            </button>
            <button
              type="button"
              className={`minibtn ${previewing ? 'is-on' : ''}`}
              disabled={selected.length === 0 || previewBusy}
              title={selected.length === 0 ? 'No bullets included' : 'Render just these bullets'}
              onClick={() => onPreview(selected.map(r => r.bulletId))}
            >
              {previewBusy ? '…' : 'Preview just this'}
            </button>
            {g.project && (
              <button
                type="button"
                className="minibtn"
                onClick={() => onEditProject(g.project!.id)}
              >
                Edit heading
              </button>
            )}
          </div>
        </div>
      )}
      {open && g.items.map(row)}
      {open && extra.length > 0 && (
        <button type="button" className={`minibtn ${bankOpen ? 'is-on' : ''}`} style={{ marginTop: 6 }} onClick={onToggleBank}>
          {bankOpen ? 'Hide bank' : `+ ${extra.length} more from bank`}
        </button>
      )}
      {open && bankOpen && extra.map(row)}
    </div>
  );
}
