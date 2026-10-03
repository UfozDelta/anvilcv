import { useAvgSec } from './useAvgSec';
import { LayoutGroup, motion } from 'framer-motion';
import { BANK, ENTRIES, JOBS, coveredKeywords, rankBank, segments } from './storyData';
import { usePrefersReducedMotion } from './useHeroLoop';

const JOB = JOBS[0];
const ROWS = rankBank(JOB);
const PICKED = ROWS.filter((r) => r.status === 'page');
const LEFT = ROWS.filter((r) => r.status !== 'page');
// The finished page groups picked bullets under their role/project, like the real PDF.
const PAGE_ENTRIES = ENTRIES
  .map((e) => ({ entry: e, rows: PICKED.filter((r) => r.bullet.entry === e.id) }))
  .filter((g) => g.rows.length > 0);
const COVERED = [...coveredKeywords(JOB, ROWS)];
// Varied line lengths so the page reads as text, not a bar chart.
const WIDTHS = ['78%', '64%', '86%', '58%', '72%', '68%'];
const HITS = new Set(BANK.filter((b) => segments(b.text, JOB.keywords).some((s) => s.kw)).map((b) => b.id));

/** Two-to-three word handles, so the stage reads at a glance instead of as paragraphs. */
const LABEL: Record<string, string> = {
  t1: 'p99 −41%', t2: 'gRPC split', t3: 'Kafka → BQ', t4: 'Terraform',
  b1: 'React DS', b2: 'Churn model', b3: 'dbt warehouse', b4: 'WCAG intake',
  q1: 'Raft KV', q2: 'PG exporter', s1: 'Offline PWA', s2: 'Playwright',
};
const STATUS = ['', '12 BULLETS', `${JOB.keywords.length} KEYWORDS`, `${PICKED.length} PICKED`, 'PDF READY'];
// No bounce: scroll-driven state changes carry no momentum to overshoot with.
const MOVE = { type: 'spring', duration: 0.55, bounce: 0 } as const;

/**
 * Text-light stage for the Spotlight "How it works". One persistent layout so every
 * step reads as the same object changing: bank tiles → keywords light matching tiles →
 * tiles morph into a ranked list (shared layoutId) → the page slides up over it.
 * The region the active step is about stays lit; the step number sits in the top bar.
 */
export function SpotStage({ step }: { step: number }) {
  const sec = useAvgSec();
  const reduced = usePrefersReducedMotion();
  const ranked = step >= 3;

  const tile = (id: string, extra?: React.ReactNode, cls = '') => (
    <motion.div
      key={id}
      layoutId={reduced ? undefined : `ss-${id}`}
      transition={MOVE}
      className={`ss-tile ${cls}`}
      data-hit={step === 2 && HITS.has(id) ? '' : undefined}
    >
      <span className="ss-tile__label">{LABEL[id]}</span>
      {extra}
    </motion.div>
  );

  return (
    <div className="ss" data-step={step} aria-hidden="true">
      <div className="ss-bar">
        <span>{step === 4 ? 'jordan-reyes.pdf' : 'your-bank'}</span>
        {/* The step number lives here, in one fixed spot, instead of tags over the content. */}
        <span key={step} className="ss-bar__status">0{step} · {STATUS[step]}</span>
      </div>

      <div className="ss-job ss-zone" data-zone="2">
        {step < 2 ? (
          <span className="ss-job__empty">No job yet</span>
        ) : (
          <>
            <div className="ss-job__head">
              <strong>{JOB.company}</strong> <span>{JOB.role}</span>
            </div>
            {/* Once ranking starts the keywords have done their job; folding them frees room. */}
            {step === 2 && (
              <div className="ss-job__chips">
                {JOB.keywords.map((k, i) => (
                  <span key={k.label} className="ss-chip" style={{ animationDelay: `${i * 40}ms` }}>{k.label}</span>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      <LayoutGroup>
        <div className="ss-bank ss-zone" data-zone={ranked ? '3' : '1'}>
          {ranked ? (
            <>
              <div className="ss-list">
                {PICKED.map((r, i) => tile(
                  r.bullet.id,
                  <>
                    <span className="ss-tile__bar" style={{ transform: `scaleX(${r.score / 100})` }} />
                    <span className="ss-tile__num">{r.score}</span>
                  </>,
                  `ss-tile--row ss-r${i}`,
                ))}
              </div>
              <span className="ss-left__label">Stayed in the bank</span>
              <div className="ss-left">
                {LEFT.map((r) => tile(r.bullet.id, r.status === 'cap' ? <span className="ss-tile__cap">cap</span> : null, 'ss-tile--dim'))}
              </div>
            </>
          ) : (
            <div className="ss-grid">{BANK.map((b) => tile(b.id, step === 2 && HITS.has(b.id) ? <span className="ss-tile__dot" /> : null))}</div>
          )}
        </div>
      </LayoutGroup>

      <div className="ss-page ss-zone" data-zone="4" data-on={step === 4 || undefined}>
        <div className="ss-doc">
          <header className="ss-doc__head">
            <div className="ss-doc__name">Jordan Reyes</div>
            <div className="ss-doc__role">{JOB.role}</div>
            <div className="ss-doc__contact"><span /><span /><span /></div>
          </header>
          {(['EXPERIENCE', 'PROJECTS'] as const).map((kind) => {
            const groups = PAGE_ENTRIES.filter((g) => g.entry.kind === kind);
            if (!groups.length) return null;
            return (
              <section key={kind} className="ss-doc__sec">
                <div className="ss-doc__sec-title">{kind}</div>
                {groups.map(({ entry, rows }) => (
                  <div key={entry.id} className="ss-doc__entry">
                    <div className="ss-doc__org">
                      <strong>{entry.org}</strong>
                      {entry.when && <span>{entry.when}</span>}
                    </div>
                    {rows.map((r) => (
                      <div key={r.bullet.id} className="ss-doc__line">
                        <span className="ss-doc__dot" />
                        <strong>{LABEL[r.bullet.id]}</strong>
                        <span className="ss-doc__fill" style={{ width: WIDTHS[PICKED.indexOf(r) % WIDTHS.length] }} />
                      </div>
                    ))}
                  </div>
                ))}
              </section>
            );
          })}
          <section className="ss-doc__sec ss-doc__sec--skills">
            <div className="ss-doc__sec-title">SKILLS</div>
            <div className="ss-doc__skills">{COVERED.join(' · ')}</div>
          </section>
          <footer className="ss-doc__foot">
            <span>1 PAGE</span><span>~{sec}s</span><span>+ COVER LETTER</span>
          </footer>
        </div>
      </div>
    </div>
  );
}
