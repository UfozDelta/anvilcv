import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { PageTitle } from '../components/ledger/shared';
import { EventStream } from '../components/EventStream';
import { CATEGORIES } from '../lib/api';

// Auto ('') sends no roleEmphasis, so the lens is inferred from the JD. The rest are manual
// overrides, derived from CATEGORIES so the slugs match the lenses the bank was generated under;
// "generalist" means no angle preference.
const EMPHASES = [
  { value: '', label: 'Auto' },
  ...CATEGORIES.map(c => ({ value: c.slug, label: c.label })),
];

const looksLikeUrl = (s: string) => /^https?:\/\/\S+\.\S+/i.test(s.trim());

export function NewApplication() {
  const nav = useNavigate();
  const [jdText, setJdText] = useState('');
  // Prefilled when arriving from a posting's Tailor button on /jobs.
  const [params] = useSearchParams();
  const [jdUrl, setJdUrl] = useState(params.get('jdUrl') ?? '');
  const [roleEmphasis, setRoleEmphasis] = useState('');
  const [includeCoverLetter, setIncludeCoverLetter] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [touched, setTouched] = useState(false);

  const urlBad = jdUrl.trim() !== '' && !looksLikeUrl(jdUrl);
  const ready = (jdText.trim().length > 0 || looksLikeUrl(jdUrl)) && !urlBad;
  const missing = touched && !ready;

  function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (ready) setStreaming(true);
  }

  return (
    <div className="shell ap-page na-page">
      <PageTitle title="New application" />

      <form className="na-form" onSubmit={submit} aria-busy={streaming}>
        <fieldset className="na-sec" disabled={streaming}>
          <legend className="sr-only">The job</legend>
          <textarea
            className="na-input na-text"
            value={jdText}
            onChange={e => setJdText(e.target.value)}
            placeholder="Paste the full posting"
            aria-label="Job description"
            aria-invalid={missing || undefined}
            aria-describedby={missing || urlBad ? 'na-err' : undefined}
          />
          <input
            className="na-input na-url"
            inputMode="url"
            autoComplete="off"
            value={jdUrl}
            onChange={e => setJdUrl(e.target.value)}
            placeholder="…or paste a job URL"
            aria-label="Job URL"
            aria-invalid={urlBad || missing || undefined}
            aria-describedby={missing || urlBad ? 'na-err' : undefined}
          />
          {(urlBad || missing) && (
            <p id="na-err" className="na-hint na-hint--err" role="alert">
              {urlBad ? 'URL needs to start with https://' : 'Add the posting text or a URL first.'}
            </p>
          )}
        </fieldset>

        <fieldset className="na-sec" disabled={streaming}>
          <legend className="ap-label">Emphasis</legend>
          <div className="na-lenses" role="group" aria-label="Role emphasis">
            {EMPHASES.map(o => (
              <button key={o.value} type="button" aria-pressed={roleEmphasis === o.value} onClick={() => setRoleEmphasis(o.value)}>{o.label}</button>
            ))}
          </div>
        </fieldset>

        <div className="na-submit">
          <label className="na-check">
            <input type="checkbox" checked={includeCoverLetter} disabled={streaming} onChange={e => setIncludeCoverLetter(e.target.checked)} />
            <span>Cover letter</span>
          </label>
          <button type="submit" className="ap-btn ap-btn--acid" disabled={streaming}>{streaming ? 'Tailoring…' : 'Run pipeline →'}</button>
        </div>
      </form>

      {streaming && (
        <EventStream
          submitUrl="/api/applications/submit"
          submitBody={{
            jdText: jdText.trim() || undefined,
            jdUrl: jdUrl.trim() || undefined,
            roleEmphasis: roleEmphasis || undefined,
            includeCoverLetter,
          }}
          pollUrl={jobId => `/api/applications/jobs/${jobId}/progress`}
          onDone={appId => nav(`/applications/${appId}`)}
          onClose={() => setStreaming(false)}
          title="TAILORING RESUME..."
        />
      )}
    </div>
  );
}
