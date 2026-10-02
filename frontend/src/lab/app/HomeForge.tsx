import { AnimatePresence, motion } from 'framer-motion';
import { FULL_BANK, READY_AT, REPO_LABEL, edited, readiness, type Readiness } from './appData';
import { BulletBar, EmptyBank, PageHead, RowMenu, UndoBar, useProjectRows } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

const EXIT = { duration: 0.2, ease: [0.23, 1, 0.32, 1] } as const;

const GROUPS: { key: Readiness; title: string; hint: string }[] = [
  { key: 'needs', title: 'Needs bullets', hint: `Under ${READY_AT} bullets: thin for tailoring` },
  { key: 'exploring', title: 'Reading repo', hint: 'Bullets land when the read finishes' },
  { key: 'ready', title: 'Ready', hint: 'Enough to tailor any job' },
];

/** Forge — readiness model: projects grouped by what they need next, with one call to action. */
export function HomeForge({ empty }: { empty: boolean }) {
  const { rows, remove, duplicate, removed, more, undo } = useProjectRows(empty);
  const reduced = usePrefersReducedMotion();
  const needs = rows.filter((r) => readiness(r) === 'needs').length;

  return (
    <div className="shell ap-page">
      <PageHead rows={rows} />
      {rows.length === 0 ? <EmptyBank /> : (
        <>
          {needs > 0 && (
            <div className="fg-callout">
              <span className="lp-display fg-callout__n">{needs}</span>
              <p><strong>project{needs === 1 ? '' : 's'} need{needs === 1 ? 's' : ''} bullets.</strong> Thin banks make thin résumés.</p>
              <button type="button" className="ap-btn ap-btn--ink">Draft bullets for them →</button>
            </div>
          )}
          <div className="fg-groups">
            {GROUPS.map((g) => {
              const items = rows.filter((r) => readiness(r) === g.key).sort((a, b) => a.editedMinAgo - b.editedMinAgo);
              return (
                <section key={g.key} className="fg-group" data-kind={g.key}>
                  <header className="fg-group__head">
                    <h2 className="ap-label">{g.title} <span className="fg-group__count">{items.length}</span></h2>
                    <span className="ap-muted fg-group__hint">{g.hint}</span>
                  </header>
                  <AnimatePresence initial={false}>
                    {items.map((p) => (
                      <motion.a
                        key={p.id}
                        href="#"
                        onClick={(e) => e.preventDefault()}
                        className="fg-card"
                        layout={reduced ? false : 'position'}
                        initial={reduced ? false : { opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0, transition: EXIT }}
                        transition={EXIT}
                      >
                        <div className="fg-card__top">
                          <strong className="lp-display fg-card__name">{p.name}</strong>
                          <RowMenu label={p.name} onDelete={() => remove(p.id)} onDuplicate={() => duplicate(p.id)} />
                        </div>
                        <span className="fg-card__desc">{p.description}</span>
                        <span className="fg-card__bank">
                          <BulletBar n={p.bullets} max={FULL_BANK} />
                          <span>{p.bullets} bullets</span>
                        </span>
                        <span className="ap-muted fg-card__meta">{REPO_LABEL[p.repo]} · {edited(p.editedMinAgo)}</span>
                      </motion.a>
                    ))}
                  </AnimatePresence>
                  {items.length === 0 && <p className="ap-muted fg-group__empty">Nothing here.</p>}
                </section>
              );
            })}
          </div>
        </>
      )}
      <UndoBar name={removed?.name ?? null} more={more} onUndo={undo} />
    </div>
  );
}
