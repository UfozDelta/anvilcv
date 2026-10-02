import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { FULL_BANK, REPO_LABEL, edited } from './appData';
import { BulletBar, EXIT, EmptyBank, PageHead, RowMenu, UndoBar, inert, useProjectRows } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

/** Ledger — density: an editorial table where every row shows how full its bank is. */
export function HomeLedger({ empty }: { empty: boolean }) {
  const { rows, remove, duplicate, removed, more, undo } = useProjectRows(empty);
  const reduced = usePrefersReducedMotion();
  const [q, setQ] = useState('');
  const shown = useMemo(
    () => [...rows].filter((r) => r.name.toLowerCase().includes(q.trim().toLowerCase())).sort((a, b) => a.editedMinAgo - b.editedMinAgo),
    [rows, q],
  );

  return (
    <div className="shell ap-page">
      <PageHead rows={rows} />
      {rows.length === 0 ? <EmptyBank /> : (
        <>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search projects" aria-label="Search projects" value={q} onChange={(e) => setQ(e.target.value)} />
            <span className="ap-tools__hint">Most recently edited first</span>
          </div>
          <div className="ld-table" role="table" aria-label="Projects">
            <div className="ld-row ld-row--head" role="row">
              <span role="columnheader">Project</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Repo</span>
              <span role="columnheader" className="ld-hide-sm">Edited</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map((p) => (
                <motion.div
                  key={p.id}
                  className="ld-row"
                  role="row"
                  layout={reduced ? false : 'position'}
                  initial={reduced ? false : { opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, transition: EXIT }}
                  transition={EXIT}
                >
                  <span className="ld-name" role="cell">
                    <strong><a href="#" onClick={inert} className="ld-link">{p.name}</a></strong>
                    <span className="ld-desc">{p.description}</span>
                  </span>
                  <span className="ld-bullets" role="cell">
                    <span className="ld-bullets__n">{p.bullets}</span>
                    <BulletBar n={p.bullets} max={FULL_BANK} />
                  </span>
                  <span role="cell" className="ld-hide-sm ld-repo" data-state={p.repo}>{REPO_LABEL[p.repo]}</span>
                  <span role="cell" className="ld-hide-sm ld-edited">{edited(p.editedMinAgo)}</span>
                  <RowMenu label={p.name} onDelete={() => remove(p.id)} onDuplicate={() => duplicate(p.id)} />
                </motion.div>
              ))}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>
            )}
          </div>
        </>
      )}
      <UndoBar name={removed?.name ?? null} more={more} onUndo={undo} />
    </div>
  );
}
