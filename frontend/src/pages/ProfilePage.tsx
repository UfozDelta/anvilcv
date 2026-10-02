import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../lib/api';
import { PageTitle, useUnsavedGuard } from '../components/ledger/shared';

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

type BasicKey = 'name' | 'email' | 'phone' | 'linkedinHandle' | 'githubHandle' | 'portfolioUrl';
const BASICS: { key: BasicKey; label: string; type?: string; prefix?: string; optional?: boolean }[] = [
  { key: 'name', label: 'Full name' },
  { key: 'email', label: 'Email', type: 'email' },
  { key: 'phone', label: 'Phone', type: 'tel' },
  { key: 'linkedinHandle', label: 'LinkedIn', prefix: 'linkedin.com/in/' },
  { key: 'githubHandle', label: 'GitHub', prefix: 'github.com/' },
  { key: 'portfolioUrl', label: 'Portfolio', type: 'url', optional: true },
];

type SkillKey = 'skillsLanguages' | 'skillsFrameworks' | 'skillsDatabases' | 'skillsDevops' | 'skillsInterests';
const SKILLS: { key: SkillKey; label: string; hint: string }[] = [
  { key: 'skillsLanguages', label: 'Languages', hint: 'Python, TypeScript, …' },
  { key: 'skillsFrameworks', label: 'Frameworks', hint: 'React, Next.js, …' },
  { key: 'skillsDatabases', label: 'Databases & AI', hint: 'PostgreSQL, RAG, …' },
  { key: 'skillsDevops', label: 'DevOps & tools', hint: 'Docker, CI/CD, …' },
  { key: 'skillsInterests', label: 'Interests', hint: 'Hackathons, Chess, …' },
];

function Row({ label, prefix, children }: { label: string; prefix?: string; children: React.ReactNode }) {
  return (
    <label className="pf-field">
      <span className="pf-field__k">{label}</span>
      <span className="pf-field__v">{prefix && <span className="pf-field__prefix" aria-hidden="true">{prefix}</span>}{children}</span>
    </label>
  );
}

function EducationRow({ e, open, onToggle, onChange, onRemove }: {
  e: EducationEntry; open: boolean; onToggle: () => void; onChange: (p: Partial<EducationEntry>) => void; onRemove: () => void;
}) {
  const summary = [e.degree, e.dates].filter(Boolean).join(' · ');
  return (
    <div className="pf-edu" data-open={open || undefined}>
      <button type="button" className="pf-edu__head" aria-expanded={open} onClick={onToggle}>
        <span className="ld-name">
          <strong className="pf-edu__name">{e.school || 'New school'}</strong>
          <span className="ld-desc">{summary || 'Add degree and dates'}</span>
        </span>
        <span className="pf-edu__chev" aria-hidden="true">{open ? 'Done' : 'Edit'}</span>
      </button>
      {open && (
        <div className="pf-edu__body">
          <div className="pf-grid">
            <Row label="School"><input value={e.school} onChange={(ev) => onChange({ school: ev.target.value })} autoFocus={!e.school} /></Row>
            <Row label="Location"><input value={e.location} onChange={(ev) => onChange({ location: ev.target.value })} /></Row>
            <Row label="Degree"><input value={e.degree} onChange={(ev) => onChange({ degree: ev.target.value })} placeholder="B.S. Computer Science" /></Row>
            <Row label="Dates"><input value={e.dates} onChange={(ev) => onChange({ dates: ev.target.value })} placeholder="Sep. 2023 – Apr. 2027" /></Row>
          </div>
          <Row label="Coursework"><textarea rows={1} value={e.coursework} onChange={(ev) => onChange({ coursework: ev.target.value })} placeholder="Comma-separated" /></Row>
          <button type="button" className="pf-remove" onClick={onRemove}>Remove</button>
        </div>
      )}
    </div>
  );
}

export function ProfilePage() {
  const [p, setP] = useState<ProfileDto | null>(null);
  const [saved, setSaved] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [justSaved, setJustSaved] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  useEffect(() => {
    api.get<ProfileDto>('/api/profile')
      .then((r) => { setP(r); setSaved(JSON.stringify(r)); })
      .catch(e => setErr(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!justSaved) return;
    const t = window.setTimeout(() => setJustSaved(false), 2000);
    return () => window.clearTimeout(t);
  }, [justSaved]);

  const dirty = p !== null && JSON.stringify(p) !== saved;
  useUnsavedGuard(dirty);

  if (loading) return <div className="shell ap-page pf-page"><span className="spinner">LOADING</span></div>;
  if (!p) return <div className="shell ap-page pf-page"><div className="err">{err || 'Failed to load profile'}</div></div>;

  const sectionsDone = [
    BASICS.filter((b) => !b.optional).every((b) => (p[b.key] ?? '').trim()),
    p.education.length > 0,
    SKILLS.every((k) => p[k.key].trim()),
  ].filter(Boolean).length;

  function update<K extends keyof ProfileDto>(k: K, v: ProfileDto[K]) {
    setP(prev => prev ? { ...prev, [k]: v } : prev);
  }

  function updateEdu(i: number, patch: Partial<EducationEntry>) {
    setP(prev => prev ? { ...prev, education: prev.education.map((e, idx) => idx === i ? { ...e, ...patch } : e) } : prev);
  }
  function addEdu() {
    if (!p) return;
    setP({ ...p, education: [...p.education, { ...EMPTY_EDU }] });
    setOpen(p.education.length);
  }
  function delEdu(i: number) {
    setP(prev => prev ? { ...prev, education: prev.education.filter((_, idx) => idx !== i) } : prev);
    setOpen(null);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!p) return;
    setSaving(true); setErr(null);
    try {
      const updated = await api.put<ProfileDto>('/api/profile', p);
      setP(updated);
      setSaved(JSON.stringify(updated));
      setJustSaved(true);
    } catch (e: any) {
      setErr(e?.message || 'Failed to save');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="shell ap-page pf-page">
      <PageTitle title="Profile" count={<><strong>{sectionsDone}</strong> of 3 sections complete</>} />

      <form onSubmit={save}>
        <section className="pf-sec">
          <h2 className="ap-label">Basics</h2>
          <div className="pf-grid">
            {BASICS.map((b) => (
              <Row key={b.key} label={b.label} prefix={b.prefix}>
                <input
                  type={b.type ?? 'text'}
                  value={p[b.key] ?? ''}
                  placeholder={b.optional ? 'Optional' : undefined}
                  autoComplete="off"
                  onChange={(e) => update(b.key, (b.optional ? e.target.value || null : e.target.value) as never)}
                />
              </Row>
            ))}
          </div>
        </section>

        <section className="pf-sec">
          <h2 className="ap-label">Education</h2>
          <div className="pf-list">
            {p.education.map((e, i) => (
              <EducationRow
                key={i}
                e={e}
                open={open === i}
                onToggle={() => setOpen(open === i ? null : i)}
                onChange={(patch) => updateEdu(i, patch)}
                onRemove={() => delEdu(i)}
              />
            ))}
          </div>
          <button type="button" className="ap-btn ap-btn--ghost pf-add" onClick={addEdu}>+ Add education</button>
        </section>

        <section className="pf-sec">
          <h2 className="ap-label">Skills</h2>
          <div className="pf-list">
            {SKILLS.map((s) => (
              <Row key={s.key} label={s.label}>
                <textarea
                  rows={1}
                  value={p[s.key]}
                  placeholder={s.hint}
                  onChange={(e) => update(s.key, e.target.value)}
                />
              </Row>
            ))}
          </div>
        </section>

        {(dirty || justSaved || err) && (
          <div className="pf-save" role={err ? 'alert' : 'status'}>
            <span>{err ?? (dirty ? 'Unsaved changes' : 'Saved')}</span>
            {dirty && <button type="submit" className="ap-btn ap-btn--acid" disabled={saving}>{saving ? 'Saving…' : 'Save profile'}</button>}
          </div>
        )}
      </form>
    </div>
  );
}
