import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, type Project } from '../lib/api';
import { Section } from '../components/Section';

export function Experiences() {
  const [experiences, setExperiences] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  const [name, setName] = useState('');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [dates, setDates] = useState('');
  const [description, setDescription] = useState('');

  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try { setExperiences(await api.get<Project[]>('/api/projects?kind=EXPERIENCE')); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setErr(null); setBusy(true);
    try {
      await api.post<Project>('/api/projects', {
        kind: 'EXPERIENCE', name, description, title, company, location, dates,
      });
      setName(''); setTitle(''); setCompany(''); setLocation(''); setDates(''); setDescription('');
      setShowForm(false);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'Failed to create');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="shell">
      <Section num="02" title="Experiences" count={experiences.length} />

      {loading ? <span className="spinner">LOADING</span> : (
        <>
          <div className="list">
            {experiences.map((p, i) => (
              <Link key={p.id} to={`/experiences/${p.id}`} className="list__row">
                <div className="list__num">{String(i + 1).padStart(2, '0')}</div>
                <div>
                  <h3 className="list__title">{p.title || p.name}</h3>
                  <div className="list__meta">
                    {[p.company, p.location, p.dates].filter(Boolean).join(' · ') || '—'}
                  </div>
                </div>
                <span className="list__arrow">→</span>
              </Link>
            ))}
            {experiences.length === 0 && (
              <div style={{ padding: '40px 0', borderBottom: 'var(--rule-thin)' }} className="editorial muted">
                No experiences yet. Add a role below.
              </div>
            )}
          </div>

          <div style={{ marginTop: 28 }}>
            {!showForm ? (
              <button className="btn btn--acid" onClick={() => setShowForm(true)}>+ NEW EXPERIENCE</button>
            ) : (
              <form onSubmit={submit} className="panel panel--inset stack" style={{ marginTop: 12 }}>
                <div className="label">NEW EXPERIENCE / ROLE</div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <label className="field">
                    <div className="field__label">Job Title</div>
                    <input className="field__input" autoFocus value={title} onChange={e => setTitle(e.target.value)} required
                      placeholder="e.g. Software Engineer" />
                  </label>
                  <label className="field">
                    <div className="field__label">Company</div>
                    <input className="field__input" value={company} onChange={e => setCompany(e.target.value)} required
                      placeholder="e.g. Acme Corp" />
                  </label>
                  <label className="field">
                    <div className="field__label">Location</div>
                    <input className="field__input" value={location} onChange={e => setLocation(e.target.value)}
                      placeholder="Toronto, ON" />
                  </label>
                  <label className="field">
                    <div className="field__label">Dates</div>
                    <input className="field__input" value={dates} onChange={e => setDates(e.target.value)}
                      placeholder="Jan 2025 – Present" />
                  </label>
                </div>

                <label className="field">
                  <div className="field__label">Internal label</div>
                  <input className="field__input" value={name} onChange={e => setName(e.target.value)} required
                    placeholder="e.g. acme-corp-swe" />
                </label>
                <label className="field">
                  <div className="field__label">Description / Bullet Bank Source</div>
                  <textarea className="field__textarea" value={description} onChange={e => setDescription(e.target.value)} required
                    style={{ minHeight: 160 }}
                    placeholder="Describe what you built at this role, with what tech, at what scale. Use anchor numbers (e.g., ~64K listings, ~300ms latency). AI will generate 6-12 bullets." />
                </label>

                {err && <div className="err">{err}</div>}
                <div className="row row--between">
                  <button type="button" className="btn btn--ghost" onClick={() => setShowForm(false)}>CANCEL</button>
                  <button type="submit" className="btn btn--acid" disabled={busy}>
                    {busy ? <span className="spinner">CREATING</span> : <>CREATE &nbsp;→</>}
                  </button>
                </div>
              </form>
            )}
          </div>
        </>
      )}
    </div>
  );
}
