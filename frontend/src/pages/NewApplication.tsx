import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { Section } from '../components/Section';
import { EventStream } from '../components/EventStream';

const EMPHASES = [
  { value: 'backend',    label: 'Backend' },
  { value: 'frontend',   label: 'Frontend' },
  { value: 'ml',         label: 'ML / AI' },
  { value: 'generalist', label: 'Generalist' },
];

export function NewApplication() {
  const nav = useNavigate();
  const [jdText, setJdText] = useState('');
  const [jdUrl, setJdUrl] = useState('');
  const [roleEmphasis, setRoleEmphasis] = useState('backend');
  const [streaming, setStreaming] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    setStreaming(true);
  }

  return (
    <div className="shell">
      <Section num="03" title="New Application" />

      <div className="row" style={{ marginBottom: 28, alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <div className="display" style={{ fontSize: 40, lineHeight: 1, marginBottom: 12 }}>
            Paste the job. <br />
            <span style={{ fontStyle: 'normal', fontWeight: 400, fontSize: '0.6em', fontFamily: 'var(--mono)', textTransform: 'uppercase', letterSpacing: '0.18em', color: 'var(--muted)' }}>
              AI cleans · ranks · drafts · renders
            </span>
          </div>
        </div>
      </div>

      <form onSubmit={submit} className="stack">
        <label className="field">
          <div className="field__label">Job description (text)</div>
          <textarea
            className="field__textarea"
            style={{ minHeight: 240 }}
            value={jdText}
            onChange={e => setJdText(e.target.value)}
            placeholder="Paste the full posting here — responsibilities, requirements, tech stack."
          />
        </label>

        <div className="row row--centered muted label" style={{ justifyContent: 'center', margin: '4px 0' }}>
          — OR —
        </div>

        <label className="field">
          <div className="field__label">JD URL</div>
          <input
            className="field__input"
            value={jdUrl}
            onChange={e => setJdUrl(e.target.value)}
            placeholder="https://company.com/jobs/backend-engineer"
          />
          <div className="field__hint">We'll fetch & strip it. Some sites block crawlers; paste the text if it fails.</div>
        </label>

        <div>
          <div className="field__label" style={{ marginBottom: 10 }}>Role emphasis</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {EMPHASES.map(o => (
              <button
                type="button"
                key={o.value}
                className={`btn btn--sm ${roleEmphasis === o.value ? '' : 'btn--ghost'}`}
                style={roleEmphasis === o.value ? { background: 'var(--ink)', color: 'var(--paper)' } : { border: '2px solid var(--ink)' }}
                onClick={() => setRoleEmphasis(o.value)}
              >
                {o.label.toUpperCase()}
              </button>
            ))}
          </div>
        </div>

        <div className="row row--between row--centered" style={{ marginTop: 12 }}>
          <span className="label muted">SYNC · ~15-25S · MODAL WILL OPEN</span>
          <button type="submit" className="btn btn--acid" disabled={streaming}>
            {streaming ? <span className="spinner">TAILORING</span> : <>RUN PIPELINE &nbsp;→</>}
          </button>
        </div>
      </form>

      {streaming && (
        <EventStream
          jdText={jdText}
          jdUrl={jdUrl}
          roleEmphasis={roleEmphasis}
          onDone={appId => nav(`/applications/${appId}`)}
          onClose={() => setStreaming(false)}
        />
      )}
    </div>
  );
}
