import { useState } from 'react';
import type { Project } from '../../lib/api';

export function EditProjectHeader({ project, onSave, onCancel }: {
  project: Project;
  onSave: (patch: Partial<Project>) => void;
  onCancel: () => void;
}) {
  const isExperience = project.kind === 'EXPERIENCE';
  const [name, setName] = useState(project.name);
  const [techStack, setTechStack] = useState(project.techStack || '');
  const [title, setTitle] = useState(project.title || '');
  const [company, setCompany] = useState(project.company || '');
  const [location, setLocation] = useState(project.location || '');
  const [dates, setDates] = useState(project.dates || '');

  function save() {
    onSave(isExperience
      ? { title, company, location, dates }
      : { name, techStack, dates });
  }

  return (
    <div className="stack-sm" style={{ marginBottom: 8, padding: 8, border: '2px solid var(--ink)' }}>
      {isExperience ? (
        <>
          <input className="field__input" value={title} onChange={e => setTitle(e.target.value)} placeholder="Title" />
          <input className="field__input" value={company} onChange={e => setCompany(e.target.value)} placeholder="Company" />
          <input className="field__input" value={location} onChange={e => setLocation(e.target.value)} placeholder="Location" />
          <input className="field__input" value={dates} onChange={e => setDates(e.target.value)} placeholder="Dates" />
        </>
      ) : (
        <>
          <input className="field__input" value={name} onChange={e => setName(e.target.value)} placeholder="Project name" />
          <input className="field__input" value={techStack} onChange={e => setTechStack(e.target.value)} placeholder="Tech stack" />
          <input className="field__input" value={dates} onChange={e => setDates(e.target.value)} placeholder="Dates" />
        </>
      )}
      <div className="row">
        <button className="btn btn--sm" onClick={save}>SAVE</button>
        <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
      </div>
    </div>
  );
}
