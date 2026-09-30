import { useEffect, useState } from 'react';
import { api, API_BASE, type GithubStatus } from '../../lib/api';

/** Settings panel: connect / disconnect the GitHub App. Reads ?github=connected|error from the OAuth bounce. */
export function GithubConnection() {
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const flash = new URLSearchParams(window.location.search).get('github');

  async function load() {
    try { setStatus(await api.get<GithubStatus>('/api/github/status')); }
    catch (e: any) { setErr(e?.message || 'Could not load GitHub status'); }
  }
  useEffect(() => { load(); }, []);

  async function disconnect() {
    if (!confirm('Disconnect GitHub? Linked projects keep their context but can no longer read the repo.')) return;
    await api.del('/api/github');
    await load();
  }

  const mono = { fontFamily: 'var(--mono)', fontSize: '0.75rem', color: 'var(--ink-3)', lineHeight: 1.6 } as const;

  return (
    <div style={{ marginTop: 20 }}>
      <span style={{ fontFamily: 'var(--mono)', fontSize: '0.85rem', fontWeight: 600 }}>GitHub</span>
      <p style={{ ...mono, marginTop: 6 }}>
        Read-only access to the repos you pick. AnvilCV explores them itself to fill project context
        and trace every bullet back to the code it came from. No token is stored.
      </p>
      {flash === 'connected' && <div className="label" style={{ marginBottom: 8 }}>✓ GITHUB CONNECTED</div>}
      {flash === 'error' && <div className="err" style={{ marginBottom: 8 }}>GitHub connection failed — try again.</div>}
      {err && <div className="err">{err}</div>}

      {status && !status.configured && <p style={mono}>Not set up on this server — see README › GitHub App.</p>}
      {status?.configured && !status.connected && (
        <a className="btn btn--acid" href={`${API_BASE}/api/github/connect`}>CONNECT GITHUB</a>
      )}
      {status?.connected && (
        <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <span style={mono}>Connected as <strong>{status.account}</strong></span>
          {status.manageUrl && <a className="btn btn--ghost btn--sm" href={status.manageUrl} target="_blank" rel="noreferrer">CHOOSE REPOS / UNINSTALL ↗</a>}
          <button className="btn btn--ghost btn--sm" onClick={disconnect}>DISCONNECT</button>
        </div>
      )}
    </div>
  );
}
