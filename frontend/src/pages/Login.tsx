import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [u, setU] = useState('');
  const [p, setP] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      await login(u, p);
      nav('/projects', { replace: true });
    } catch {
      setErr('Invalid credentials.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center-page">
      <div style={{ width: 460, maxWidth: '100%' }}>
        <div style={{ marginBottom: 28, borderBottom: '3px solid var(--ink)', paddingBottom: 14 }}>
          <h1 className="display" style={{ fontSize: 56, margin: '0 0 6px' }}>
            Resu<span style={{ fontFamily: 'var(--mono)', fontStyle: 'normal', fontWeight: 700, fontSize: '0.5em' }}> // </span>Forge
          </h1>
          <div className="editorial muted" style={{ fontSize: 16, marginTop: 8 }}>
            Tailored résumés, powered by AI.
          </div>
        </div>

        <form onSubmit={submit} className="stack">
          <label className="field">
            <div className="field__label">Username</div>
            <input
              className="field__input"
              autoFocus
              autoComplete="username"
              value={u}
              onChange={e => setU(e.target.value)}
            />
          </label>
          <label className="field">
            <div className="field__label">Password</div>
            <input
              className="field__input"
              type="password"
              autoComplete="current-password"
              value={p}
              onChange={e => setP(e.target.value)}
            />
          </label>

          {err && <div className="err">{err}</div>}

          <div className="row row--between row--centered" style={{ marginTop: 12 }}>
            <span />
            <button className="btn btn--acid" type="submit" disabled={busy}>
              {busy ? <span className="spinner">SIGNING IN</span> : <>ENTER &nbsp;→</>}
            </button>
          </div>
        </form>

      </div>
    </div>
  );
}
