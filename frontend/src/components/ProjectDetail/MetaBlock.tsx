import { useState } from 'react';
import { api, type Project } from '../../lib/api';
import { dropPresent, ensurePresent, looksCurrent } from '../../lib/dates';

/**
 * Experience header: role title, company, location and dates. Read mode shows them with an Edit
 * button; edit mode is a draft held here and saved only on Save.
 */
export function MetaBlock({ project, onSaved }: { project: Project; onSaved: () => void }) {
  const [edit, setEdit] = useState(false);
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [dates, setDates] = useState('');
  const [current, setCurrent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  function begin() {
    const cur = !!project.current || looksCurrent(project.dates);
    setTitle(project.title ?? '');
    setCompany(project.company ?? '');
    setLocation(project.location ?? '');
    setCurrent(cur);
    setDates(cur ? dropPresent(project.dates ?? '') : (project.dates ?? ''));
    setErr(null);
    setEdit(true);
  }

  async function save() {
    setBusy(true);
    setErr(null);
    try {
      await api.put(`/api/projects/${project.id}`, {
        title, company, location, dates: current ? ensurePresent(dates) : dates, current,
      });
      setEdit(false);
      onSaved();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Save failed.');
    } finally {
      setBusy(false);
    }
  }

  if (!edit) {
    const line = [project.company, project.location, project.dates].filter(Boolean).join(' · ');
    return (
      <div className="sf-meta">
        <div className="editorial" style={{ fontSize: 18, color: 'var(--ink)' }}>{line || 'No company or dates yet.'}</div>
        <button type="button" className="minibtn" onClick={begin}>Edit</button>
      </div>
    );
  }

  return (
    <div className="sf-meta panel panel--inset stack-sm">
      <div className="sf-meta__grid">
        <div className="field">
          <label className="field__label" htmlFor="sf-meta-title">Title</label>
          <input id="sf-meta-title" className="field__input" value={title} onChange={e => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="sf-meta-company">Company</label>
          <input id="sf-meta-company" className="field__input" value={company} onChange={e => setCompany(e.target.value)} />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="sf-meta-location">Location</label>
          <input id="sf-meta-location" className="field__input" value={location} onChange={e => setLocation(e.target.value)} />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="sf-meta-dates">Dates</label>
          <input id="sf-meta-dates" className="field__input" value={dates} disabled={current} placeholder="Jan 2022 – Mar 2024"
            onChange={e => setDates(e.target.value)} />
        </div>
      </div>
      <label className="na-check">
        <input type="checkbox" checked={current} onChange={e => setCurrent(e.target.checked)} />
        <span>I currently work here</span>
      </label>
      {err && <div className="err">{err}</div>}
      <div className="row" style={{ gap: 8 }}>
        <button type="button" className="btn btn--acid btn--sm" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
        <button type="button" className="btn btn--ghost btn--sm" disabled={busy} onClick={() => setEdit(false)}>Cancel</button>
      </div>
    </div>
  );
}
