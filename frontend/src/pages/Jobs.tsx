import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type JobPosting, type JobList } from '../lib/api';
import { useAuth } from '../lib/auth';
import { Section } from '../components/Section';

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
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const r = await api.get<JobList>(query(source, q, location, 0));
      setJobs(r.jobs);
      setTotal(r.total);
      setPage(0);
    } catch (e) {
      setErr(`Could not load jobs: ${(e as Error).message}`);
    } finally {
      setLoading(false);
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
      try {
        const r = await api.get<JobList>(query(source, q, location, 0));
        setJobs(js => {
          const seen = new Set(js.map(j => j.id));
          const fresh = r.jobs.filter(j => !seen.has(j.id));
          return fresh.length ? [...fresh, ...js] : js;
        });
        setTotal(r.total);
      } catch { /* next tick retries */ }
    }, POLL_MS);
    return () => clearInterval(t);
  }, [source, q, location]);

  async function loadMore() {
    try {
      const r = await api.get<JobList>(query(source, q, location, page + 1));
      setJobs(js => {
        const seen = new Set(js.map(j => j.id));
        return [...js, ...r.jobs.filter(j => !seen.has(j.id))];
      });
      setTotal(r.total);
      setPage(page + 1);
    } catch (e) {
      setErr(`Could not load more: ${(e as Error).message}`);
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

  return (
    <div className="shell">
      <Section num="00" title="Intern jobs" count={total} />

      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="Search title, company or role…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />
        <input
          className="toolbar__search"
          placeholder="Location…"
          value={location}
          onChange={e => setLocation(e.target.value)}
        />
        <div className="filterset">
          {SOURCES.map(s => (
            <button key={s.key} className={source === s.key ? 'is-on' : ''} onClick={() => setSource(s.key)}>
              {s.label}
            </button>
          ))}
          {username && (
            <button className={source === SAVED ? 'is-on' : ''} onClick={() => setSource(SAVED)}>Saved</button>
          )}
        </div>
      </div>

      {!username && (
        <div className="legend" style={{ marginBottom: 16 }}>
          Browse freely. <b>Tailor</b> builds a résumé for the posting — you&rsquo;ll sign in or create an account first.
        </div>
      )}

      {err && <div className="err" style={{ marginBottom: 12 }}>{err}</div>}

      <div className="joblist">
        {!loading && jobs.length === 0 && <div className="muted" style={{ padding: '18px 0' }}>No jobs match.</div>}
        {jobs.map(j => (
          <div className="jobrow" key={j.id}>
            <div className="jobrow__main">
              <h3 className="jobrow__title">{j.title || 'Untitled role'}</h3>
              <div className="jobrow__meta">
                {j.companyUrl
                  ? <a href={j.companyUrl} target="_blank" rel="noopener noreferrer">{j.company || 'Unknown company'}</a>
                  : (j.company || 'Unknown company')}
                {j.location && <> · {j.location}</>}
                {j.posted && <> · {j.posted}</>}
              </div>
              {j.role && <p className="jobrow__role">{j.role}</p>}
              <div>
                <span className="tag tag--filled">{j.source}</span>
                {j.stack.map((s, i) => <span className="tag" key={i}>{s}</span>)}
              </div>
            </div>
            <div className="jobrow__actions">
              <button className="btn btn--sm btn--ghost" onClick={() => toggleSave(j)} aria-pressed={j.saved}>
                {j.saved ? '★ SAVED' : '☆ SAVE'}
              </button>
              <a className="btn btn--sm btn--ghost" href={j.url} target="_blank" rel="noopener noreferrer">POSTING ↗</a>
              <button className="btn btn--sm btn--acid" onClick={() => tailor(j)}>TAILOR →</button>
            </div>
          </div>
        ))}
      </div>

      {loading && jobs.length === 0 && <div className="center-page" style={{ minHeight: 120 }}><span className="spinner">LOADING</span></div>}

      {jobs.length < total && (
        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <button className="btn btn--sm" onClick={loadMore}>LOAD MORE</button>
        </div>
      )}
    </div>
  );
}
