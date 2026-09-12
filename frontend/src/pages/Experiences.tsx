import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Project } from '../lib/api';
import { RowMenu } from '../components/ProjectsList/RowMenu';
import { NewEntryForm } from '../components/ProjectsList/NewEntryForm';

type Sort = 'newest' | 'name';

const SORTS: { key: Sort; label: string }[] = [
  { key: 'newest', label: 'Newest first' },
  { key: 'name', label: 'A → Z' },
];

export function Experiences() {
  const [rows, setRows] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('newest');
  const [showForm, setShowForm] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try { setRows(await api.get<Project[]>('/api/projects?kind=EXPERIENCE')); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    let out = rows;
    if (needle) {
      out = out.filter(r =>
        (r.title ?? '').toLowerCase().includes(needle) ||
        (r.company ?? '').toLowerCase().includes(needle));
    }
    const by: Record<Sort, (a: Project, b: Project) => number> = {
      newest: (a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
      name: (a, b) => (a.title || a.name).localeCompare(b.title || b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, q, sort]);

  async function del(id: string, label: string) {
    setErr(null);
    try {
      await api.del(`/api/projects/${id}`);
      await load();
    } catch (e: any) {
      setErr(e?.message || `Failed to delete "${label}"`);
    }
  }

  async function dup(id: string) {
    setErr(null);
    try {
      await api.post(`/api/projects/${id}/duplicate`);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'Failed to duplicate');
    }
  }

  function create(p: Project) {
    setRows(rs => [p, ...rs]);
    setShowForm(false);
  }

  return (
    <div className="shell">
      <div className="toolbar">
        <input
          className="toolbar__search"
          placeholder="Search role or company…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />

        <select
          className="approw__status"
          style={{ width: 'auto' }}
          value={sort}
          onChange={e => setSort(e.target.value as Sort)}
        >
          {SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>
      </div>

      {err && <div className="err" style={{ marginBottom: 12 }}>{err}</div>}

      {loading ? <span className="spinner">LOADING</span> : (
        <>
          <div className="applist">
            {shown.map(p => {
              const title = p.title || p.name;
              const meta = [p.company, p.location, p.dates].filter(Boolean).join(' · ') || '—';
              return (
                <div className="approw" key={p.id} style={{ gridTemplateColumns: 'minmax(0,1fr) 150px 34px' }}>
                  <Link className="approw__link" to={`/experiences/${p.id}`}>{title}</Link>

                  <div>
                    <h3 className="approw__title">{title}</h3>
                    <div className="approw__role">{meta}</div>
                  </div>

                  <div />

                  <RowMenu onDelete={() => del(p.id, title)} onDuplicate={() => dup(p.id)} />
                </div>
              );
            })}

            {shown.length === 0 && (
              <div style={{ padding: '44px 0', textAlign: 'center', borderBottom: 'var(--rule-thin)' }}>
                <div className="editorial" style={{ fontSize: 17, marginBottom: 6 }}>
                  {q ? `Nothing matches "${q}".` : 'No experiences yet.'}
                </div>
                {q && <button className="minibtn" onClick={() => setQ('')}>Clear search</button>}
              </div>
            )}
          </div>

          <div style={{ marginTop: 28 }}>
            {!showForm ? (
              <button className="btn btn--acid" onClick={() => setShowForm(true)}>+ NEW EXPERIENCE</button>
            ) : (
              <NewEntryForm kind="EXPERIENCE" onCreate={create} onCancel={() => setShowForm(false)} />
            )}
          </div>
        </>
      )}
    </div>
  );
}
