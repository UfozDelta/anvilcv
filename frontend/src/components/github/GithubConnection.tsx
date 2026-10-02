import { useEffect, useRef, useState } from 'react';
import { api, API_BASE, type GithubStatus } from '../../lib/api';

/** Settings section: connect / disconnect the GitHub App. Reads ?github=connected|error from the OAuth bounce. */
export function GithubConnection() {
  const [status, setStatus] = useState<GithubStatus | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const timer = useRef<number>();
  const flash = new URLSearchParams(window.location.search).get('github');

  async function load() {
    try { setStatus(await api.get<GithubStatus>('/api/github/status')); }
    catch (e: any) { setErr(e?.message || 'Could not load GitHub status'); }
  }
  useEffect(() => { load(); }, []);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  // Two clicks, so a stray tap can't cut the link: the first arms it for a few seconds.
  async function disconnect() {
    if (!confirming) {
      setConfirming(true);
      timer.current = window.setTimeout(() => setConfirming(false), 4000);
      return;
    }
    window.clearTimeout(timer.current);
    setConfirming(false);
    try {
      await api.del('/api/github');
      await load();
    } catch (e: any) { setErr(e?.message || 'Could not disconnect GitHub'); }
  }

  return (
    <section className="pf-sec">
      <h2 className="ap-label">GitHub</h2>
      {flash === 'connected' && <p className="na-hint" role="status">GitHub connected.</p>}
      {flash === 'error' && <div className="err ap-err" role="alert">GitHub connection failed — try again.</div>}
      {err && <div className="err ap-err" role="alert">{err}</div>}
      {status && !status.configured && <p className="na-hint">Not set up on this server — see README › GitHub App.</p>}
      {status?.configured && (
        <div className="st-gh">
          {status.connected ? (
            <>
              <span className="st-gh__who"><span className="st-gh__dot" aria-hidden="true" />Connected as <strong>{status.account}</strong></span>
              <span className="st-gh__btns">
                {status.manageUrl && <a className="ap-btn ap-btn--ghost st-btn" href={status.manageUrl} target="_blank" rel="noreferrer">Choose repos ↗</a>}
                <button type="button" className={`ap-btn ap-btn--ghost st-btn${confirming ? ' st-btn--danger' : ''}`} onClick={disconnect}>
                  {confirming ? 'Confirm disconnect' : 'Disconnect'}
                </button>
              </span>
            </>
          ) : (
            <>
              <span className="st-gh__who">Not connected</span>
              <a className="ap-btn ap-btn--acid st-btn" href={`${API_BASE}/api/github/connect`}>Connect GitHub</a>
            </>
          )}
        </div>
      )}
    </section>
  );
}
