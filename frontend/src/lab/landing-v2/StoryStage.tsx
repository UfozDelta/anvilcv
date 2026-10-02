import { motion } from 'framer-motion';
import { Marked } from './Marked';
import { ResumePage } from './ResumePage';
import { BANK, JOBS, rankBank } from '../../components/landing/storyData';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

const JOB = JOBS[0];
const ROWS = rankBank(JOB);
const BY_ID = Object.fromEntries(ROWS.map((r, i) => [r.bullet.id, { ...r, rank: i + 1 }]));
// No bounce: a scroll-triggered re-sort carries no momentum to overshoot with.
const SORT = { type: 'spring', duration: 0.6, bounce: 0 } as const;

const STATUS = ['PDF READY', '12 BULLETS', 'JD PARSED', '12 / 12 SCORED', 'PDF READY'];

/**
 * The pinned visual of the hero story. step: 0 hero (finished page), 1 bank,
 * 2 job pasted, 3 ranked, 4 page again. Each state is a class/prop change, so a
 * fast scroll back and forth retargets mid-flight instead of queueing.
 */
export function StoryStage({ step }: { step: number }) {
  const reduced = usePrefersReducedMotion();
  const ranked = step >= 3;
  const order = ranked ? ROWS.map((r) => r.bullet) : BANK;
  const showPage = step === 0 || step === 4;

  return (
    <div className="lv-stage" data-step={step} aria-hidden="true">
      <div className="lv-stage__bar">
        <span>{showPage ? 'jordan-reyes.pdf' : 'bullet-bank'}</span>
        <span key={step} className="lv-stage__status">{STATUS[step]}</span>
      </div>

      <div className="lv-stage__jd" data-on={step >= 2 || undefined}>
        <span className="lv-stage__prompt">$ paste job post</span>
        {/* The ghost reserves the JD's height, so the bank never shifts when it lands. */}
        <p className="lv-stage__jd-text">
          <span className="lv-stage__ghost">{JOB.jd}</span>
          {step >= 2
            ? <span className="lv-stage__jd-live"><Marked text={JOB.jd} keywords={JOB.keywords} /></span>
            : <span className="lv-stage__wait">waiting for a job post…</span>}
        </p>
      </div>

      <ul className="lv-stage__bank">
        {order.map((b) => {
          const r = BY_ID[b.id];
          return (
            <motion.li
              key={b.id}
              layout={reduced ? false : 'position'}
              transition={SORT}
              className="lv-row"
              data-status={ranked ? r.status : undefined}
            >
              <span className="lv-row__rank">{ranked ? String(r.rank).padStart(2, '0') : '·'}</span>
              <span className="lv-row__text">
                {step >= 2 ? <Marked text={b.text} keywords={JOB.keywords} /> : b.text}
              </span>
              <span className="lv-row__score">
                <span className="lv-row__bar" style={{ transform: `scaleX(${ranked ? r.score / 100 : 0})` }} />
                <span className="lv-row__num">{ranked ? r.score : ''}</span>
              </span>
            </motion.li>
          );
        })}
      </ul>

      <div className="lv-stage__page" data-on={showPage || undefined}>
        <ResumePage job={JOB} rows={ROWS} />
      </div>
    </div>
  );
}
