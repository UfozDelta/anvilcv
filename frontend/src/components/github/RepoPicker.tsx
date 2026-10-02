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
    <div className="nf">
      <div className="nf__head">
        <h2 className="ap-label">Link a GitHub repo</h2>
        {onCancel && <button type="button" className="ap-btn ap-btn--ghost nf__x" onClick={onCancel}>Cancel</button>}
      </div>

      {err && <div className="err ap-err" role="alert">{err}</div>}
      {!status && !err && <span className="spinner">LOADING</span>}

      {status && !status.configured && (
        <p className="na-hint">GitHub isn't set up on this server — see README › GitHub App.</p>
      )}

      {status?.configured && !status.connected && (
        <div className="st-gh">
          <span className="st-gh__who">Grant AnvilCV read-only access to the repos you pick.</span>
          <a className="ap-btn ap-btn--acid st-btn" href={`${API_BASE}/api/github/connect`}>Connect GitHub</a>
        </div>
      )}

      {repos && (
        <>
          <label className="pf-field">
            <span className="pf-field__k">Filter</span>
            <span className="pf-field__v">
              <input placeholder={`Filter ${repos.length} repos…`} value={q} onChange={e => setQ(e.target.value)} />
            </span>
          </label>
          <div className="nf-repos">
            {shown.map(r => (
              <button key={r.fullName} type="button" className="nf-repo" onClick={() => link(r)} disabled={busy !== null}>
                <span className="nf-repo__name">
                  <strong>{r.fullName}</strong>
                  {r.description && <span className="ld-desc">{r.description}</span>}
                </span>
                <span className="nf-chip" data-private={r.isPrivate ? '' : undefined}>
                  {busy === r.fullName ? 'Linking…' : r.isPrivate ? 'Private' : 'Public'}
                </span>
              </button>
            ))}
            {shown.length === 0 && (
              <p className="na-hint nf-repos__none">
                No repos. Add some under{' '}
                {status?.manageUrl ? <a href={status.manageUrl} target="_blank" rel="noreferrer">GitHub › app settings</a> : 'GitHub app settings'}.
              </p>
            )}
          </div>
        </>
      )}
    </div>
  );
}
