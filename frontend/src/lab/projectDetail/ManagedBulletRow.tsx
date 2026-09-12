import { useState } from 'react';
import type { Bullet } from '../../lib/api';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { LAB_CFG } from '../fixtures';
import { RichText } from '../LabChrome';

export function ManagedBulletRow({ bullet, categoryLabel, onEdit, onDelete, onToggleApprove }: {
  bullet: Bullet;
  categoryLabel?: { label: string; blurb: string };
  onEdit: () => void;
  onDelete: () => void;
  onToggleApprove: () => void;
}) {
  const approved = bullet.status === 'APPROVED';
  const fit = fitOf(bullet.text, LAB_CFG);
  const bad = needsRefit(fit);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);

  return (
    <div className={`brow ${approved ? 'is-in' : ''}`}>
      <button className="brow__toggle" onClick={onToggleApprove} aria-pressed={approved}>
        <span className={`brow__state ${approved ? 'brow__state--in' : 'brow__state--out'}`}>
          {approved ? '✓ APPROVED' : 'NORMAL'}
        </span>
        <span className="brow__rank">{estimatedLines(bullet.text)}L</span>
      </button>

      <div>
        <div className="brow__text"><RichText text={bullet.text} /></div>

        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginTop: 5 }}>
          {fit !== 'OFF' && (
            <span
              title={fitHint(bullet.text, LAB_CFG)}
              style={{
                fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.1em',
                color: bad ? 'var(--rust)' : 'var(--muted)',
              }}
            >
              {FIT_LABEL[fit]} · {charCount(bullet.text)}c
            </span>
          )}

          {categoryLabel && (
            <span style={{ fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--muted)', letterSpacing: '0.1em', textTransform: 'uppercase' }}>
              {categoryLabel.label}
            </span>
          )}

          {bullet.tags.map(t => (
            <span key={t} style={{
              fontFamily: 'var(--mono)', fontSize: 9.5, color: 'var(--muted)', letterSpacing: '0.03em',
              border: '1px solid var(--soft)', padding: '1px 5px',
            }}>
              {t}
            </span>
          ))}
        </div>

        <div className="brow__under">
          <div className="brow__actions" style={{ marginLeft: 0 }} onMouseLeave={() => { setMenuOpen(false); setConfirming(false); }}>
            <button className="minibtn" onClick={onEdit}>Edit</button>
            <div className="rowmenu" style={{ display: 'inline-block' }}>
              <button className="rowmenu__btn" aria-label="Bullet actions" aria-expanded={menuOpen} onClick={() => setMenuOpen(o => !o)}>⋯</button>
              {menuOpen && (
                <div className="rowmenu__pop">
                  {confirming ? (
                    <button className="is-danger" onClick={() => { onDelete(); setMenuOpen(false); setConfirming(false); }}>
                      Really delete?
                    </button>
                  ) : (
                    <button className="is-danger" onClick={() => setConfirming(true)}>Delete</button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
