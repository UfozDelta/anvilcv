import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../lib/api';
import { Section } from '../components/Section';

interface EducationEntry {
  school: string;
  location: string;
  degree: string;
  dates: string;
  coursework: string;
}

interface ProfileDto {
  name: string;
  phone: string;
  email: string;
  linkedinHandle: string;
  githubHandle: string;
  portfolioUrl: string | null;
  education: EducationEntry[];
  skillsLanguages: string;
  skillsFrameworks: string;
  skillsDatabases: string;
  skillsDevops: string;
  skillsInterests: string;
}

const EMPTY_EDU: EducationEntry = { school: '', location: '', degree: '', dates: '', coursework: '' };

export function ProfilePage() {
  const [p, setP] = useState<ProfileDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api.get<ProfileDto>('/api/profile')
      .then(setP)
      .catch(e => setErr(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="shell"><span className="spinner">LOADING</span></div>;
  if (!p) return <div className="shell"><div className="err">{err || 'Failed to load profile'}</div></div>;

  function update<K extends keyof ProfileDto>(k: K, v: ProfileDto[K]) {
    setP(prev => prev ? { ...prev, [k]: v } : prev);
  }

  function updateEdu(i: number, patch: Partial<EducationEntry>) {
    setP(prev => prev ? { ...prev, education: prev.education.map((e, idx) => idx === i ? { ...e, ...patch } : e) } : prev);
  }
  function addEdu() { setP(prev => prev ? { ...prev, education: [...prev.education, { ...EMPTY_EDU }] } : prev); }
  function delEdu(i: number) { setP(prev => prev ? { ...prev, education: prev.education.filter((_, idx) => idx !== i) } : prev); }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!p) return;
    setSaving(true); setErr(null);
    try {
      const updated = await api.put<ProfileDto>('/api/profile', p);
      setP(updated);
      setSavedAt(new Date());
    } catch (e: any) {
      setErr(e?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="shell">
      <Section num="00" title="Profile" />
      <div className="editorial muted" style={{ fontSize: 16, marginBottom: 28, maxWidth: 720 }}>
        The static parts of every résumé. Use <code>**double asterisks**</code> in bullets and degree text to <strong>bold</strong> metrics or technologies.
      </div>

      <form onSubmit={save} className="stack">
        {/* BASICS */}
        <div className="panel panel--inset stack-sm">
          <div className="label">BASICS</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Full Name" value={p.name} onChange={v => update('name', v)} />
            <Field label="Phone" value={p.phone} onChange={v => update('phone', v)} />
            <Field label="Email" value={p.email} onChange={v => update('email', v)} />
            <Field label="LinkedIn handle" value={p.linkedinHandle} onChange={v => update('linkedinHandle', v)} hint="linkedin.com/in/<handle>" />
            <Field label="GitHub handle" value={p.githubHandle} onChange={v => update('githubHandle', v)} hint="github.com/<handle>" />
            <Field label="Portfolio URL (optional)" value={p.portfolioUrl || ''} onChange={v => update('portfolioUrl', v || null)} />
          </div>
        </div>

        {/* EDUCATION */}
        <Section num="00.A" title="Education" count={p.education.length} />
        {p.education.map((e, i) => (
          <div key={i} className="panel panel--inset stack-sm">
            <div className="row row--between row--centered">
              <div className="label">ENTRY {i + 1}</div>
              <button type="button" className="btn btn--ghost btn--sm btn--rust" onClick={() => delEdu(i)}>REMOVE</button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Field label="School" value={e.school} onChange={v => updateEdu(i, { school: v })} />
              <Field label="Location" value={e.location} onChange={v => updateEdu(i, { location: v })} />
              <Field label="Degree" value={e.degree} onChange={v => updateEdu(i, { degree: v })} />
              <Field label="Dates" value={e.dates} onChange={v => updateEdu(i, { dates: v })} hint="e.g. Sep. 2023 - April 2027" />
            </div>
            <TextField label="Coursework (comma-separated)" value={e.coursework} onChange={v => updateEdu(i, { coursework: v })} />
          </div>
        ))}
        <div><button type="button" className="btn btn--sm" onClick={addEdu}>+ ADD EDUCATION</button></div>

        <div className="editorial muted" style={{ padding: '20px 0' }}>
          Work experience now lives in <strong>02 — EXPERIENCES</strong> with its own AI-generated bullet bank per role.
        </div>

        {/* SKILLS */}
        <Section num="00.B" title="Skills" />
        <div className="panel panel--inset stack-sm">
          <TextField label="Languages"      value={p.skillsLanguages}  onChange={v => update('skillsLanguages', v)}  hint="Python, TypeScript, ..." />
          <TextField label="Frameworks"     value={p.skillsFrameworks} onChange={v => update('skillsFrameworks', v)} hint="React, Next.js, ..." />
          <TextField label="Databases & AI" value={p.skillsDatabases}  onChange={v => update('skillsDatabases', v)}  hint="PostgreSQL, MongoDB, RAG, embeddings, ..." />
          <TextField label="DevOps & Tools" value={p.skillsDevops}     onChange={v => update('skillsDevops', v)}     hint="Docker, CI/CD, Stripe, Clerk, ..." />
          <TextField label="Interests"      value={p.skillsInterests}  onChange={v => update('skillsInterests', v)}  hint="Hackathons, Chess, ..." />
        </div>

        {err && <div className="err">{err}</div>}

        <div className="row row--between row--centered" style={{ position: 'sticky', bottom: 16, background: 'var(--paper)', padding: '12px 0', borderTop: '2px solid var(--ink)', marginTop: 12 }}>
          <span className="label muted">
            {savedAt ? `SAVED ${savedAt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : 'EDIT · APPLIES TO NEXT RENDER'}
          </span>
          <button type="submit" className="btn btn--acid" disabled={saving}>
            {saving ? <span className="spinner">SAVING</span> : <>SAVE PROFILE &nbsp;→</>}
          </button>
        </div>
      </form>
    </div>
  );
}

function Field({ label, value, onChange, hint }: { label: string; value: string; onChange: (v: string) => void; hint?: string }) {
  return (
    <label className="field">
      <div className="field__label">{label}</div>
      <input className="field__input" value={value} onChange={e => onChange(e.target.value)} />
      {hint && <div className="field__hint">{hint}</div>}
    </label>
  );
}

function TextField({ label, value, onChange, hint }: { label: string; value: string; onChange: (v: string) => void; hint?: string }) {
  return (
    <label className="field">
      <div className="field__label">{label}</div>
      <textarea className="field__textarea" style={{ minHeight: 60 }} value={value} onChange={e => onChange(e.target.value)} />
      {hint && <div className="field__hint">{hint}</div>}
    </label>
  );
}
