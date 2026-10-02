import { useEffect, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from './AppNav';
import { PageTitle, useDemoPage } from './shared';
import { PROFILE, type DemoEducation } from './appData';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';

const BASICS = [
  { key: 'name', label: 'Full name' },
  { key: 'email', label: 'Email', type: 'email' },
  { key: 'phone', label: 'Phone', type: 'tel' },
  { key: 'linkedin', label: 'LinkedIn', prefix: 'linkedin.com/in/' },
  { key: 'github', label: 'GitHub', prefix: 'github.com/' },
  { key: 'portfolio', label: 'Portfolio', type: 'url', optional: true },
] as const;
type BasicKey = (typeof BASICS)[number]['key'];

const SKILLS = [
  { key: 'languages', label: 'Languages', hint: 'Python, TypeScript, …' },
  { key: 'frameworks', label: 'Frameworks', hint: 'React, Next.js, …' },
  { key: 'databases', label: 'Databases & AI', hint: 'PostgreSQL, RAG, …' },
  { key: 'devops', label: 'DevOps & tools', hint: 'Docker, CI/CD, …' },
  { key: 'interests', label: 'Interests', hint: 'Hackathons, Chess, …' },
] as const;
type SkillKey = (typeof SKILLS)[number]['key'];

let nextId = 100;

function Row({ label, prefix, children }: { label: string; prefix?: string; children: React.ReactNode }) {
  return (
    <label className="pf-field">
      <span className="pf-field__k">{label}</span>
      <span className="pf-field__v">{prefix && <span className="pf-field__prefix" aria-hidden="true">{prefix}</span>}{children}</span>
    </label>
  );
}

function EducationRow({ e, open, onToggle, onChange, onRemove }: {
  e: DemoEducation; open: boolean; onToggle: () => void; onChange: (p: Partial<DemoEducation>) => void; onRemove: () => void;
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

function ProfileLedger({ empty }: { empty: boolean }) {
  const init = empty ? { basics: { name: '', email: '', phone: '', linkedin: '', github: '', portfolio: '' }, edu: [], skills: { languages: '', frameworks: '', databases: '', devops: '', interests: '' } } : PROFILE;
  const [basics, setBasics] = useState<Record<BasicKey, string>>(init.basics);
  const [skills, setSkills] = useState<Record<SkillKey, string>>(init.skills);
  const [edu, setEdu] = useState<DemoEducation[]>(init.edu);
  const [open, setOpen] = useState<string | null>(null);
  const [saved, setSaved] = useState(() => JSON.stringify({ basics, edu, skills }));
  const [justSaved, setJustSaved] = useState(false);

  const snapshot = JSON.stringify({ basics, edu, skills });
  const dirty = snapshot !== saved;

  const sectionsDone = [
    BASICS.filter((b) => !('optional' in b)).every((b) => basics[b.key].trim()),
    edu.length > 0,
    SKILLS.every((k) => skills[k.key].trim()),
  ].filter(Boolean).length;

  useEffect(() => {
    if (!justSaved) return;
    const t = window.setTimeout(() => setJustSaved(false), 2000);
    return () => window.clearTimeout(t);
  }, [justSaved]);

  const addEdu = () => {
    const id = `edu-${nextId++}`;
    setEdu((rs) => [...rs, { id, school: '', location: '', degree: '', dates: '', coursework: '' }]);
    setOpen(id);
  };
  const patchEdu = (id: string, p: Partial<DemoEducation>) => setEdu((rs) => rs.map((r) => (r.id === id ? { ...r, ...p } : r)));
  const removeEdu = (id: string) => setEdu((rs) => rs.filter((r) => r.id !== id));
  const save = (e: React.FormEvent) => { e.preventDefault(); setSaved(snapshot); setJustSaved(true); };

  return (
    <div className="shell ap-page pf-page">
      <PageTitle title="Profile" count={<><strong>{sectionsDone}</strong> of 3 sections complete</>} />

      <form onSubmit={save}>
        <section className="pf-sec">
          <h2 className="ap-label">Basics</h2>
          <div className="pf-grid">
            {BASICS.map((b) => (
              <Row key={b.key} label={b.label} prefix={'prefix' in b ? b.prefix : undefined}>
                <input
                  type={'type' in b ? b.type : 'text'}
                  value={basics[b.key]}
                  placeholder={'optional' in b ? 'Optional' : undefined}
                  autoComplete="off"
                  onChange={(e) => setBasics((s) => ({ ...s, [b.key]: e.target.value }))}
                />
              </Row>
            ))}
          </div>
        </section>

        <section className="pf-sec">
          <h2 className="ap-label">Education</h2>
          <div className="pf-list">
            {edu.map((e) => (
              <EducationRow
                key={e.id}
                e={e}
                open={open === e.id}
                onToggle={() => setOpen(open === e.id ? null : e.id)}
                onChange={(p) => patchEdu(e.id, p)}
                onRemove={() => removeEdu(e.id)}
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
                  value={skills[s.key]}
                  placeholder={s.hint}
                  onChange={(e) => setSkills((v) => ({ ...v, [s.key]: e.target.value }))}
                />
              </Row>
            ))}
          </div>
        </section>

        {(dirty || justSaved) && (
          <div className="pf-save" role="status">
            <span>{dirty ? 'Unsaved changes' : 'Saved'}</span>
            {dirty && <button type="submit" className="ap-btn ap-btn--acid">Save profile</button>}
          </div>
        )}
      </form>
    </div>
  );
}

/** /lab/profile — Profile in the Ledger style, under the Strip nav. E toggles first-run (blank), R replays. */
export function LabProfile() {
  const { empty, mountKey, replay } = useDemoPage();
  return (
    <div className="ap-root">
      <NavStrip />
      <ProfileLedger key={`${mountKey}-${empty}`} empty={empty} />
      <Picker names={['Ledger']} current={0} onPick={() => {}} onReplay={replay} />
    </div>
  );
}
