import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type ParsedExperience, type ParsedProject } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useField } from '../../lib/field';
import { FieldPicker } from '../FieldPicker';
import { refreshProgress, setRoad } from './store';
import './onboarding.css';

/** The fast first run: paste a résumé, confirm the bullets it holds, land on New application. Their own lines are taken as true. */

interface Line { text: string; on: boolean }
interface Entry { kind: 'EXPERIENCE' | 'PROJECT'; name: string; head: string; sub: string; src: ParsedExperience | ParsedProject; lines: Line[] }

const toLines = (bullets: string[]): Line[] => bullets.map((t) => t.trim()).filter(Boolean).map((text) => ({ text, on: true }));

/** A page needs two bullets under an entry to show it, so a lone bullet would never print. */
const MIN_BULLETS_TO_PRINT = 2;

function toEntries(r: { experiences: ParsedExperience[]; projects: ParsedProject[] }): Entry[] {
  return [
    ...r.experiences.map((e): Entry => ({ kind: 'EXPERIENCE', name: e.name, head: e.title || e.name, sub: [e.company, e.dates].filter(Boolean).join(' · '), src: e, lines: toLines(e.bullets) })),
    ...r.projects.map((p): Entry => ({ kind: 'PROJECT', name: p.name, head: p.name, sub: p.dates ?? '', src: p, lines: toLines(p.bullets) })),
  ].filter((e) => e.lines.length > 0);
}

export function WelcomeFast() {
  const nav = useNavigate();
  const { username } = useAuth();
  const { field, setField } = useField();
  const [text, setText] = useState('');
  const [entries, setEntries] = useState<Entry[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // The guide reads the road from the first screen on, so it can point at the paste box.
  useEffect(() => setRoad('fast'), []);

  const parse = async () => {
    setErr(null); setBusy(true);
    try {
      const found = toEntries(await api.parseResume(text));
      if (found.length === 0) setErr('We found no bullets. Paste the Experience and Projects sections, one bullet per line.');
      else setEntries(found);
    } catch { setErr('Could not read that. Try again.'); }
    finally { setBusy(false); }
  };

  const edit = (ei: number, li: number, patch: Partial<Line>) =>
    setEntries((es) => es && es.map((e, i) => (i === ei ? { ...e, lines: e.lines.map((l, j) => (j === li ? { ...l, ...patch } : l)) } : e)));

  const kept = entries?.reduce((n, e) => n + e.lines.filter((l) => l.on && l.text.trim()).length, 0) ?? 0;

  const confirm = async () => {
    if (!entries) return;
    setErr(null); setBusy(true);
    try {
      const items = entries
        .map((e) => ({ e, bullets: e.lines.filter((l) => l.on && l.text.trim()).map((l) => l.text.trim()) }))
        .filter((x) => x.bullets.length > 0)
        .map(({ e, bullets }) => ({ ...e.src, kind: e.kind, name: e.name, bullets }));
      await api.post('/api/resume/import', { items });
      setRoad('fast');
      // The next page's guide must already know the import is done, or it flashes the old step.
      await refreshProgress();
      nav('/jobs', { replace: true });
    } catch { setErr('Could not save. Try again.'); setBusy(false); }
  };

  return (
    <div className="ap-root ob-wel">
      <header className="shell ob-wel__top">
        <span className="ob-brand">Anvil // CV</span>
        <span className="ob-wel__step">Step {entries ? 2 : 1} of 2</span>
      </header>
      <div className="shell ob-wel__body">
        {!entries ? (
          <section className="fr">
            <h1 className="lp-display ob-wel__h">Welcome{username ? `, ${username}` : ''}.</h1>
            <p className="ob-sub">Paste your résumé. We keep your own words and tailor them to each job.</p>
            <div className="fr-field">
              <span className="ap-label">Your field</span>
              <FieldPicker value={field} onChange={(f) => void setField(f)} />
            </div>
            <textarea className="field__textarea fr-paste" value={text} onChange={(e) => setText(e.target.value)} placeholder="Paste your résumé text here…" aria-label="Résumé text" disabled={busy} />
            {err && <div className="err" role="alert">{err}</div>}
            <div className="fr-acts">
              <button type="button" className="ap-btn ap-btn--acid fr-parse" disabled={busy || !text.trim()} onClick={() => void parse()}>{busy ? 'Reading…' : 'Continue →'}</button>
              <button type="button" className="ob-skip" onClick={() => { setRoad('skip'); nav('/projects', { replace: true }); }}>No résumé? Start from scratch</button>
            </div>
          </section>
        ) : (
          <section className="fr">
            <h1 className="lp-display ob-wel__h">Is this your résumé?</h1>
            <p className="ob-sub">These are your bullets. Fix a line or uncheck one you don’t want. We treat what stays as true.</p>
            {entries.map((e, ei) => (
              <div key={ei} className="fr-entry">
                <h2 className="fr-entry__h"><strong>{e.head}</strong>{e.sub && <span>{e.sub}</span>}</h2>
                {e.lines.filter((l) => l.on && l.text.trim()).length < MIN_BULLETS_TO_PRINT && (
                  <p className="fr-entry__note">A page needs at least two bullets under an entry to show it.</p>
                )}
                <ul className="fr-lines">
                  {e.lines.map((l, li) => (
                    <li key={li} data-off={!l.on || undefined}>
                      <input type="checkbox" checked={l.on} aria-label="Keep this bullet" onChange={(ev) => edit(ei, li, { on: ev.target.checked })} />
                      <textarea className="fr-line" rows={2} value={l.text} aria-label="Bullet text" onChange={(ev) => edit(ei, li, { text: ev.target.value })} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            {err && <div className="err" role="alert">{err}</div>}
            <div className="fr-acts">
              <button type="button" className="ap-btn ap-btn--acid fr-go" disabled={busy || kept === 0} onClick={() => void confirm()}>{busy ? 'Saving…' : `Looks right, ${kept} bullet${kept === 1 ? '' : 's'} →`}</button>
              <button type="button" className="ob-skip" onClick={() => setEntries(null)}>← Paste again</button>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
