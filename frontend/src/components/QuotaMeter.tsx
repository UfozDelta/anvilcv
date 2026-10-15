import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { QUOTA_EVENT } from '../lib/quota';

interface Meter { used: number; limit: number | null }
interface Entries { used: number; cap: number }
interface Usage {
  plan: string;
  exempt: boolean;
  tailors: Meter;
  generations: Meter;
  projects: Entries | null;
  experiences: Entries | null;
  resetsAt: string | null;
}

/** What is left on the user's plan. Renders nothing when exempt or when the meter cannot load. */
export function QuotaMeter() {
  const [u, setU] = useState<Usage | null>(null);
  useEffect(() => {
    const load = () => { api.get<Usage>('/api/quota').then(setU).catch(() => setU(null)); };
    load();
    window.addEventListener(QUOTA_EVENT, load);
    return () => window.removeEventListener(QUOTA_EVENT, load);
  }, []);
  if (!u || u.exempt || u.tailors.limit == null) return null;

  const left = Math.max(0, u.tailors.limit - u.tailors.used);
  const resets = u.resetsAt ? new Date(u.resetsAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : null;
  const free = u.plan === 'free';
  return (
    <p className="qm" data-out={left === 0 || undefined} role="status">
      <strong>{left} of {u.tailors.limit} tailors left</strong>
      {resets && <span> · resets {resets}</span>}
      {free && u.projects && u.experiences && (
        <span> · bullets written for {u.projects.used}/{u.projects.cap} projects, {u.experiences.used}/{u.experiences.cap} experience</span>
      )}
      {left === 0 && <> <Link to="/pricing">Upgrade</Link></>}
    </p>
  );
}
