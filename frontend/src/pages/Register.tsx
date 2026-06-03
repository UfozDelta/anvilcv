import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../lib/auth';

const schema = z.object({
  username: z.string().min(3, 'Min 3 characters').max(64, 'Max 64 characters'),
  email: z.email('Invalid email'),
  password: z.string().min(8, 'Min 8 characters'),
  confirm: z.string(),
}).refine(d => d.password === d.confirm, {
  message: 'Passwords do not match',
  path: ['confirm'],
});

type Fields = z.input<typeof schema>;
type FieldErrors = Partial<Record<keyof Fields, string>>;

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [fields, setFields] = useState<Fields>({ username: '', email: '', password: '', confirm: '' });
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
      await register(result.data.username, result.data.email, result.data.password);
      nav('/projects', { replace: true });
    } catch (ex: unknown) {
      const msg = ex instanceof Error ? ex.message : String(ex);
      if (msg.includes('409') || msg.toLowerCase().includes('conflict')) {
        setErr('Username or email already taken.');
      } else if (msg.includes('429')) {
        setErr('Too many attempts. Try again later.');
      } else {
        setErr('Registration failed. Please try again.');
      }
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
            Create your account.
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
            <div className="field__label">Email</div>
            <input
              className="field__input"
              type="email"
              autoComplete="email"
              value={fields.email}
              onChange={set('email')}
            />
            {fieldErrors.email && <div className="err">{fieldErrors.email}</div>}
          </label>
          <label className="field">
            <div className="field__label">Password</div>
            <input
              className="field__input"
              type="password"
              autoComplete="new-password"
              value={fields.password}
              onChange={set('password')}
            />
            {fieldErrors.password && <div className="err">{fieldErrors.password}</div>}
          </label>
          <label className="field">
            <div className="field__label">Confirm Password</div>
            <input
              className="field__input"
              type="password"
              autoComplete="new-password"
              value={fields.confirm}
              onChange={set('confirm')}
            />
            {fieldErrors.confirm && <div className="err">{fieldErrors.confirm}</div>}
          </label>

          {err && <div className="err">{err}</div>}

          <div className="row row--between row--centered" style={{ marginTop: 12 }}>
            <Link to="/login" className="muted" style={{ fontSize: 13 }}>Sign in instead</Link>
            <button className="btn btn--acid" type="submit" disabled={busy}>
              {busy ? <span className="spinner">CREATING</span> : <>CREATE &nbsp;→</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
