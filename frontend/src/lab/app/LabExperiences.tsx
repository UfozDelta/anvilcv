import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { EXPERIENCES, FULL_BANK, REPO_LABEL, datesLabel, endKey, tenure, type DemoExperience } from './appData';
import { BulletBar, EXIT, PageTitle, RowMenu, UndoBar, inert, useDemoPage, useUndoRows } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

type Sort = 'recent' | 'name';
const SORTS: { key: Sort; label: string }[] = [
  { key: 'recent', label: 'Most recent first' },
  { key: 'name', label: 'A → Z' },
];

/** First run: two ways in, nothing else. */
function EmptyExperiences() {
  return (
    <section className="ap-empty">
      <h2 className="lp-display ap-empty__title">No experience yet.</h2>
      <p className="ap-empty__sub">Jobs, internships, research. Tailored résumés pull their work-history bullets from here.</p>
      <div className="ap-empty__paths">
        <button type="button" className="ap-path ap-path--primary">
          <span className="ap-path__num">01</span>
          <strong>Import a GitHub repo</strong>
          <span>We read the code and draft bullets for you.</span>
        </button>
        <button type="button" className="ap-path">
          <span className="ap-path__num">02</span>
          <strong>Add one by hand</strong>
          <span>Title, company, dates. Bullets come after.</span>
        </button>
      </div>
    </section>
  );
}

function ExperiencesLedger({ empty }: { empty: boolean }) {
  const { rows, remove, removed, more, undo } = useUndoRows(empty ? [] : EXPERIENCES);
  const reduced = usePrefersReducedMotion();
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('recent');

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const by: Record<Sort, (a: DemoExperience, b: DemoExperience) => number> = {
      recent: (a, b) => endKey(b) - endKey(a),
      name: (a, b) => a.title.localeCompare(b.title),
    };
    return rows.filter((r) => (r.title + ' ' + r.company).toLowerCase().includes(needle)).sort(by[sort]);
  }, [rows, q, sort]);

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Experiences"
        count={rows.length > 0 && <><strong>{rows.length}</strong> role{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + r.bullets, 0)}</strong> bullets in your bank</>}
        actions={rows.length > 0 && (
          <>
            <button type="button" className="ap-btn ap-btn--ghost">Import from GitHub</button>
            <button type="button" className="ap-btn ap-btn--acid">+ New experience</button>
          </>
        )}
      />
      {rows.length === 0 ? <EmptyExperiences /> : (
        <>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search role or company" aria-label="Search experiences" value={q} onChange={(e) => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort experiences" onChange={(e) => setSort(e.target.value as Sort)}>
              {SORTS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </div>
          <div className="ld-table" role="table" aria-label="Experiences">
            <div className="ld-row ex-row ex-row--head ld-row--head" role="row">
              <span role="columnheader">Role</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Repo</span>
              <span role="columnheader" className="ld-hide-sm">Dates</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map((x) => (
                <motion.div
                  key={x.id}
                  className="ld-row ex-row"
                  role="row"
                  layout={reduced ? false : 'position'}
                  initial={reduced ? false : { opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, transition: EXIT }}
                  transition={EXIT}
                >
                  <span className="ld-name" role="cell">
                    <strong>
                      <a href="#" onClick={inert} className="ld-link">{x.title}</a>
                      {!x.end && <span className="ex-now">NOW</span>}
                    </strong>
                    <span className="ld-desc"><span className="ex-inline">{datesLabel(x)} · </span>{x.company} · {x.location}</span>
                  </span>
                  <span className="ld-bullets" role="cell">
                    <span className={`ld-bullets__n${x.bullets === 0 ? ' ex-zero' : ''}`}>{x.bullets}</span>
                    <BulletBar n={x.bullets} max={FULL_BANK} />
                  </span>
                  <span role="cell" className="ld-hide-sm ld-repo" data-state={x.repo}>{REPO_LABEL[x.repo]}</span>
                  <span role="cell" className="ld-hide-sm ex-dates">
                    <strong>{datesLabel(x)}</strong>
                    <span>{tenure(x)}</span>
                  </span>
                  <RowMenu label={x.title} onDelete={() => remove(x.id)} />
                </motion.div>
              ))}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>
            )}
          </div>
        </>
      )}
      <UndoBar name={removed?.title ?? null} more={more} onUndo={undo} />
    </div>
  );
}

/** /lab/experiences — Experiences home in the Ledger style, under the Strip nav. */
export function LabExperiences() {
  const { empty, mountKey, replay } = useDemoPage();
  return (
    <div className="ap-root">
      <NavStrip />
      <ExperiencesLedger key={`${mountKey}-${empty}`} empty={empty} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
