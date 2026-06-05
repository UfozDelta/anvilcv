import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type ApplicationSummary } from '../lib/api';
import { Section } from '../components/Section';

const OUTCOMES = ['', 'applied', 'interview', 'offer', 'rejected'];

export function Applications() {
  const [apps, setApps] = useState<ApplicationSummary[]>([]);
  const [outcome, setOutcome] = useState('');
  const [loading, setLoading] = useState(true);

  async function deleteApp(id: string, label: string) {
    if (!window.confirm(`Delete application for "${label}"?`)) return;
    await api.del(`/api/applications/${id}`);
    await load();
  }

  const q = outcome ? `?outcome=${outcome}` : '';

  async function load() {
    setLoading(true);
    try {
      setApps(await api.get<ApplicationSummary[]>(`/api/applications${q}`));
    } finally { setLoading(false); }
  }

  async function exportCsv() {
    const res = await api.fetchRaw(`/api/applications/export${q}`);
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'applications.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  useEffect(() => { load(); }, [outcome]);

  return (
    <div className="shell">
      <Section num="02" title="Applications" count={apps.length} />

      <div className="row" style={{ marginBottom: 20, flexWrap: 'wrap', gap: 6 }}>
        {OUTCOMES.map(o => (
          <button
            key={o || 'all'}
            onClick={() => setOutcome(o)}
            className={`btn btn--sm ${outcome === o ? '' : 'btn--ghost'}`}
            style={outcome === o ? { background: 'var(--ink)', color: 'var(--paper)' } : { border: '2px solid var(--ink)' }}
          >
            {o ? o.toUpperCase() : 'ALL'}
          </button>
        ))}
        <button className="btn btn--sm btn--ghost" onClick={exportCsv} style={{ marginLeft: 'auto', border: '2px solid var(--ink)' }}>
          ↓ CSV
        </button>
      </div>

      {loading ? <span className="spinner">LOADING</span> : (
        <div className="list">
          {apps.map((a, i) => (
            <Link key={a.id} to={`/applications/${a.id}`} className="list__row">
              <div className="list__num">{String(i + 1).padStart(2, '0')}</div>
              <div>
                <h3 className="list__title">{a.company || 'Untitled'}</h3>
                <div className="list__meta">
                  {a.role || 'role?'}
                  &nbsp;·&nbsp;
                  {new Date(a.createdAt).toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' })}
                </div>
              </div>
              <span className={`outcome outcome--${a.outcome}`}>{a.outcome}</span>
              <button
                className="btn btn--ghost btn--sm"
                style={{ marginLeft: 8 }}
                onClick={e => { e.preventDefault(); deleteApp(a.id, a.company || 'Untitled'); }}
              >DELETE</button>
            </Link>
          ))}
          {apps.length === 0 && (
            <div style={{ padding: '40px 0', borderBottom: 'var(--rule-thin)' }} className="editorial muted">
              {outcome ? `No applications with outcome "${outcome}".` : 'No applications yet. Start with 04 — NEW APPLICATION.'}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
