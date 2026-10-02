import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type JobPosting, type JobList, type JobCounts } from '../lib/api';
import { useAuth } from '../lib/auth';
import { EmptyState, PageTitle, ago } from '../components/ledger/shared';

const PAGE_SIZE = 50;
/** New postings arrive by webhook; the list picks them up without a reload. */
const POLL_MS = 60_000;

const SOURCES = [
  { key: '', label: 'All' },
  { key: 'linkedin', label: 'LinkedIn' },
  { key: 'indeed', label: 'Indeed' },
];
/** Not a source: a filter value meaning "my saved postings". Signed-in only. */
const SAVED = 'saved';

function query(source: string, q: string, location: string, page: number) {
  const p = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (source === SAVED) p.set('saved', 'true');
  else if (source) p.set('source', source);
  if (q.trim()) p.set('q', q.trim());
  if (location.trim()) p.set('location', location.trim());
  return `/api/public/jobs?${p}`;
}

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
  const [loading, setLoading] = useState(true);
  const [counts, setCounts] = useState<JobCounts | null>(null);
  const [err, setErr] = useState<string | null>(null);
  // Bumped by every load(); a poll or load-more that started under an older value is stale and ignored.
  const gen = useRef(0);

  const load = useCallback(async () => {
    const mine = ++gen.current;
    setLoading(true);
    setErr(null);
    try {
      const r = await api.get<JobList>(query(source, q, location, 0));
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
  }, [source, q, location]);

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
        const r = await api.get<JobList>(query(source, q, location, 0));
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
  }, [source, q, location]);

  async function loadMore() {
    const mine = gen.current;
    try {
      const r = await api.get<JobList>(query(source, q, location, page + 1));
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

  /** Tab counts follow the q/location filters; hidden until the API has sent them. */
  function tabCount(key: string): number | null {
    if (!counts) return null;
    if (key === '') return counts.linkedin + counts.indeed;
    if (key === SAVED) return counts.saved;
    return counts[key as 'linkedin' | 'indeed'] ?? null;
  }

  const none = !loading && jobs.length === 0;
  const filtered = !!(source || q.trim() || location.trim());

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
      </div>

      {err && <div className="err ap-err" role="alert">{err}</div>}

      {loading && jobs.length === 0 && <span className="spinner">LOADING</span>}

      {none && (filtered
        ? (
          <div className="ap-nomatch">
            {source === SAVED && !q.trim() && !location.trim() ? 'Nothing saved yet. Tap ☆ on a posting.' : 'No jobs match.'}{' '}
            <button type="button" onClick={() => { setQ(''); setLocation(''); setSource(''); }}>Clear</button>
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
