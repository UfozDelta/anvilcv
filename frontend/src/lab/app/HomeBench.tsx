import { AnimatePresence, motion } from 'framer-motion';
import { FULL_BANK, REPO_LABEL, edited } from './appData';
import { BulletBar, EmptyBank, PageHead, RowMenu, UndoBar, useProjectRows } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

const EXIT = { duration: 0.2, ease: [0.23, 1, 0.32, 1] } as const;

/**
 * Bench — spotlight: the project you touched last is lit as a hero with its real
 * bullets (the product's output leads); everything else is a quiet list below.
 */
export function HomeBench({ empty }: { empty: boolean }) {
  const { rows, remove, duplicate, removed, more, undo } = useProjectRows(empty);
  const reduced = usePrefersReducedMotion();
  const sorted = [...rows].sort((a, b) => a.editedMinAgo - b.editedMinAgo);
  // The hero shows real output, so it's the latest project that already has bullets.
  const hero = sorted.find((p) => p.bullets > 0) ?? sorted[0];
  const rest = sorted.filter((p) => p !== hero);

  return (
    <div className="shell ap-page">
      <PageHead rows={rows} />
      {!hero ? <EmptyBank /> : (
        <>
          <article className="bn-hero">
            <div className="bn-hero__meta">
              <span className="bn-hero__tag">PICK UP WHERE YOU LEFT OFF</span>
              <span className="ap-muted">edited {edited(hero.editedMinAgo)}</span>
            </div>
            <div className="bn-hero__grid">
              <div>
                <h2 className="lp-display bn-hero__name">{hero.name}</h2>
                <p className="bn-hero__desc">{hero.description}</p>
                <div className="bn-hero__stack">{hero.stack.map((s) => <span key={s} className="lp-tag">{s}</span>)}</div>
                <div className="bn-hero__stat">
                  <span className="lp-display bn-hero__n">{hero.bullets}</span>
                  <span className="ap-muted">bullets · {REPO_LABEL[hero.repo].toLowerCase()}</span>
                </div>
              </div>
              <div className="bn-hero__bullets">
                <span className="ap-label">TOP BULLETS</span>
                {hero.topBullets.length ? (
                  <ul>{hero.topBullets.map((b) => <li key={b}>{b}</li>)}</ul>
                ) : (
                  <p className="ap-muted">Drafting bullets from the repo…</p>
                )}
                <div className="bn-hero__ctas">
                  <a href="#" onClick={(e) => e.preventDefault()} className="ap-btn ap-btn--acid">Open bank →</a>
                  <a href="#" onClick={(e) => e.preventDefault()} className="ap-btn ap-btn--ghost-dark">Tailor a job with it</a>
                </div>
              </div>
            </div>
          </article>

          {rest.length > 0 && (
            <section className="bn-rest">
              <h3 className="ap-label">ALL PROJECTS</h3>
              <AnimatePresence initial={false}>
                {rest.map((p) => (
                  <motion.a
                    key={p.id}
                    href="#"
                    onClick={(e) => e.preventDefault()}
                    className="bn-row"
                    layout={reduced ? false : 'position'}
                    initial={reduced ? false : { opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0, transition: EXIT }}
                    transition={EXIT}
                  >
                    <strong className="lp-display bn-row__name">{p.name}</strong>
                    <span className="bn-row__bank">
                      <BulletBar n={p.bullets} max={FULL_BANK} />
                      <span>{p.bullets}</span>
                    </span>
                    <span className="ap-muted bn-row__edited">{edited(p.editedMinAgo)}</span>
                    <RowMenu label={p.name} onDelete={() => remove(p.id)} onDuplicate={() => duplicate(p.id)} />
                  </motion.a>
                ))}
              </AnimatePresence>
            </section>
          )}
        </>
      )}
      <UndoBar name={removed?.name ?? null} more={more} onUndo={undo} />
    </div>
  );
}
