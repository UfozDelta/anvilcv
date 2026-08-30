import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Section } from '../components/Section';
import { formStyles as styles } from '../components/form/styles';
import { useAuth } from '../lib/auth';
import { api } from '../lib/api';

type ProviderId = 'gemini' | 'opencode' | 'openai';

interface ProviderView {
  apiKeyMasked: string | null;
  apiKeyFromDb: boolean;
  baseUrl: string | null;
  generateModel: string | null;
  matchModel: string | null;
  cleanJdModel: string | null;
}

interface SettingsView {
  provider: ProviderId;
  secretKeyConfigured: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
  gemini: ProviderView;
  opencode: ProviderView;
  openai: ProviderView;
}

interface TestResult {
  ok: boolean;
  provider: string;
  model: string | null;
  error?: string;
  company?: string;
  role?: string;
  elapsedMs: number;
}

const PROVIDERS: { id: ProviderId; label: string; hasBaseUrl: boolean; hint: string }[] = [
  { id: 'gemini',   label: 'Gemini',       hasBaseUrl: false, hint: 'Google AI Studio key.' },
  { id: 'opencode', label: 'OpenCode Zen', hasBaseUrl: true,  hint: 'Model ids: GET /zen/v1/models — the API wins over the docs page.' },
  { id: 'openai',   label: 'OpenAI-compatible', hasBaseUrl: true, hint: 'Also OpenRouter, Ollama, LM Studio — anything with /chat/completions.' },
];

/** Draft state per provider. apiKey is always blank on load: the server never sends one back. */
type Draft = Record<ProviderId, {
  apiKey: string; baseUrl: string; generateModel: string; matchModel: string; cleanJdModel: string;
}>;

function toDraft(v: SettingsView): Draft {
  const one = (p: ProviderView) => ({
    apiKey: '',
    baseUrl: p.baseUrl ?? '',
    generateModel: p.generateModel ?? '',
    matchModel: p.matchModel ?? '',
    cleanJdModel: p.cleanJdModel ?? '',
  });
  return { gemini: one(v.gemini), opencode: one(v.opencode), openai: one(v.openai) };
}

const mono = { fontFamily: 'var(--mono)' };
const note = { ...mono, fontSize: '0.72rem', color: 'var(--ink-3)', lineHeight: 1.6 };

export function AdminPage() {
  const { isAdmin, loading: authLoading } = useAuth();

  const [view, setView] = useState<SettingsView | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [provider, setProvider] = useState<ProviderId>('gemini');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [test, setTest] = useState<TestResult | null>(null);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = (v: SettingsView) => {
    setView(v);
    setDraft(toDraft(v));
    setProvider(v.provider);
  };

  useEffect(() => {
    if (!isAdmin) return;
    api.get<SettingsView>('/api/admin/llm')
      .then(load)
      .catch(e => setErr(e.message))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  // The nav link is already hidden for non-admins; this covers someone typing the URL.
  // It is a courtesy redirect, not the access control — that lives on the server.
  if (!authLoading && !isAdmin) return <Navigate to="/projects" replace />;
  if (authLoading || loading) return <div className="shell"><span className="spinner">LOADING</span></div>;
  if (!view || !draft) return <div className="shell"><div className="err">{err ?? 'Failed to load settings'}</div></div>;

  const set = (p: ProviderId, field: keyof Draft[ProviderId], value: string) =>
    setDraft(d => d && ({ ...d, [p]: { ...d[p], [field]: value } }));

  const save = async () => {
    setSaving(true); setErr(null); setTest(null);
    try {
      const body = {
        provider,
        gemini: draft.gemini,
        opencode: draft.opencode,
        openai: draft.openai,
      };
      load(await api.put<SettingsView>('/api/admin/llm', body));
      setSavedAt(new Date());
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSaving(false);
    }
  };

  const runTest = async () => {
    setTesting(true); setErr(null); setTest(null);
    try {
      setTest(await api.post<TestResult>(`/api/admin/llm/test?provider=${provider}`));
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="shell" style={{ maxWidth: 720 }}>
      <h1 style={{ ...mono, fontSize: '1rem', marginBottom: 32 }}>07 — LLM PROVIDER (ADMIN)</h1>

      {err && <div className="err" style={{ marginBottom: 16 }}>{err}</div>}

      {!view.secretKeyConfigured && (
        <div className="err" style={{ marginBottom: 16 }}>
          LLM_SECRET_KEY is not set. Provider keys cannot be saved — the app will keep running on
          the keys in the environment. Generate one with <code>openssl rand -base64 32</code> and
          restart.
        </div>
      )}

      <Section num="07.A" title="Active Provider" />
      <div style={styles.section}>
        <div style={styles.row}>
          <span style={styles.label}>Provider</span>
          <select
            value={provider}
            onChange={e => setProvider(e.target.value as ProviderId)}
            style={{ ...mono, fontSize: '0.78rem', padding: '6px 8px' }}
          >
            {PROVIDERS.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}
          </select>
        </div>
        <p style={note}>
          Takes effect on the next pipeline run — no redeploy. A blank field below falls back to
          the value in application.yml / the environment.
        </p>
      </div>

      {PROVIDERS.map((p, i) => {
        const stored = view[p.id];
        const d = draft[p.id];
        return (
          <div key={p.id}>
            <Section num={`07.${String.fromCharCode(66 + i)}`} title={p.label} count={provider === p.id ? 'ACTIVE' : undefined} />
            <div style={styles.section}>
              <div style={styles.row}>
                <span style={styles.label}>API key</span>
                <input
                  type="password"
                  value={d.apiKey}
                  placeholder={stored.apiKeyMasked
                    ? `stored ${stored.apiKeyMasked}${stored.apiKeyFromDb ? '' : ' (from env)'} — leave blank to keep`
                    : 'not set'}
                  onChange={e => set(p.id, 'apiKey', e.target.value)}
                  autoComplete="new-password"
                  style={{ ...mono, fontSize: '0.78rem', flex: 1, padding: '6px 8px' }}
                />
              </div>
              {p.hasBaseUrl && (
                <div style={styles.row}>
                  <span style={styles.label}>Base URL</span>
                  <input
                    value={d.baseUrl}
                    onChange={e => set(p.id, 'baseUrl', e.target.value)}
                    style={{ ...mono, fontSize: '0.78rem', flex: 1, padding: '6px 8px' }}
                  />
                </div>
              )}
              {(['generateModel', 'matchModel', 'cleanJdModel'] as const).map(field => (
                <div key={field} style={styles.row}>
                  <span style={styles.label}>
                    {field === 'generateModel' ? 'Generate' : field === 'matchModel' ? 'Match' : 'Clean JD'}
                  </span>
                  <input
                    value={d[field]}
                    onChange={e => set(p.id, field, e.target.value)}
                    style={{ ...mono, fontSize: '0.78rem', flex: 1, padding: '6px 8px' }}
                  />
                </div>
              ))}
              <p style={note}>{p.hint}</p>
            </div>
          </div>
        );
      })}

      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginTop: 24, flexWrap: 'wrap' }}>
        <button className="btn" onClick={save} disabled={saving}>
          {saving ? 'SAVING…' : 'SAVE'}
        </button>
        <button className="btn btn--ghost" onClick={runTest} disabled={testing}>
          {testing ? 'TESTING…' : 'TEST SAVED SETTINGS'}
        </button>
        {savedAt && (
          <span style={{ ...mono, fontSize: '0.72rem', color: 'var(--ink-3)' }}>
            Saved {savedAt.toLocaleTimeString()}
          </span>
        )}
      </div>

      {test && (
        <div style={{ ...note, marginTop: 16, padding: 12, border: '1px solid var(--rule)' }}>
          {test.ok
            ? `OK — ${test.provider} / ${test.model} answered in ${test.elapsedMs}ms (parsed company "${test.company}", role "${test.role}").`
            : `FAILED — ${test.provider} / ${test.model} after ${test.elapsedMs}ms: ${test.error}`}
        </div>
      )}

      <p style={{ ...note, marginTop: 24 }}>
        Test calls the live provider with the settings already saved, not the unsaved fields above —
        save first, then test. Last change: {view.updatedAt ? new Date(view.updatedAt).toLocaleString() : 'never'}
        {view.updatedBy ? ` by ${view.updatedBy}` : ''}.
      </p>
    </div>
  );
}
