import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, type Project } from '../lib/api';
import { Section } from '../components/Section';

export function Projects() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [sourcePath, setSourcePath] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try { setProjects(await api.get<Project[]>('/api/projects?kind=PROJECT')); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setErr(null); setBusy(true);
    try {
      await api.post<Project>('/api/projects', { kind: 'PROJECT', name, description, sourcePath: sourcePath || null });
      setName(''); setDescription(''); setSourcePath('');
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
      <Section num="01" title="Projects" count={projects.length} />

      {loading ? <span className="spinner">LOADING</span> : (
        <>
          <div className="list">
            {projects.map((p, i) => (
              <Link key={p.id} to={`/projects/${p.id}`} className="list__row">
                <div className="list__num">{String(i + 1).padStart(2, '0')}</div>
                <div>
                  <h3 className="list__title">{p.name}</h3>
                  <div className="list__meta">{p.sourcePath || 'description-only'}</div>
                </div>
                <span className="list__arrow">→</span>
              </Link>
            ))}
            {projects.length === 0 && (
              <div style={{ padding: '40px 0', borderBottom: 'var(--rule-thin)' }} className="editorial muted">
                No projects yet. Add one below.
              </div>
            )}
          </div>

          <div style={{ marginTop: 28 }}>
            {!showForm ? (
              <button className="btn btn--acid" onClick={() => setShowForm(true)}>+ NEW PROJECT</button>
            ) : (
              <form onSubmit={submit} className="panel panel--inset stack" style={{ marginTop: 12 }}>
                <div className="label">NEW PROJECT</div>
                <label className="field">
                  <div className="field__label">Name</div>
                  <input className="field__input" autoFocus value={name} onChange={e => setName(e.target.value)} required
                    placeholder="e.g. resume-pipeline" />
                </label>
                <label className="field">
                  <div className="field__label">Description / Bullet Bank Source</div>
                  <textarea className="field__textarea" value={description} onChange={e => setDescription(e.target.value)} required
                    style={{ minHeight: 160 }}
                    placeholder="What does this project do? What did you build, with what tech, at what scale?" />
                </label>
                <label className="field">
                  <div className="field__label">Local repo path (optional)</div>
                  <input className="field__input" value={sourcePath} onChange={e => setSourcePath(e.target.value)}
                    placeholder="C:\path\to\repo" />
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
