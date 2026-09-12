import { useState } from 'react';
import type { Project } from '../../lib/api';
import { parseExtract, type ExtractField } from '../../lib/parseExtract';

/** The 8 context fields plus the long-form context description — identical set for Projects
 *  and Experiences, fed by the same paste extractor either way. Only the header fields above
 *  them differ. `contextDescription` is distinct from the project's short list-row
 *  `description`: the extractor's "Architecture Overview" section fills this one, not that. */
function useContextFields(project: Project) {
  const [techStack, setTechStack] = useState(project.techStack ?? '');
  const [yourRole, setYourRole] = useState(project.yourRole ?? '');
  const [ownership, setOwnership] = useState(project.ownership ?? '');
  const [scaleImpact, setScaleImpact] = useState(project.scaleImpact ?? '');
  const [hardestProblem, setHardestProblem] = useState(project.hardestProblem ?? '');
  const [technicalDecisions, setTechnicalDecisions] = useState(project.technicalDecisions ?? '');
  const [userImpact, setUserImpact] = useState(project.userImpact ?? '');
  const [securityPosture, setSecurityPosture] = useState(project.securityPosture ?? '');
  const [description, setDescription] = useState(project.contextDescription ?? '');

  const setters: Record<ExtractField, (v: string) => void> = {
    techStack: setTechStack, yourRole: setYourRole, ownership: setOwnership,
    scaleImpact: setScaleImpact, hardestProblem: setHardestProblem,
    technicalDecisions: setTechnicalDecisions, userImpact: setUserImpact,
    securityPosture: setSecurityPosture, description: setDescription,
  };

  return {
    techStack, setTechStack, yourRole, setYourRole, ownership, setOwnership,
    scaleImpact, setScaleImpact, hardestProblem, setHardestProblem,
    technicalDecisions, setTechnicalDecisions, userImpact, setUserImpact,
    securityPosture, setSecurityPosture, description, setDescription,
    setters,
  };
}

function PasteSidebar({ setters }: { setters: Record<ExtractField, (v: string) => void> }) {
  const [pasteOpen, setPasteOpen] = useState(true);
  const [pasteText, setPasteText] = useState('');
  const [pasteMsg, setPasteMsg] = useState<string | null>(null);

  function parseAndFill() {
    setPasteMsg(null);
    const fields = parseExtract(pasteText);
    const keys = Object.keys(fields) as ExtractField[];
    if (keys.length === 0) {
      setPasteMsg('No recognized sections found. Paste the full extractor output.');
      return;
    }
    for (const k of keys) {
      const v = fields[k];
      if (v !== undefined) setters[k](v);
    }
    setPasteMsg(`Filled ${keys.length} field${keys.length === 1 ? '' : 's'} — review, then save.`);
    setPasteText('');
  }

  return (
    <div className="panel panel--inset stack-sm" style={{ position: 'sticky', top: 16, alignSelf: 'start' }}>
      <button
        type="button"
        style={{ all: 'unset', cursor: 'pointer', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        onClick={() => setPasteOpen(o => !o)}
      >
        <span className="label">PASTE EXTRACT</span>
        <span className="label muted">{pasteOpen ? '▲ COLLAPSE' : '▼ AUTO-FILL'}</span>
      </button>
      {!pasteOpen && (
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.05em' }}>
          Paste the Project Context Extractor output to auto-fill every field.
        </div>
      )}
      {pasteOpen && (
        <div className="stack-sm" style={{ marginTop: 8 }}>
          <textarea
            className="field__textarea"
            value={pasteText}
            onChange={e => setPasteText(e.target.value)}
            style={{ minHeight: 220 }}
            placeholder={"Paste the full extractor output here, e.g.\n\n## Tech Stack\nReact, PostgreSQL, AES-256-GCM…\n\n## Your Role\n…"}
          />
          <div className="row">
            <button type="button" className="btn btn--acid btn--sm" onClick={parseAndFill} disabled={!pasteText.trim()}>
              PARSE &amp; FILL
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setPasteText('')} disabled={!pasteText}>
              CLEAR
            </button>
          </div>
        </div>
      )}
      {pasteMsg && <div className="label muted" style={{ marginTop: 4 }}>{pasteMsg}</div>}
    </div>
  );
}

function ContextFieldset(f: ReturnType<typeof useContextFields>) {
  return (
    <>
      <label className="field">
        <div className="field__label">Tech stack</div>
        <input className="field__input" value={f.techStack} onChange={e => f.setTechStack(e.target.value)}
          placeholder="React, PostgreSQL, FastAPI, Redis, Docker…" />
      </label>
      <label className="field">
        <div className="field__label">Your role</div>
        <input className="field__input" value={f.yourRole} onChange={e => f.setYourRole(e.target.value)}
          placeholder="Solo / Lead / Contributor — e.g. 'Led backend, solo on infra'" />
      </label>
      <label className="field">
        <div className="field__label">What you owned end-to-end</div>
        <textarea className="field__textarea" value={f.ownership} onChange={e => f.setOwnership(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="I built the auth system, designed the DB schema, owned the data pipeline…" />
      </label>
      <label className="field">
        <div className="field__label">Scale & impact</div>
        <input className="field__input" value={f.scaleImpact} onChange={e => f.setScaleImpact(e.target.value)}
          placeholder="10k DAU, 200ms p99, reduced costs 40%, 3-person team…" />
      </label>
      <label className="field">
        <div className="field__label">Hardest problem solved</div>
        <textarea className="field__textarea" value={f.hardestProblem} onChange={e => f.setHardestProblem(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="Had to guarantee exactly-once delivery under network partitions…" />
      </label>
      <label className="field">
        <div className="field__label">Key technical decisions</div>
        <textarea className="field__textarea" value={f.technicalDecisions} onChange={e => f.setTechnicalDecisions(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="Chose Redis over Postgres pub/sub to cut write amplification…" />
      </label>
      <label className="field">
        <div className="field__label">Who it served &amp; why it mattered</div>
        <input className="field__input" value={f.userImpact} onChange={e => f.setUserImpact(e.target.value)}
          placeholder="40 brokerage tenants; an outage means lost listings…" />
      </label>
      <label className="field">
        <div className="field__label">Security &amp; compliance posture</div>
        <textarea className="field__textarea" value={f.securityPosture} onChange={e => f.setSecurityPosture(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="AES-256-GCM for tokens at rest; SOC 2 Type II…" />
      </label>
      <label className="field">
        <div className="field__label">Architecture overview</div>
        <textarea className="field__textarea" value={f.description} onChange={e => f.setDescription(e.target.value)}
          style={{ minHeight: 70 }}
          placeholder="3–5 sentences: lead each with one subsystem + its technique or number…" />
      </label>
    </>
  );
}

/** Experiences carry title/company/location/dates on top of the shared context set. */
export function ExperienceContextForm({ project }: { project: Project }) {
  const f = useContextFields(project);
  return (
    <div className="labsplit">
      <PasteSidebar setters={f.setters} />
      <div className="stack">
        <div className="grid-2">
          <label className="field">
            <div className="field__label">Title</div>
            <input className="field__input" defaultValue={project.title ?? ''} placeholder="Software Engineer Intern" />
          </label>
          <label className="field">
            <div className="field__label">Company</div>
            <input className="field__input" defaultValue={project.company ?? ''} placeholder="Acme Corp" />
          </label>
          <label className="field">
            <div className="field__label">Location</div>
            <input className="field__input" defaultValue={project.location ?? ''} placeholder="Boston, MA" />
          </label>
          <label className="field">
            <div className="field__label">Dates</div>
            <input className="field__input" defaultValue={project.dates ?? ''} placeholder="Jun 2024 – Aug 2024" />
          </label>
        </div>
        <ContextFieldset {...f} />
        <div className="row">
          <button className="btn btn--acid">SAVE INFO</button>
        </div>
      </div>
    </div>
  );
}

/** Projects have no title/company/location/dates — just a name and an optional repo link. */
export function ProjectContextForm({ project }: { project: Project }) {
  const f = useContextFields(project);
  return (
    <div className="labsplit">
      <PasteSidebar setters={f.setters} />
      <div className="stack">
        <label className="field">
          <div className="field__label">Name</div>
          <input className="field__input" defaultValue={project.name} placeholder="resume-pipeline" />
        </label>
        <label className="field">
          <div className="field__label">GitHub URL (optional — enriches AI context)</div>
          <input className="field__input" defaultValue={project.githubUrl ?? ''} placeholder="https://github.com/owner/repo" />
        </label>
        <ContextFieldset {...f} />
        <div className="row">
          <button className="btn btn--acid">SAVE INFO</button>
        </div>
      </div>
    </div>
  );
}
