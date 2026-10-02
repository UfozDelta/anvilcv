import { useEffect, useRef, useState } from 'react';
import { motion, useInView } from 'framer-motion';
import { JobTabs } from './JobTabs';
import { Marked } from './Marked';
import { ResumePage } from './ResumePage';
import { CAP, JOBS, coveredKeywords, rankBank } from '../../components/landing/storyData';
import { useCountUp, usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

// No bounce: a tap-triggered re-sort carries no momentum to overshoot with.
const SORT = { type: 'spring', duration: 0.5, bounce: 0 } as const;
const PANEL = 'lv-switch-panel';

/**
 * Same bank → different page. Job tabs on top; the job post with its keywords; the
 * whole bank re-ranked for it (left) and the page it produces (right). Opens on
 * BACKEND, the job the story above just built, and switches once to DATA on first
 * view so the re-sort is seen; after any input it only moves when asked.
 */
export function JobSwitch() {
  const reduced = usePrefersReducedMotion();
  const [idx, setIdx] = useState(0);
  const touched = useRef(false);
  const ref = useRef<HTMLDivElement>(null);
  const seen = useInView(ref, { once: true, amount: 0.4 });

  useEffect(() => {
    if (!seen || reduced) return;
    const t = window.setTimeout(() => { if (!touched.current) setIdx(1); }, 1600);
    return () => window.clearTimeout(t);
  }, [seen, reduced]);

  const select = (i: number) => { touched.current = true; setIdx(i); };
  const job = JOBS[idx];
  const rows = rankBank(job);
  const covered = coveredKeywords(job, rows);
  const missing = new Set(job.keywords.map((k) => k.label).filter((l) => !covered.has(l)));
  const capped = rows.find((r) => r.status === 'cap');

  return (
    <div className="lv-switch" ref={ref}>
      <JobTabs jobs={JOBS} idx={idx} onSelect={select} panelId={PANEL} />

      <div id={PANEL} role="tabpanel" aria-labelledby={`lv-tab-${job.id}`} className="lv-switch__panel">
        <div className="lv-job">
          <div className="lv-job__head">
            <span key={job.id} className="lp-display lv-job__role">{job.role}</span>
            <span className="lp-label lp-muted">{job.company} · {job.where}</span>
          </div>
          <p key={`jd-${job.id}`} className="lv-job__jd">
            <Marked text={job.jd} keywords={job.keywords} missing={missing} />
          </p>
          <div className="lv-job__ats">
            <Meter key={job.id} match={job.match} generic={job.generic} />
            <ul className="lv-chips" aria-label="ATS keywords">
              {job.keywords.map((k, i) => (
                <li key={`${job.id}-${k.label}`} className="lv-chip" data-miss={missing.has(k.label) || undefined} style={{ ['--d' as string]: `${i * 40}ms` }}>
                  {k.label}<span>{missing.has(k.label) ? ' : missing' : ' ✓'}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="lv-switch__grid">
          <div className="lv-bank">
            <div className="lv-col-head">
              <span className="lp-label">Your bank · 12 bullets</span>
              <span className="lp-label lp-muted">score</span>
            </div>
            <ul className="lv-bank__list">
              {rows.map((r, i) => (
                <motion.li key={r.bullet.id} layout={reduced ? false : 'position'} transition={SORT} className="lv-brow" data-status={r.status}>
                  <span className="lv-brow__rank">{String(i + 1).padStart(2, '0')}</span>
                  <span className="lv-brow__text">
                    <Marked key={job.id} text={r.bullet.text} keywords={job.keywords} />
                    {r.status === 'cap' && <span className="lv-brow__cap"> Cap {CAP}/{CAP}</span>}
                  </span>
                  <span className="lv-brow__score">
                    <span className="lv-brow__bar" style={{ transform: `scaleX(${r.score / 100})` }} />
                    <span className="lv-brow__num">{r.score}</span>
                  </span>
                </motion.li>
              ))}
            </ul>
            <p className="lv-bank__note">
              Acid rows made the page.
              {capped && <> A {capped.score} stayed in the bank: {CAP} from one role is the limit.</>}
            </p>
          </div>

          <div className="lv-switch__out">
            <div className="lv-col-head">
              <span className="lp-label">The page</span>
              <span className="lp-label lp-muted">1 of 1</span>
            </div>
            <ResumePage job={job} rows={rows} />
          </div>
        </div>
      </div>
    </div>
  );
}

/** ATS match for this page, with a tick where one generic résumé would land. */
function Meter({ match, generic }: { match: number; generic: number }) {
  const reduced = usePrefersReducedMotion();
  const n = useCountUp(match, true, reduced ? 1 : 600);
  return (
    <div className="lv-meter">
      <span className="lv-meter__num">{n}<small>%</small></span>
      <div className="lv-meter__body">
        <span className="lp-label">ATS match</span>
        <span className="lv-meter__track">
          <span className="lv-meter__fill" style={{ ['--to' as string]: match / 100 }} />
          <span className="lv-meter__generic" style={{ left: `${generic}%` }} />
        </span>
        <span className="lv-meter__cap">vs {generic}% for one generic résumé</span>
      </div>
    </div>
  );
}
