import { useState, type FormEvent } from 'react';
import { api, type Project, type ProjectKind } from '../../lib/api';
import { dropPresent, ensurePresent, looksCurrent } from '../../lib/dates';

export function NewEntryForm({ kind, onCreate, onCancel }: {
  kind: ProjectKind;
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
  const [current, setCurrent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (kind === 'PROJECT' && !name.trim()) { setErr('Name is required.'); return; }
    if (kind === 'EXPERIENCE' && (!title.trim() || !company.trim() || !name.trim())) {
      setErr('Job title, company, and internal label are required.');
      return;
    }
    if (!description.trim()) { setErr('Description is required.'); return; }
    setErr(null);
    setBusy(true);
    try {
      const created = await api.post<Project>('/api/projects', {
        kind, name, description,
        ...(kind === 'PROJECT' ? { githubUrl: githubUrl || null } : { title, company, location, dates: current ? ensurePresent(dates) : dates, current }),
      });
      onCreate(created);
    } catch (e: any) {
      setErr(e?.message || 'Failed to create');
    } finally {
      setBusy(false);
    }
  }

  /** Boxed input with the label above, same vocabulary as the Profile page. */
  const field = (label: string, input: React.ReactNode) => (
    <label className="pf-field">
      <span className="pf-field__k">{label}</span>
      <span className="pf-field__v">{input}</span>
    </label>
  );

  return (
    <form onSubmit={submit} className="nf">
      <div className="nf__head">
        <h2 className="ap-label">{kind === 'PROJECT' ? 'New project' : 'New experience / role'}</h2>
      </div>

      {kind === 'EXPERIENCE' && (
        <div className="pf-grid">
          {field('Job title', <input autoFocus value={title} onChange={e => setTitle(e.target.value)} placeholder="e.g. Software Engineer" />)}
          {field('Company', <input value={company} onChange={e => setCompany(e.target.value)} placeholder="e.g. Acme Corp" />)}
          {field('Location', <input value={location} onChange={e => setLocation(e.target.value)} placeholder="Toronto, ON" />)}
          {field('Dates', <input value={dates} onChange={e => { setDates(e.target.value); setCurrent(looksCurrent(e.target.value)); }} placeholder="Jan 2025 – Present" />)}
        </div>
      )}

      {kind === 'EXPERIENCE' && (
        <label className="na-check">
          <input type="checkbox" checked={current} onChange={e => { setCurrent(e.target.checked); setDates(d => (e.target.checked ? ensurePresent(d) : dropPresent(d))); }} />
          <span>I currently work here</span>
        </label>
      )}

      {field(kind === 'PROJECT' ? 'Name' : 'Internal label',
        <input autoFocus={kind === 'PROJECT'} value={name} onChange={e => setName(e.target.value)}
          placeholder={kind === 'PROJECT' ? 'e.g. resume-pipeline' : 'e.g. acme-corp-swe'} />)}

      {kind === 'PROJECT' && field('GitHub URL (optional — enriches AI context)',
        <input value={githubUrl} onChange={e => setGithubUrl(e.target.value)} placeholder="https://github.com/owner/repo" />)}

      {field('Short description',
        <textarea rows={2} value={description} onChange={e => setDescription(e.target.value)}
          placeholder="One or two sentences — shown in the list. Full context goes in Info & Context after this is created." />)}

      {err && <div className="err ap-err" role="alert">{err}</div>}
      <div className="nf__actions">
        <button type="button" className="ap-btn ap-btn--ghost" onClick={onCancel}>Cancel</button>
        <button type="submit" className="ap-btn ap-btn--acid" disabled={busy}>{busy ? 'Creating…' : 'Create →'}</button>
      </div>
    </form>
  );
}
