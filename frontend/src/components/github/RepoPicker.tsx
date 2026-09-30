import { useEffect, useMemo, useState } from 'react';
import { api, API_BASE, type GithubRepo, type GithubStatus, type Project } from '../../lib/api';

/**
 * Lists the repos granted to the AnvilCV GitHub App and links one — creating a project, or
 * re-pointing `projectId` when given. Falls back to a connect prompt when GitHub isn't linked.
 */
export function RepoPicker({ projectId, onLinked, onCancel }: {
  projectId?: string;
  onLinked: (p: Project) => void;
  onCancel?: () => void;
}) {
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [repos, setRepos] = useState<GithubRepo[] | null>(null);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const s = await api.get<GithubStatus>('/api/github/status');
        setStatus(s);
        if (s.connected) setRepos(await api.get<GithubRepo[]>('/api/github/repos'));
      } catch (e: any) {
        setErr(e?.message || 'Could not reach GitHub');
      }
    })();
  }, []);

  const shown = useMemo(() => {
    const n = q.trim().toLowerCase();
    return (repos ?? []).filter(r => !n || r.fullName.toLowerCase().includes(n));
  }, [repos, q]);

  async function link(r: GithubRepo) {
    setBusy(r.fullName); setErr(null);
    try {
      onLinked(await api.post<Project>('/api/github/link', { projectId, fullName: r.fullName }));
    } catch (e: any) {
      setErr(e?.message || 'Link failed');
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="panel panel--inset stack-sm">
      <div className="row row--between row--centered">
        <span className="label">LINK A GITHUB REPO</span>
        {onCancel && <button className="btn btn--ghost btn--sm" onClick={onCancel}>✕ CANCEL</button>}
      </div>

      {err && <div className="err">{err}</div>}
      {!status && !err && <span className="spinner">LOADING</span>}

      {status && !status.configured && (
        <div className="label muted">GitHub isn't set up on this server — see README › GitHub App.</div>
      )}

      {status?.configured && !status.connected && (
        <div className="row row--centered">
          <span className="label muted" style={{ marginRight: 12 }}>Grant AnvilCV read-only access to the repos you pick.</span>
          <a className="btn btn--acid btn--sm" href={`${API_BASE}/api/github/connect`}>CONNECT GITHUB</a>
        </div>
      )}

      {repos && (
        <>
          <input className="field__input" placeholder={`Filter ${repos.length} repos…`} value={q} onChange={e => setQ(e.target.value)} />
          <div style={{ maxHeight: 320, overflowY: 'auto', borderTop: 'var(--rule-thin)' }}>
            {shown.map(r => (
              <button
                key={r.fullName}
                type="button"
                onClick={() => link(r)}
                disabled={busy !== null}
                style={{ all: 'unset', cursor: 'pointer', display: 'flex', justifyContent: 'space-between', gap: 12, width: '100%', boxSizing: 'border-box', padding: '8px 4px', borderBottom: 'var(--rule-thin)' }}
              >
                <span style={{ minWidth: 0 }}>
                  <span style={{ fontFamily: 'var(--mono)', fontSize: 13 }}>{r.fullName}</span>
                  {r.description && <span className="muted" style={{ display: 'block', fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.description}</span>}
                </span>
                <span className="label muted" style={{ flexShrink: 0 }}>
                  {busy === r.fullName ? 'LINKING…' : r.isPrivate ? 'PRIVATE' : 'PUBLIC'}
                </span>
              </button>
            ))}
            {shown.length === 0 && (
              <div className="label muted" style={{ padding: 12 }}>
                No repos. Add some under{' '}
                {status?.manageUrl ? <a href={status.manageUrl} target="_blank" rel="noreferrer">GitHub › app settings</a> : 'GitHub app settings'}.
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
