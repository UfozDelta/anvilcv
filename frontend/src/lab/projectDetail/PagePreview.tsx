import { CATEGORIES, type Bullet } from '../../lib/api';
import { LAB_MAX_LINES } from '../fixtures';
import { RichText } from '../LabChrome';

const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.slug, c]));

export function PagePreview({ bullets, lines, over, onClose }: {
  bullets: Bullet[]; lines: number; over: boolean; onClose: () => void;
}) {
  const groups = new Map<string, Bullet[]>();
  for (const b of bullets) {
    const list = groups.get(b.category) ?? [];
    list.push(b);
    groups.set(b.category, list);
  }
  return (
    <div style={{ marginBottom: 20 }}>
      <div className="row row--between row--centered" style={{ background: 'var(--acid)', color: 'var(--ink)', padding: '6px 10px', border: '2px solid var(--ink)', borderBottom: 'none' }}>
        <span className="label" style={{ fontWeight: 700 }}>
          PREVIEW · {bullets.length} BULLETS ({bullets.filter(b => b.status === 'APPROVED').length} APPROVED) · NOT SAVED
        </span>
        <button className="btn btn--ghost btn--sm" style={{ fontSize: 10, padding: '2px 6px' }} onClick={onClose}>✕ CLOSE</button>
      </div>
      <div style={{ background: '#fff', border: '2px solid var(--ink)', padding: '24px 26px', maxHeight: 480, overflow: 'auto' }}>
        {[...groups.entries()].map(([slug, rows]) => (
          <div key={slug} style={{ marginBottom: 14 }}>
            <div style={{ fontFamily: 'var(--serif)', fontWeight: 700, fontSize: 13, borderBottom: '1px solid #000', paddingBottom: 2, marginBottom: 5 }}>
              {CATEGORY_MAP[slug]?.label ?? slug}
            </div>
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              {rows.map(b => (
                <li key={b.id} style={{ fontSize: 11.5, lineHeight: 1.45, marginBottom: 3, opacity: b.status === 'APPROVED' ? 1 : 0.75 }}>
                  <RichText text={b.text} />
                  {b.status !== 'APPROVED' && (
                    <span style={{ fontFamily: 'var(--mono)', fontSize: 8.5, color: 'var(--muted)', marginLeft: 6 }}>
                      not yet approved
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
        {bullets.length === 0 && <div className="editorial muted">Nothing in the bank yet.</div>}
        {over && (
          <div style={{ borderTop: '2px dashed var(--rust)', color: 'var(--rust)', fontFamily: 'var(--mono)', fontSize: 10, fontWeight: 700, letterSpacing: '0.14em', paddingTop: 6, marginTop: 10 }}>
            PAGE 1 ENDS HERE — {lines - LAB_MAX_LINES} LINE(S) SPILL ONTO PAGE 2
          </div>
        )}
      </div>
    </div>
  );
}
