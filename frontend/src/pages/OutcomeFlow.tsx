import { useEffect, useState } from 'react';
import { api, type OutcomeHistoryEntry } from '../lib/api';
import { Section } from '../components/Section';
import { OutcomeSankey } from '../components/OutcomeSankey';

export function OutcomeFlow() {
  const [history, setHistory] = useState<OutcomeHistoryEntry[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get<OutcomeHistoryEntry[]>('/api/applications/outcome-history')
      .then(setHistory)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="shell">
      <Section num="05" title="Outcome Flow" count={history.length} />
      {loading ? <span className="spinner">LOADING</span>
        : error ? <div className="muted" style={{ fontSize: 13 }}>Could not load outcome history — {error}</div>
        : <OutcomeSankey history={history} />}
    </div>
  );
}
