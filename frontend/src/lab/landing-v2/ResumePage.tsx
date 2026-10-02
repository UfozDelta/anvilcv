import { AnimatePresence, motion } from 'framer-motion';
import { Marked } from './Marked';
import { ENTRIES, type Job, type RankedRow } from '../../components/landing/storyData';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

const EASE_OUT = [0.23, 1, 0.32, 1] as const;

/**
 * The one-page résumé. The same component is the hero's page, the story's last step
 * and the job-switch output, so it reads as one page changing rather than three.
 * Bullets that join the page slide in from the left (where the bank sits); ones that
 * leave just fade, and the rest glide to their new spot (layout).
 */
export function ResumePage({ job, rows }: { job: Job; rows: RankedRow[] }) {
  const reduced = usePrefersReducedMotion();
  const onPage = rows.filter((r) => r.status === 'page');

  return (
    <div className="lv-page">
      <div className="lv-page__name">Jordan Reyes</div>
      <div className="lv-page__contact">
        jordan@reyes.dev · github.com/jreyes ·{' '}
        <span key={job.id} className="lv-page__target">{job.role}</span>
      </div>
      <div className="lv-page__rule" />
      {(['EXPERIENCE', 'PROJECTS'] as const).map((kind) => (
        <div key={kind}>
          <div className="lv-page__section">{kind}</div>
          {/* Entries with nothing picked for this job are left off the page, like a real résumé. */}
          {ENTRIES.filter((e) => e.kind === kind && onPage.some((r) => r.bullet.entry === e.id)).map((e) => (
            <motion.div key={e.id} layout={reduced ? false : 'position'} transition={{ duration: 0.3, ease: EASE_OUT }} className="lv-page__entry">
              <div className="lv-page__entry-head">
                <strong>{e.org}</strong> <em>{e.title}</em>
                {e.when && <span>{e.when}</span>}
              </div>
              <ul className="lv-page__bullets">
                <AnimatePresence initial={false} mode="popLayout">
                  {onPage.filter((r) => r.bullet.entry === e.id).map((r) => (
                    <motion.li
                      key={r.bullet.id}
                      layout={!reduced}
                      initial={reduced ? { opacity: 0 } : { opacity: 0, transform: 'translateX(-16px)' }}
                      animate={{ opacity: 1, transform: 'translateX(0px)' }}
                      exit={{ opacity: 0, transition: { duration: 0.12 } }}
                      transition={{ duration: 0.3, ease: EASE_OUT }}
                    >
                      <Marked key={job.id} text={r.bullet.text} keywords={job.keywords} />
                    </motion.li>
                  ))}
                </AnimatePresence>
              </ul>
            </motion.div>
          ))}
        </div>
      ))}
    </div>
  );
}
