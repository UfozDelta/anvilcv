import type { Dispatch, SetStateAction } from 'react';
import { CATEGORIES, type Bullet } from '../../lib/api';
import { AddBulletForm } from './AddBulletForm';
import { EditBulletForm } from './EditBulletForm';
import { ManagedBulletRow } from './ManagedBulletRow';
import { PagePreview } from './PagePreview';

const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));

type SortMode = 'category' | 'date';
type StatusTab = 'bank' | 'approved';

export function BulletsTab(props: {
  statusTab: StatusTab; setStatusTab: (t: StatusTab) => void;
  bankCount: number; approvedCount: number;
  sortMode: SortMode; setSortMode: (m: SortMode) => void;
  offBand: Bullet[]; refitting: boolean; refit: () => void; refitMsg: string | null;
  adding: boolean; setAdding: Dispatch<SetStateAction<boolean>>;
  editingId: string | null; setEditingId: (id: string | null) => void;
  previewOpen: boolean; setPreviewOpen: (v: boolean) => void;
  previewBusy: boolean; setPreviewBusy: (v: boolean) => void;
  pageLines: number; overBudget: boolean; pageBullets: Bullet[];
  byCategory: { slug: string; label: string; blurb?: string; rows: Bullet[] }[];
  visible: { slug: string; label: string; blurb?: string; rows: Bullet[] }[];
  filterCat: string | null; setFilterCat: Dispatch<SetStateAction<string | null>>;
  closedGroups: Set<string>; toggleGroup: (slug: string) => void;
  flatByDate: Bullet[];
  displayed: Bullet[];
  addBullet: (text: string, tags: string[], category: string) => void;
  saveEdit: (id: string, text: string, tags: string[]) => void;
  deleteBullet: (id: string) => void;
  toggleApprove: (id: string) => void;
}) {
  const {
    statusTab, setStatusTab, bankCount, approvedCount, sortMode, setSortMode,
    offBand, refitting, refit, refitMsg, adding, setAdding, editingId, setEditingId,
    previewOpen, setPreviewOpen, previewBusy, setPreviewBusy, pageLines, overBudget,
    pageBullets, byCategory, visible, filterCat, setFilterCat, closedGroups, toggleGroup,
    flatByDate, displayed, addBullet, saveEdit, deleteBullet, toggleApprove,
  } = props;

  return (
    <div>
      <div className="row row--between row--centered" style={{ marginBottom: 14, flexWrap: 'wrap', gap: 10 }}>
        <div className="filterset">
          <button className={statusTab === 'bank' ? 'is-on' : ''} onClick={() => setStatusTab('bank')}>
            Bank <span className="filterset__count">{bankCount}</span>
          </button>
          <button className={statusTab === 'approved' ? 'is-on' : ''} onClick={() => setStatusTab('approved')}>
            Approved <span className="filterset__count">{approvedCount}</span>
          </button>
        </div>

        <div className="row" style={{ gap: 8 }}>
          <div className="filterset">
            <button className={sortMode === 'category' ? 'is-on' : ''} onClick={() => setSortMode('category')}>By category</button>
            <button className={sortMode === 'date' ? 'is-on' : ''} onClick={() => setSortMode('date')}>By date</button>
          </div>
          <button
            className="minibtn"
            onClick={refit}
            disabled={refitting || offBand.length === 0}
            title="Rewrite bullets whose length misses the page bands"
          >
            {refitting ? 'Refitting…' : offBand.length === 0 ? 'All fit' : `Refit ${offBand.length}`}
          </button>
          <button className="minibtn" onClick={() => { setAdding(a => !a); setEditingId(null); }}>
            {adding ? '✕ Cancel' : '+ Add bullet'}
          </button>
          <button
            className="btn btn--sm"
            style={{ background: 'var(--acid)', borderWidth: 2 }}
            onClick={() => { setPreviewBusy(true); setPreviewOpen(true); window.setTimeout(() => setPreviewBusy(false), 600); }}
            disabled={pageBullets.length === 0}
          >
            {previewBusy ? 'Rendering…' : `Render PDF (~${pageLines}L)`}
          </button>
        </div>
      </div>

      {refitMsg && <div className="label muted" style={{ marginBottom: 10 }}>{refitMsg}</div>}

      {previewOpen && (
        <PagePreview
          bullets={pageBullets}
          lines={pageLines}
          over={overBudget}
          onClose={() => setPreviewOpen(false)}
        />
      )}

      {adding && <AddBulletForm onSave={addBullet} onCancel={() => setAdding(false)} />}

      {byCategory.length > 1 && sortMode === 'category' && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 18 }}>
          <button
            className="btn btn--sm"
            onClick={() => setFilterCat(null)}
            style={{ background: filterCat === null ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)' }}
          >ALL</button>
          {byCategory.map(g => (
            <button
              key={g.slug}
              className="btn btn--sm"
              onClick={() => setFilterCat(c => c === g.slug ? null : g.slug)}
              style={{
                background: filterCat === g.slug ? 'var(--acid)' : 'var(--paper)',
                color: 'var(--ink)', borderColor: 'var(--ink)',
              }}
            >
              {g.label} <span style={{ opacity: 0.6, marginLeft: 4 }}>{g.rows.length}</span>
            </button>
          ))}
        </div>
      )}

      {sortMode === 'category' ? visible.map(g => {
        const open = !closedGroups.has(g.slug);
        return (
          <div key={g.slug} style={{ marginBottom: 28 }}>
            <div
              className="grouphead"
              role="button"
              tabIndex={0}
              aria-expanded={open}
              onClick={() => toggleGroup(g.slug)}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleGroup(g.slug); } }}
              style={{ marginBottom: open ? 12 : 0, paddingBottom: 6, borderBottom: 'var(--rule-thick)', cursor: 'pointer' }}
            >
              <div className="row row--between row--centered">
                <span className="label">{open ? '▾' : '▸'} {g.label}</span>
                <span className="label muted">{g.rows.length}</span>
              </div>
            </div>
            {open && g.rows.map(b => editingId === b.id ? (
              <EditBulletForm key={b.id} bullet={b} onSave={(t, tg) => saveEdit(b.id, t, tg)} onCancel={() => setEditingId(null)} />
            ) : (
              <ManagedBulletRow
                key={b.id} bullet={b}
                onEdit={() => { setEditingId(b.id); setAdding(false); }}
                onDelete={() => deleteBullet(b.id)}
                onToggleApprove={() => toggleApprove(b.id)}
              />
            ))}
          </div>
        );
      }) : (
        <div>
          {flatByDate.map(b => editingId === b.id ? (
            <EditBulletForm key={b.id} bullet={b} onSave={(t, tg) => saveEdit(b.id, t, tg)} onCancel={() => setEditingId(null)} />
          ) : (
            <ManagedBulletRow
              key={b.id} bullet={b} categoryLabel={CATEGORY_MAP[b.category]}
              onEdit={() => { setEditingId(b.id); setAdding(false); }}
              onDelete={() => deleteBullet(b.id)}
              onToggleApprove={() => toggleApprove(b.id)}
            />
          ))}
        </div>
      )}

      {displayed.length === 0 && !adding && (
        <div className="editorial muted" style={{ padding: '40px 0' }}>
          {statusTab === 'approved' ? 'No approved bullets yet.' : 'Bank is empty. Generate more, or add one above.'}
        </div>
      )}
    </div>
  );
}
