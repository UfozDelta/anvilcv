import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type JobPosting, type JobList, type JobCounts } from '../lib/api';
import { useAuth } from '../lib/auth';
import { EmptyState, PageTitle, ago } from '../components/ledger/shared';
import { jobsQuery, SAVED, toggleTag, type JobFilters } from '../lib/jobQuery';

const PAGE_SIZE = 50;
/** New postings arrive by webhook; the list picks them up without a reload. */
const POLL_MS = 60_000;

const SOURCES = [
  { key: '', label: 'All' },
  { key: 'linkedin', label: 'LinkedIn' },
  { key: 'indeed', label: 'Indeed' },
];
/** Filters on when the posting reached us; `posted` is free text from the source. */
const ADDED = [
  { key: '', label: 'Added: any time' },
  { key: '1', label: 'Added: 24h' },
  { key: '7', label: 'Added: 7 days' },
  { key: '30', label: 'Added: 30 days' },
];
/** Tag chips shown before "more"; the rest of the top tags stay one tap away. */
const TAGS_SHOWN = 12;

const query = (source: string, f: JobFilters, page: number) => jobsQuery(source, f, page, PAGE_SIZE);

/** Public: anyone can browse. Tailoring a posting needs an account. */
export function Jobs() {
  const { username } = useAuth();
  const nav = useNavigate();
  const [jobs, setJobs] = useState<JobPosting[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [source, setSource] = useState('');
  const [q, setQ] = useState('');
  const [location, setLocation] = useState('');
  const [remote, setRemote] = useState(false);
  const [days, setDays] = useState('');
  const [stack, setStack] = useState<string[]>([]);
  const [mine, setMine] = useState(false);
  const [tags, setTags] = useState<string[]>([]);
  const [allTags, setAllTags] = useState(false);
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState<JobCounts | null>(null);
  const [err, setErr] = useState<string | null>(null);
  // Bumped by every load(); a poll or load-more that started under an older value is stale and ignored.
  const gen = useRef(0);
  const filters: JobFilters = { q, location, remote, days, stack, mine };

  // The picker is a nicety: with no tags (or no answer) it just stays hidden.
  useEffect(() => {
    api.get<string[]>('/api/public/jobs/tags').then(setTags).catch(() => {});
  }, []);

  const load = useCallback(async () => {
    const mine = ++gen.current;
    setLoading(true);
    setErr(null);
    try {
      const r = await api.get<JobList>(query(source, filters, 0));
      if (mine !== gen.current) return;
      setJobs(r.jobs);
      setTotal(r.total);
      setCounts(r.counts ?? null);
      setPage(0);
    } catch (e) {
      if (mine === gen.current) setErr(`Could not load jobs: ${(e as Error).message}`);
    } finally {
      if (mine === gen.current) setLoading(false);
    }
  }, [source, q, location, remote, days, stack, mine]);

  // Debounced so typing in the search box doesn't fire a request per keystroke.
  useEffect(() => {
    const t = setTimeout(() => { void load(); }, 300);
    return () => clearTimeout(t);
  }, [load]);

  // Poll the first page and put anything unseen on top; loaded pages below stay put.
  useEffect(() => {
    const t = setInterval(async () => {
      const mine = gen.current;
      try {
        const r = await api.get<JobList>(query(source, filters, 0));
        if (mine !== gen.current) return;
        setJobs(js => {
          const seen = new Set(js.map(j => j.id));
          const fresh = r.jobs.filter(j => !seen.has(j.id));
          return fresh.length ? [...fresh, ...js] : js;
        });
        setTotal(r.total);
        setCounts(r.counts ?? null);
      } catch { /* next tick retries */ }
    }, POLL_MS);
    return () => clearInterval(t);
  }, [source, q, location, remote, days, stack, mine]);

  async function loadMore() {
    const mine = gen.current;
    try {
      const r = await api.get<JobList>(query(source, filters, page + 1));
      if (mine !== gen.current) return;
      setJobs(js => {
        const seen = new Set(js.map(j => j.id));
        return [...js, ...r.jobs.filter(j => !seen.has(j.id))];
      });
      setTotal(r.total);
      setPage(page + 1);
    } catch (e) {
      if (mine === gen.current) setErr(`Could not load more: ${(e as Error).message}`);
    }
  }

  function tailor(j: JobPosting) {
    const target = `/new?jdUrl=${encodeURIComponent(j.url)}`;
    if (username) nav(target);
    else nav('/login', { state: { from: target } });
  }

  async function toggleSave(j: JobPosting) {
    if (!username) { nav('/login', { state: { from: '/jobs' } }); return; }
    const saved = !j.saved;
    const set = (v: boolean) => setJobs(js => js.map(x => (x.id === j.id ? { ...x, saved: v } : x)));
    set(saved);
    try {
      if (saved) await api.put(`/api/jobs/${j.id}/save`);
      else await api.del(`/api/jobs/${j.id}/save`);
    } catch (e) {
      set(!saved);
      setErr(`Could not ${saved ? 'save' : 'unsave'}: ${(e as Error).message}`);
    }
  }

  /** Tab counts follow the search filters; hidden until the API has sent them. */
  function tabCount(key: string): number | null {
    if (!counts) return null;
    if (key === '') return counts.linkedin + counts.indeed;
    if (key === SAVED) return counts.saved;
    return counts[key as 'linkedin' | 'indeed'] ?? null;
  }

  const none = !loading && jobs.length === 0;
  const filtered = !!(source || q.trim() || location.trim() || remote || days || stack.length || mine);
  const shownTags = allTags ? tags : tags.slice(0, TAGS_SHOWN);

  return (
    <div className="shell ap-page">
      <PageTitle title="Jobs" count={total > 0 && <><strong>{total}</strong> intern postings · newest first</>} />

      <div className="la-tabs" role="group" aria-label="Filter by source">
        {SOURCES.map(s => (
          <button key={s.key || 'all'} type="button" aria-pressed={source === s.key} onClick={() => setSource(s.key)}>
            {s.label}{tabCount(s.key) !== null && <> <span className="la-tabs__n">{tabCount(s.key)}</span></>}
          </button>
        ))}
        {username && (
          <button type="button" aria-pressed={source === SAVED} onClick={() => setSource(SAVED)}>
            Saved{tabCount(SAVED) !== null && <> <span className="la-tabs__n">{tabCount(SAVED)}</span></>}
          </button>
        )}
      </div>
      <div className="ap-tools">
        <input className="ap-search" placeholder="Title or company" aria-label="Search jobs" value={q} onChange={e => setQ(e.target.value)} />
        <input className="ap-search" placeholder="Location" aria-label="Location" value={location} onChange={e => setLocation(e.target.value)} />
        <div className="la-tabs" role="group" aria-label="Remote">
          <button type="button" aria-pressed={remote} title="With a location: that place or remote" onClick={() => setRemote(r => !r)}>+ Remote</button>
        </div>
        <select className="la-sort" value={days} aria-label="Added within" onChange={e => setDays(e.target.value)}>
          {ADDED.map(a => <option key={a.key || 'any'} value={a.key}>{a.label}</option>)}
        </select>
        {username && (
          <div className="la-tabs" role="group" aria-label="Skills">
            <button type="button" aria-pressed={mine} title="Postings whose stack overlaps your profile skills" onClick={() => setMine(m => !m)}>My skills</button>
          </div>
        )}
      </div>
      {tags.length > 0 && (
        <div className="jb-tags" role="group" aria-label="Filter by tech (any)">
          {shownTags.map(t => (
            <button key={t} type="button" className="jb-chip" aria-pressed={stack.includes(t)} onClick={() => setStack(s => toggleTag(s, t))}>{t}</button>
          ))}
          {/* A picked tag stays visible even when the list is collapsed. */}
          {!allTags && stack.filter(t => !shownTags.includes(t)).map(t => (
            <button key={t} type="button" className="jb-chip" aria-pressed onClick={() => setStack(s => toggleTag(s, t))}>{t}</button>
          ))}
          {tags.length > TAGS_SHOWN && (
            <button type="button" className="jb-tags__more" onClick={() => setAllTags(a => !a)}>{allTags ? 'Fewer' : `+${tags.length - TAGS_SHOWN} more`}</button>
          )}
        </div>
      )}

      {err && <div className="err ap-err" role="alert">{err}</div>}

      {loading && jobs.length === 0 && <span className="spinner">LOADING</span>}

      {none && (filtered
        ? (
          <div className="ap-nomatch">
            {source === SAVED && !q.trim() && !location.trim() && !remote && !days && !stack.length && !mine
              ? 'Nothing saved yet. Tap ☆ on a posting.'
              : mine ? 'No jobs match. Skills come from your profile.' : 'No jobs match.'}{' '}
            <button type="button" onClick={() => { setQ(''); setLocation(''); setRemote(false); setDays(''); setStack([]); setMine(false); setSource(''); }}>Clear</button>
          </div>
        )
        : <EmptyState title="No postings yet." sub="New roles arrive here as they are posted." />)}

      {jobs.length > 0 && (
        <ul className="ld-table jb-list" aria-label="Jobs">
          {jobs.map(j => {
            const name = `${j.title || 'Untitled role'} at ${j.company || 'unknown company'}`;
            return (
              <li key={j.id} className="ld-row jb-row">
                <div className="ld-name">
                  <strong className="jb-company">{j.company || 'Unknown company'}</strong>
                  <span className="jb-title">{j.title || 'Untitled role'}</span>
                  {j.role && <span className="jb-desc">{j.role}</span>}
                  <span className="ld-desc">{[j.location, j.posted || ago(j.receivedAt)].filter(Boolean).join(' · ')}</span>
                  {j.stack.length > 0 && (
                    <ul className="jb-stack" aria-label="Tech stack">
                      {j.stack.map(t => {
                        const hit = j.matched?.includes(t);
                        return <li key={t} className="jb-chip" data-match={hit || undefined} title={hit ? 'On your profile' : undefined}>{t}</li>;
                      })}
                    </ul>
                  )}
                </div>
                <div className="jb-actions">
                  <button
                    type="button"
                    className="jb-save"
                    aria-pressed={j.saved}
                    aria-label={`${j.saved ? 'Unsave' : 'Save'} ${name}`}
                    onClick={() => toggleSave(j)}
                  >{j.saved ? '★' : '☆'}</button>
                  <a href={j.url} target="_blank" rel="noopener noreferrer" className="ap-btn ap-btn--ghost jb-btn" aria-label={`View ${name} posting`}>View post ↗</a>
                  <button type="button" className="ap-btn ap-btn--acid jb-btn" aria-label={`Tailor a résumé for ${name}`} onClick={() => tailor(j)}>Tailor →</button>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {jobs.length < total && (
        <div className="jb-more">
          <button type="button" className="ap-btn ap-btn--ghost" onClick={loadMore}>Load more</button>
        </div>
      )}
    </div>
  );
}
