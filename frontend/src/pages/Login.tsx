import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../lib/auth';

const schema = z.object({
  username: z.string().min(1, 'Username required'),
  password: z.string().min(1, 'Password required'),
});

type Fields = z.infer<typeof schema>;
type FieldErrors = Partial<Record<keyof Fields, string>>;

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [fields, setFields] = useState<Fields>({ username: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function set(k: keyof Fields) {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      setFields(f => ({ ...f, [k]: e.target.value }));
      setFieldErrors(fe => ({ ...fe, [k]: undefined }));
    };
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    const result = schema.safeParse(fields);
    if (!result.success) {
      const errs: FieldErrors = {};
      for (const issue of result.error.issues) {
        errs[issue.path[0] as keyof Fields] = issue.message;
      }
      setFieldErrors(errs);
      return;
    }
    setBusy(true);
    try {
      await login(result.data.username, result.data.password);
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
            Anvil<span style={{ fontFamily: 'var(--mono)', fontStyle: 'normal', fontWeight: 700, fontSize: '0.5em' }}> // </span>CV
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
              value={fields.username}
              onChange={set('username')}
            />
            {fieldErrors.username && <div className="err">{fieldErrors.username}</div>}
          </label>
          <label className="field">
            <div className="field__label">Password</div>
            <input
              className="field__input"
              type="password"
              autoComplete="current-password"
              value={fields.password}
              onChange={set('password')}
            />
            {fieldErrors.password && <div className="err">{fieldErrors.password}</div>}
          </label>

          {err && <div className="err">{err}</div>}

          <div className="row row--between row--centered" style={{ marginTop: 12 }}>
            <Link to="/register" className="muted" style={{ fontSize: 13 }}>Create account</Link>
            <button className="btn btn--acid" type="submit" disabled={busy}>
              {busy ? <span className="spinner">SIGNING IN</span> : <>ENTER &nbsp;→</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
