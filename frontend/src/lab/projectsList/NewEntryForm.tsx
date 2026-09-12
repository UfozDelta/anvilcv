import { useState, type FormEvent } from 'react';
import type { Project } from '../../lib/api';

export type Kind = 'PROJECT' | 'EXPERIENCE';

export function NewEntryForm({ kind, onCreate, onCancel }: {
  kind: Kind;
  onCreate: (p: Project) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [githubUrl, setGithubUrl] = useState('');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [dates, setDates] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (kind === 'PROJECT' && !name.trim()) { setErr('Name is required.'); return; }
    if (kind === 'EXPERIENCE' && (!title.trim() || !company.trim() || !name.trim())) {
      setErr('Job title, company, and internal label are required.');
      return;
    }
    if (!description.trim()) { setErr('Description is required.'); return; }
    setErr(null);
    setBusy(true);
    window.setTimeout(() => {
      const base: Project = {
        id: `lab-new-${Date.now()}`,
        kind,
        name,
        description,
        createdAt: new Date().toISOString(),
        ...(kind === 'PROJECT' ? { githubUrl: githubUrl || null } : { title, company, location, dates }),
      };
      onCreate(base);
      setBusy(false);
    }, 400);
  }

  return (
    <form onSubmit={submit} className="panel panel--inset stack" style={{ marginTop: 12 }}>
      <div className="label">{kind === 'PROJECT' ? 'NEW PROJECT' : 'NEW EXPERIENCE / ROLE'}</div>

      {kind === 'EXPERIENCE' && (
        <div className="grid-2">
          <label className="field">
            <div className="field__label">Job Title</div>
            <input className="field__input" autoFocus value={title} onChange={e => setTitle(e.target.value)}
              placeholder="e.g. Software Engineer" />
          </label>
          <label className="field">
            <div className="field__label">Company</div>
            <input className="field__input" value={company} onChange={e => setCompany(e.target.value)}
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
      )}

      <label className="field">
        <div className="field__label">{kind === 'PROJECT' ? 'Name' : 'Internal label'}</div>
        <input className="field__input" autoFocus={kind === 'PROJECT'} value={name} onChange={e => setName(e.target.value)}
          placeholder={kind === 'PROJECT' ? 'e.g. resume-pipeline' : 'e.g. acme-corp-swe'} />
      </label>

      {kind === 'PROJECT' && (
        <label className="field">
          <div className="field__label">GitHub URL (optional — enriches AI context)</div>
          <input className="field__input" value={githubUrl} onChange={e => setGithubUrl(e.target.value)}
            placeholder="https://github.com/owner/repo" />
        </label>
      )}

      <label className="field">
        <div className="field__label">Short description</div>
        <textarea className="field__textarea" value={description} onChange={e => setDescription(e.target.value)}
          style={{ minHeight: 52 }}
          placeholder="One or two sentences — shown in the list. Full context goes in Info & Context after this is created." />
      </label>

      {err && <div className="err">{err}</div>}
      <div className="row row--between">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>CANCEL</button>
        <button type="submit" className="btn btn--acid" disabled={busy}>
          {busy ? <span className="spinner">CREATING</span> : <>CREATE &nbsp;→</>}
        </button>
      </div>
    </form>
  );
}
