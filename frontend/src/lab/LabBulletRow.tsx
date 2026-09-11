import type { Bullet, BulletVerdict, RankedBullet } from '../lib/api';
import { estimatedLines } from '../lib/bulletLength';
import { RichText } from './LabChrome';

// ---------------------------------------------------------------- bullet row

export function BulletRow({ r, bullet, isIn, isLocked, verdict, whyOpen, onToggleIn, onToggleLock, onToggleWhy }: {
  r: RankedBullet;
  bullet: Bullet | undefined;
  isIn: boolean;
  isLocked: boolean;
  verdict?: BulletVerdict;
  whyOpen: boolean;
  onToggleIn: () => void;
  onToggleLock: () => void;
  onToggleWhy: () => void;
}) {
  const cost = estimatedLines(bullet?.text ?? '');
  return (
    <div className={`brow ${isIn ? 'is-in' : 'is-out'}`}>
      {/* A real button, with the state written out. Not a dimmed div. */}
      <button className="brow__toggle" onClick={onToggleIn} aria-pressed={isIn}>
        <span className={`brow__state ${isIn ? 'brow__state--in' : 'brow__state--out'}`}>
          {isIn ? '✓ IN' : 'OUT'}
        </span>
        <span className="brow__rank">
          rank {r.rank} · {cost}L
        </span>
      </button>

      <div>
        <div className="brow__text">
          {isLocked && <span title="Locked — survives auto-pick" style={{ marginRight: 5 }}>🔒</span>}
          {bullet ? <RichText text={bullet.text} /> : <em className="muted">— bullet missing —</em>}
        </div>

        <div className="brow__under">
          {/* Verdict stays visible whether or not the bullet is included — it is the
              reason you would change your mind either way. */}
          {verdict && (
            <span className={`vchip vchip--${verdict.verdict}`} title={verdict.reason}>
              {verdict.verdict}
            </span>
          )}
          {bullet?.tags.map(t => <span key={t} className="kw">{t}</span>)}

          <div className="brow__actions">
            {r.why && (
              <button className={`minibtn ${whyOpen ? 'is-on' : ''}`} onClick={onToggleWhy}>
                Why {whyOpen ? '↑' : '↓'}
              </button>
            )}
            <button className={`minibtn ${isLocked ? 'is-on' : ''}`} onClick={onToggleLock}>
              {isLocked ? 'Unlock' : 'Lock'}
            </button>
            <button className="minibtn">Edit</button>
          </div>
        </div>

        {whyOpen && (
          <div className="brow__why">
            <b>Why it ranked {r.rank}</b>
            {r.why}
            {verdict && (
              <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--soft)' }}>
                <b>Recruiter pass said “{verdict.verdict}”</b>
                {verdict.reason}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
