import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { JOBS, edited, type DemoJob } from './appData';
import { EXIT, PageTitle, inert, useDemoPage } from './shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

const SAVED = 'saved';
const TABS = [
  { key: '', label: 'All' },
  { key: 'linkedin', label: 'LinkedIn' },
  { key: 'indeed', label: 'Indeed' },
  { key: SAVED, label: 'Saved' },
];

function JobsLedger({ empty }: { empty: boolean }) {
  const [jobs, setJobs] = useState<DemoJob[]>(empty ? [] : JOBS);
  const reduced = usePrefersReducedMotion();
  const [q, setQ] = useState('');
  const [place, setPlace] = useState('');
  const [tab, setTab] = useState('');

  const counts: Record<string, number> = useMemo(() => ({
    '': jobs.length,
    linkedin: jobs.filter((j) => j.source === 'linkedin').length,
    indeed: jobs.filter((j) => j.source === 'indeed').length,
    [SAVED]: jobs.filter((j) => j.saved).length,
  }), [jobs]);

  const shown = useMemo(() => {
    const n = q.trim().toLowerCase();
    const p = place.trim().toLowerCase();
    return jobs
      .filter((j) => (tab === SAVED ? j.saved : !tab || j.source === tab))
      .filter((j) => !n || `${j.title} ${j.company} ${j.desc}`.toLowerCase().includes(n))
      .filter((j) => !p || j.location.toLowerCase().includes(p))
      .sort((a, b) => a.postedMinAgo - b.postedMinAgo);
  }, [jobs, q, place, tab]);

  const toggleSave = (id: string) => setJobs((js) => js.map((x) => (x.id === id ? { ...x, saved: !x.saved } : x)));

  return (
    <div className="shell ap-page">
      <PageTitle title="Jobs" count={jobs.length > 0 && <><strong>{jobs.length}</strong> intern postings · newest first</>} />

      {jobs.length === 0 ? (
        <section className="ap-empty">
          <h2 className="lp-display ap-empty__title">No postings yet.</h2>
          <p className="ap-empty__sub">New roles arrive here as they are posted.</p>
        </section>
      ) : (
        <>
          <div className="la-tabs" role="group" aria-label="Filter by source">
            {TABS.map((t) => (
              <button key={t.key || 'all'} type="button" aria-pressed={tab === t.key} onClick={() => setTab(t.key)}>
                {t.label} <span className="la-tabs__n">{counts[t.key]}</span>
              </button>
            ))}
          </div>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Title or company" aria-label="Search jobs" value={q} onChange={(e) => setQ(e.target.value)} />
            <input className="ap-search" placeholder="Location" aria-label="Location" value={place} onChange={(e) => setPlace(e.target.value)} />
          </div>

          <ul className="ld-table jb-list" aria-label="Jobs">
            <AnimatePresence initial={false}>
              {shown.map((j) => (
                <motion.li
                  key={j.id}
                  className="ld-row jb-row"
                  layout={reduced ? false : 'position'}
                  initial={reduced ? false : { opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, transition: EXIT }}
                  transition={EXIT}
                >
                  <div className="ld-name">
                    <strong className="jb-company">{j.company}</strong>
                    <span className="jb-title">{j.title}</span>
                    <span className="jb-desc">{j.desc}</span>
                    <span className="ld-desc">{j.location} · {edited(j.postedMinAgo)}</span>
                  </div>
                  <div className="jb-actions">
                    <button
                      type="button"
                      className="jb-save"
                      aria-pressed={j.saved}
                      aria-label={`${j.saved ? 'Unsave' : 'Save'} ${j.title} at ${j.company}`}
                      onClick={() => toggleSave(j.id)}
                    >{j.saved ? '★' : '☆'}</button>
                    <a href="#" onClick={inert} className="ap-btn ap-btn--ghost jb-btn" aria-label={`View ${j.title} at ${j.company} posting`}>View post ↗</a>
                    <a href="#" onClick={inert} className="ap-btn ap-btn--acid jb-btn" aria-label={`Tailor a résumé for ${j.title} at ${j.company}`}>Tailor →</a>
                  </div>
                </motion.li>
              ))}
            </AnimatePresence>
            {shown.length === 0 && (
              <li className="ap-nomatch">
                {tab === SAVED && !q && !place ? 'Nothing saved yet. Tap ☆ on a posting.' : 'No jobs match.'}{' '}
                {(tab || q || place) && <button type="button" onClick={() => { setQ(''); setPlace(''); setTab(''); }}>Clear</button>}
              </li>
            )}
          </ul>
        </>
      )}
    </div>
  );
}

/** /lab/jobs — Jobs feed in the Ledger style, under the Strip nav. E toggles the empty state, R replays. */
export function LabJobs() {
  const { empty, mountKey, replay } = useDemoPage();
  return (
    <div className="ap-root">
      <NavStrip />
      <JobsLedger key={`${mountKey}-${empty}`} empty={empty} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
