import { useState } from 'react';
import { RankedBulletRow } from '../components/ApplicationDetail/RankedBulletRow';
import { LAB_BULLET_MAP, LAB_CFG, LAB_RANKING, LAB_VERDICTS } from './fixtures';
import { BulletRow } from './LabBulletRow';
import { LabChrome } from './LabChrome';

const VERDICT_MAP = Object.fromEntries(LAB_VERDICTS.map(v => [v.bulletId, v]));

/** The five ranks that exercise every row state worth comparing. */
const SAMPLE = LAB_RANKING.filter(r => ['b1', 'b6', 'b7', 'b11', 'b5'].includes(r.bulletId));

/**
 * Side-by-side of the shipped row component and the proposed one, driven by one
 * shared piece of state so the same click is visible in both.
 */
export function LabRows() {
  const [selected, setSelected] = useState<Set<string>>(new Set(['b1', 'b6', 'b7', 'b11']));
  const [locked, setLocked] = useState<Set<string>>(new Set(['b1']));
  const [whys, setWhys] = useState<Set<string>>(new Set());

  function toggle(setter: React.Dispatch<React.SetStateAction<Set<string>>>, id: string) {
    setter(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  return (
    <LabChrome
      title="Bullet row — before / after"
      note={
        <>
          Same data, same state, both columns live. The left column is the component
          shipping today. Note what happens when you exclude a bullet on the left: the text
          fades to 45% opacity — the state you need to read to decide whether to put it back
          is the state that makes it hardest to read — and its recruiter verdict disappears
          entirely, because verdicts only render when a bullet is selected.
        </>
      }
    >
      <div className="labsplit">
        <div>
          <div className="eyebrow" style={{ marginBottom: 4 }}>Today</div>
          <p style={{ fontSize: 12, color: 'var(--muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
            Include/exclude = click the rank number. Excluded = 45% opacity. Verdict chip and
            tags only appear when included. Reasons live in tooltips.
          </p>
          <div style={{ borderTop: '2px solid var(--ink)' }}>
            {SAMPLE.map(r => (
              <RankedBulletRow
                key={r.bulletId}
                r={r}
                bullet={LAB_BULLET_MAP[r.bulletId]}
                isSelected={selected.has(r.bulletId)}
                whyOpen={whys.has(r.bulletId)}
                verdict={VERDICT_MAP[r.bulletId]}
                editing={false}
                cfg={LAB_CFG}
                locked={locked.has(r.bulletId)}
                onToggleSelect={() => toggle(setSelected, r.bulletId)}
                onToggleWhy={() => toggle(setWhys, r.bulletId)}
                onEdit={() => {}}
                onCancelEdit={() => {}}
                onSaveBullet={() => {}}
                onToggleLock={() => toggle(setLocked, r.bulletId)}
              />
            ))}
          </div>
        </div>

        <div>
          <div className="eyebrow" style={{ marginBottom: 4 }}>Proposed</div>
          <p style={{ fontSize: 12, color: 'var(--muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
            State is a labelled button reading IN or OUT. Excluded rows keep full contrast and
            get a hatched ground. Verdict stays visible either way. Line cost is on the row.
            Secondary actions appear on hover so the resting row is just text.
          </p>
          <div style={{ borderTop: '2px solid var(--ink)' }}>
            {SAMPLE.map(r => (
              <BulletRow
                key={r.bulletId}
                r={r}
                bullet={LAB_BULLET_MAP[r.bulletId]}
                isIn={selected.has(r.bulletId)}
                isLocked={locked.has(r.bulletId)}
                verdict={VERDICT_MAP[r.bulletId]}
                whyOpen={whys.has(r.bulletId)}
                onToggleIn={() => toggle(setSelected, r.bulletId)}
                onToggleLock={() => toggle(setLocked, r.bulletId)}
                onToggleWhy={() => toggle(setWhys, r.bulletId)}
              />
            ))}
          </div>
        </div>
      </div>
    </LabChrome>
  );
}
