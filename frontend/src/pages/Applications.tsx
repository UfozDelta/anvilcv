import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type ApplicationSummary } from '../lib/api';
import { Section } from '../components/Section';

const OUTCOMES = ['', 'applied', 'interview', 'offer', 'rejected'];

export function Applications() {
  const [apps, setApps] = useState<ApplicationSummary[]>([]);
  const [outcome, setOutcome] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const q = outcome ? `?outcome=${outcome}` : '';
      setApps(await api.get<ApplicationSummary[]>(`/api/applications${q}`));
    } finally { setLoading(false); }
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
