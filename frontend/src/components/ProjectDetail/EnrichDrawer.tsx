import { motion } from 'framer-motion';

export function EnrichDrawer(props: {
  onClose: () => void;
  pasteOpen: boolean; setPasteOpen: (v: boolean | ((o: boolean) => boolean)) => void;
  pasteText: string; setPasteText: (v: string) => void;
  pasteMsg: string | null; parseAndFill: () => void;
  techStack: string; setTechStack: (v: string) => void;
  yourRole: string; setYourRole: (v: string) => void;
  ownership: string; setOwnership: (v: string) => void;
  scaleImpact: string; setScaleImpact: (v: string) => void;
  hardestProblem: string; setHardestProblem: (v: string) => void;
  editDescription: string; setEditDescription: (v: string) => void;
  enrichErr: string | null; enrichSaving: boolean; saveEnrich: () => void;
}) {
  const {
    onClose, pasteOpen, setPasteOpen, pasteText, setPasteText, pasteMsg, parseAndFill,
    techStack, setTechStack, yourRole, setYourRole, ownership, setOwnership,
    scaleImpact, setScaleImpact, hardestProblem, setHardestProblem,
    editDescription, setEditDescription, enrichErr, enrichSaving, saveEnrich,
  } = props;

  return (
    <>
      <motion.div
        onClick={onClose}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.2 }}
        style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 40 }}
      />
      <motion.div
        className="stack"
        initial={{ x: '100%' }}
        animate={{ x: 0 }}
        exit={{ x: '100%' }}
        transition={{ type: 'spring', damping: 32, stiffness: 300 }}
        style={{
          position: 'fixed', top: 0, right: 0, bottom: 0, width: 'min(520px, 100vw)',
          background: 'var(--paper)', borderLeft: '1px solid var(--ink)', zIndex: 41,
          overflowY: 'auto', padding: 24, boxShadow: '-8px 0 24px rgba(0,0,0,0.15)',
        }}
      >
        <div className="row row--between row--centered">
          <span className="label">ENRICH CONTEXT</span>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onClose}>✕ CLOSE</button>
        </div>

        <div className="panel panel--inset stack-sm" style={{ background: 'var(--paper)' }}>
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
              Paste the Project Context Extractor output to auto-fill all fields below.
            </div>
          )}
          {pasteOpen && (
            <div className="stack-sm" style={{ marginTop: 8 }}>
              <textarea
                className="field__textarea"
                value={pasteText}
                onChange={e => setPasteText(e.target.value)}
                style={{ minHeight: 160 }}
                autoFocus
                placeholder={"Paste the full extractor output here, e.g.\n\n## Tech Stack\nReact, PostgreSQL, AES-256-GCM…\n\n## Your Role\n…"}
              />
              <div className="row">
                <button type="button" className="btn btn--acid btn--sm" onClick={parseAndFill} disabled={!pasteText.trim()}>PARSE &amp; FILL</button>
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setPasteOpen(false)}>CANCEL</button>
              </div>
            </div>
          )}
          {pasteMsg && <div className="label muted" style={{ marginTop: 4 }}>{pasteMsg}</div>}
        </div>

        <label className="field">
          <div className="field__label">Tech stack</div>
          <input className="field__input" value={techStack} onChange={e => setTechStack(e.target.value)}
            placeholder="React, PostgreSQL, FastAPI, Redis, Docker…" />
        </label>
        <label className="field">
          <div className="field__label">Your role</div>
          <input className="field__input" value={yourRole} onChange={e => setYourRole(e.target.value)}
            placeholder="Solo / Lead / Contributor — e.g. 'Led backend, solo on infra'" />
        </label>
        <label className="field">
          <div className="field__label">What you owned end-to-end</div>
          <textarea className="field__textarea" value={ownership} onChange={e => setOwnership(e.target.value)}
            style={{ minHeight: 80 }}
            placeholder="I built the auth system, designed the DB schema, owned the data pipeline from ingestion to API…" />
        </label>
        <label className="field">
          <div className="field__label">Scale & impact</div>
          <input className="field__input" value={scaleImpact} onChange={e => setScaleImpact(e.target.value)}
            placeholder="10k DAU, 200ms p99, reduced costs 40%, 3-person team…" />
        </label>
        <label className="field">
          <div className="field__label">Hardest problem solved</div>
          <textarea className="field__textarea" value={hardestProblem} onChange={e => setHardestProblem(e.target.value)}
            style={{ minHeight: 80 }}
            placeholder="Had to guarantee exactly-once delivery under network partitions…" />
        </label>
        <label className="field">
          <div className="field__label">Description / architecture overview</div>
          <textarea className="field__textarea" value={editDescription} onChange={e => setEditDescription(e.target.value)}
            style={{ minHeight: 80 }}
            placeholder="3–5 sentences: lead each with one subsystem + its technique or number…" />
        </label>
        {enrichErr && <div className="err">{enrichErr}</div>}
        <div className="row">
          <button className="btn btn--acid" onClick={saveEnrich} disabled={enrichSaving}>
            {enrichSaving ? <span className="spinner">SAVING</span> : 'SAVE CONTEXT'}
          </button>
          <button className="btn btn--ghost" onClick={onClose}>CANCEL</button>
        </div>
      </motion.div>
    </>
  );
}
