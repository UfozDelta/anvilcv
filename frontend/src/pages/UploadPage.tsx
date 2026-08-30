import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type ParsedExperience, type ParsedProject, type ProjectKind } from '../lib/api';
import { Section } from '../components/Section';

type Tab = 'EXPERIENCE' | 'PROJECT';

interface ExperienceCard extends ParsedExperience { selected: boolean; dirty: boolean; }
interface ProjectCard extends ParsedProject { selected: boolean; dirty: boolean; }

export function UploadPage() {
  const navigate = useNavigate();
  const [text, setText] = useState('');
  const [parsing, setParsing] = useState(false);
  const [parseErr, setParseErr] = useState<string | null>(null);

  const [experiences, setExperiences] = useState<ExperienceCard[]>([]);
  const [projects, setProjects] = useState<ProjectCard[]>([]);
  const [parsed, setParsed] = useState(false);
  const [tab, setTab] = useState<Tab>('EXPERIENCE');

  const [importing, setImporting] = useState(false);
  const [importErr, setImportErr] = useState<string | null>(null);

  async function handleParse() {
    if (!text.trim()) return;
    setParseErr(null);
    setParsing(true);
    try {
      const result = await api.parseResume(text);
      setExperiences(result.experiences.map(e => ({ ...e, selected: true, dirty: false })));
      setProjects(result.projects.map(p => ({ ...p, selected: true, dirty: false })));
      setParsed(true);
      if (result.experiences.length === 0 && result.projects.length > 0) setTab('PROJECT');
    } catch (e: any) {
      setParseErr(e?.message || 'Parse failed');
    } finally {
      setParsing(false);
    }
  }

  function updateExp(i: number, patch: Partial<ExperienceCard>) {
    setExperiences(prev => prev.map((e, idx) => idx === i ? { ...e, ...patch, dirty: true } : e));
  }

  function updateProj(i: number, patch: Partial<ProjectCard>) {
    setProjects(prev => prev.map((p, idx) => idx === i ? { ...p, ...patch, dirty: true } : p));
  }

  async function handleImport() {
    const expItems = experiences.filter(e => e.selected).map(e => ({
      kind: 'EXPERIENCE' as ProjectKind,
      name: e.name,
      description: e.description,
      title: e.title,
      company: e.company,
      location: e.location,
      dates: e.dates,
    }));

    const projItems = projects.filter(p => p.selected).map(p => ({
      kind: 'PROJECT' as ProjectKind,
      name: p.name,
      description: p.description,
      title: null,
      company: null,
      location: null,
      dates: p.dates,
    }));

    const items = [...expItems, ...projItems];
    if (items.length === 0) return;

    setImportErr(null);
    setImporting(true);
    try {
      await api.post('/api/resume/import', { items });
      navigate(expItems.length > 0 ? '/experiences' : '/projects');
    } catch (e: any) {
      setImportErr(e?.message || 'Import failed');
    } finally {
      setImporting(false);
    }
  }

  const selectedCount = experiences.filter(e => e.selected).length + projects.filter(p => p.selected).length;
  const totalCount = experiences.length + projects.length;
  const nothingFound = parsed && totalCount === 0;

  return (
    <div className="shell">
      <Section num="—" title="Import from Resume" />

      <div className="panel panel--inset stack" style={{ marginTop: 24 }}>
        <div className="label">PASTE RESUME TEXT</div>
        <p className="editorial muted" style={{ marginBottom: 8 }}>
          Copy plain text from your resume (e.g. from a PDF viewer or Word doc) and paste below.
          Works best with standard section headers like "Experience" and "Projects".
        </p>
        <textarea
          className="field__textarea"
          value={text}
          onChange={e => { setText(e.target.value); if (parsed) setParsed(false); }}
          style={{ minHeight: 240, fontFamily: 'var(--mono)', fontSize: '0.8rem' }}
          placeholder="Paste your resume text here..."
          disabled={parsing}
        />
        {parseErr && <div className="err">{parseErr}</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn btn--acid" onClick={handleParse} disabled={parsing || !text.trim()}>
            {parsing ? <span className="spinner">PARSING</span> : 'PARSE RESUME →'}
          </button>
        </div>
      </div>

      {nothingFound && (
        <div className="panel panel--inset" style={{ marginTop: 24 }}>
          <p className="editorial muted">
            No sections found. Make sure your resume has headers like "Experience", "Work Experience", or "Projects".
          </p>
        </div>
      )}

      {parsed && totalCount > 0 && (
        <>
          <div style={{ marginTop: 32, display: 'flex', gap: 8, borderBottom: 'var(--rule-thin)', paddingBottom: 0 }}>
            <button
              className={`btn ${tab === 'EXPERIENCE' ? 'btn--acid' : 'btn--ghost'}`}
              onClick={() => setTab('EXPERIENCE')}
            >
              EXPERIENCES ({experiences.length})
            </button>
            <button
              className={`btn ${tab === 'PROJECT' ? 'btn--acid' : 'btn--ghost'}`}
              onClick={() => setTab('PROJECT')}
            >
              PROJECTS ({projects.length})
            </button>
          </div>

          {tab === 'EXPERIENCE' && (
            <div className="stack" style={{ marginTop: 20, gap: 16 }}>
              {experiences.length === 0 && (
                <p className="editorial muted">No experiences detected.</p>
              )}
              {experiences.map((e, i) => (
                <div key={i} className="panel panel--inset" style={{ opacity: e.selected ? 1 : 0.45 }}>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 12 }}>
                    <input type="checkbox" checked={e.selected} onChange={ev => updateExp(i, { selected: ev.target.checked })} style={{ marginTop: 4 }} />
                    <div className="grid-2" style={{ flex: 1 }}>
                      <label className="field">
                        <div className="field__label">Job Title</div>
                        <input className="field__input" value={e.title || ''} onChange={ev => updateExp(i, { title: ev.target.value })} />
                      </label>
                      <label className="field">
                        <div className="field__label">Company</div>
                        <input className="field__input" value={e.company || ''} onChange={ev => updateExp(i, { company: ev.target.value })} />
                      </label>
                      <label className="field">
                        <div className="field__label">Location</div>
                        <input className="field__input" value={e.location || ''} onChange={ev => updateExp(i, { location: ev.target.value })} />
                      </label>
                      <label className="field">
                        <div className="field__label">Dates</div>
                        <input className="field__input" value={e.dates || ''} onChange={ev => updateExp(i, { dates: ev.target.value })} />
                      </label>
                      <label className="field" style={{ gridColumn: '1 / -1' }}>
                        <div className="field__label">Internal Label</div>
                        <input className="field__input" value={e.name} onChange={ev => updateExp(i, { name: ev.target.value })} />
                      </label>
                      <label className="field" style={{ gridColumn: '1 / -1' }}>
                        <div className="field__label">Description</div>
                        <textarea className="field__textarea" value={e.description} onChange={ev => updateExp(i, { description: ev.target.value })} style={{ minHeight: 100 }} />
                      </label>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {tab === 'PROJECT' && (
            <div className="stack" style={{ marginTop: 20, gap: 16 }}>
              {projects.length === 0 && (
                <p className="editorial muted">No projects detected.</p>
              )}
              {projects.map((p, i) => (
                <div key={i} className="panel panel--inset" style={{ opacity: p.selected ? 1 : 0.45 }}>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 12 }}>
                    <input type="checkbox" checked={p.selected} onChange={ev => updateProj(i, { selected: ev.target.checked })} style={{ marginTop: 4 }} />
                    <div className="grid-2" style={{ flex: 1 }}>
                      <label className="field">
                        <div className="field__label">Project Name</div>
                        <input className="field__input" value={p.name} onChange={ev => updateProj(i, { name: ev.target.value })} />
                      </label>
                      <label className="field">
                        <div className="field__label">Dates</div>
                        <input className="field__input" value={p.dates || ''} onChange={ev => updateProj(i, { dates: ev.target.value })} />
                      </label>
                      <label className="field" style={{ gridColumn: '1 / -1' }}>
                        <div className="field__label">Description</div>
                        <textarea className="field__textarea" value={p.description} onChange={ev => updateProj(i, { description: ev.target.value })} style={{ minHeight: 100 }} />
                      </label>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {importErr && <div className="err" style={{ marginTop: 16 }}>{importErr}</div>}

          <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
            <span className="editorial muted" style={{ alignSelf: 'center' }}>
              {selectedCount} of {totalCount} selected
            </span>
            <button
              className="btn btn--acid"
              onClick={handleImport}
              disabled={importing || selectedCount === 0}
            >
              {importing ? <span className="spinner">IMPORTING</span> : `IMPORT SELECTED (${selectedCount}) →`}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
