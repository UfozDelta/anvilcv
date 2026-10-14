import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { usePrefersReducedMotion } from '../landing/useHeroLoop';
import { useAuth } from '../../lib/auth';
import { useField } from '../../lib/field';
import { setRoad, type Road } from './store';
import './onboarding.css';

/** What a new account sees first: pick a field, then pick how to fill the profile. */

const EASE = [0.23, 1, 0.32, 1] as const;
const START: Record<Road, string> = { import: '/upload', manual: '/profile', skip: '/projects' };

const FIELDS = [
  { key: 'software', name: 'Software engineering', line: 'Code projects, repos, internships.', tools: 'Spring Boot · React · Kafka' },
  { key: 'mechanical', name: 'Mechanical engineering (beta)', line: 'CAD, FEA, test rigs, design teams.', tools: 'SolidWorks · ANSYS · GD&T' },
  { key: 'other', name: 'Something else', line: 'Not sure yet. You can change it in Settings.', tools: 'Uses the software wording' },
];

const ROADS: { key: Road; title: string; sub: string; primary?: boolean }[] = [
  { key: 'import', title: 'Import from my résumé', sub: 'Paste it. We fill in your experiences and projects.', primary: true },
  { key: 'manual', title: 'Set up my profile by hand', sub: 'Name, links and school first. Projects come next.' },
  { key: 'skip', title: 'Skip for now', sub: 'Look around first. A setup checklist stays at the top.' },
];

/** Two quiet screens on their own page, before the app. */
export function WelcomePage() {
  const reduce = usePrefersReducedMotion();
  const nav = useNavigate();
  const { username } = useAuth();
  const { field, setField } = useField();
  const [step, setStep] = useState(0);
  const [picked, setPicked] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const pickField = async (key: string) => {
    setErr(null);
    setPicked(key);
    try {
      if (key !== field) await setField(key);
      setStep(1);
    } catch {
      setPicked(null);
      setErr('Could not save your field. Try again.');
    }
  };
  const pickRoad = (road: Road) => { setRoad(road); nav(START[road], { replace: true }); };

  return (
    <div className="ap-root ob-wel">
      <header className="shell ob-wel__top">
        <span className="ob-brand">Anvil // CV</span>
        <span className="ob-wel__step">Step {step + 1} of 2</span>
      </header>
      <div className="shell ob-wel__body">
        <motion.section
          key={step}
          initial={reduce ? { opacity: 0 } : { opacity: 0, x: 14 }}
          animate={reduce ? { opacity: 1 } : { opacity: 1, x: 0 }}
          transition={{ duration: 0.22, ease: EASE }}
        >
          {step === 0 ? (
            <>
              <h1 className="lp-display ob-wel__h">Welcome{username ? `, ${username}` : ''}.</h1>
              <p className="ob-sub">What do you engineer? We tune your bullets and skills to it.</p>
              <div className="ob-fields" role="radiogroup" aria-label="Your field">
                {FIELDS.map((f) => (
                  <button key={f.key} type="button" role="radio" aria-checked={picked === f.key} className="ob-field"
                    disabled={picked !== null} onClick={() => void pickField(f.key)}>
                    <span className="ob-field__tick" aria-hidden>{picked === f.key ? '✓' : ''}</span>
                    <strong>{f.name}</strong>
                    <span>{f.line}</span>
                    <em>{f.tools}</em>
                  </button>
                ))}
              </div>
              {err && <div className="err" role="alert" style={{ marginTop: 16 }}>{err}</div>}
            </>
          ) : (
            <>
              <h1 className="lp-display ob-wel__h">Let’s get your profile set up.</h1>
              <p className="ob-sub">Importing is fastest. Everything stays editable.</p>
              <div className="ap-empty__paths">
                {ROADS.map((r, i) => (
                  <button key={r.key} type="button" className={`ap-path${r.primary ? ' ap-path--primary' : ''}`} onClick={() => pickRoad(r.key)}>
                    <span className="ap-path__num">0{i + 1}</span>
                    <strong>{r.title}</strong>
                    <span>{r.sub}</span>
                  </button>
                ))}
              </div>
              <button type="button" className="ob-skip" onClick={() => { setPicked(null); setStep(0); }}>← Change field</button>
            </>
          )}
        </motion.section>
      </div>
    </div>
  );
}
