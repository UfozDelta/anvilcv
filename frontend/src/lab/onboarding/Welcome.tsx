import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { setField, type Field } from './fakeApi';
import { EASE, useRise } from './shared';

/** What a new account sees first: pick a field, then pick how to fill the profile. */

export type Path = 'import' | 'manual' | 'skip';
export const START: Record<Path, string> = { import: '/upload', manual: '/profile', skip: '/projects' };

const FIELDS: { key: Field; name: string; line: string; tools: string }[] = [
  { key: 'software', name: 'Software engineering', line: 'Code projects, repos, internships.', tools: 'Spring Boot · React · Kafka' },
  { key: 'mechanical', name: 'Mechanical engineering', line: 'CAD, FEA, test rigs, design teams.', tools: 'SolidWorks · ANSYS · GD&T' },
];

const PATHS: { key: Path; title: string; sub: string; primary?: boolean }[] = [
  { key: 'import', title: 'Import from my résumé', sub: 'Paste it. We fill in your experiences and projects.', primary: true },
  { key: 'manual', title: 'Set up my profile by hand', sub: 'Name, links and school first. Projects come next.' },
  { key: 'skip', title: 'Skip for now', sub: 'Look around first. A setup checklist stays in the corner.' },
];

function FieldCards({ value, onPick }: { value: Field | null; onPick: (f: Field) => void }) {
  return (
    <div className="ob-fields" role="radiogroup" aria-label="Your field">
      {FIELDS.map((f) => (
        <button key={f.key} type="button" role="radio" aria-checked={value === f.key} className="ob-field" onClick={() => { setField(f.key); onPick(f.key); }}>
          <span className="ob-field__tick" aria-hidden>{value === f.key ? '✓' : ''}</span>
          <strong>{f.name}</strong>
          <span>{f.line}</span>
          <em>{f.tools}</em>
        </button>
      ))}
    </div>
  );
}

function PathList({ onPick }: { onPick: (p: Path) => void }) {
  const rise = useRise();
  return (
    <div className="ap-empty__paths">
      {PATHS.map((p, i) => (
        <motion.button key={p.key} type="button" className={`ap-path${p.primary ? ' ap-path--primary' : ''}`} onClick={() => onPick(p.key)} {...rise(i)}>
          <span className="ap-path__num">0{i + 1}</span>
          <strong>{p.title}</strong>
          <span>{p.sub}</span>
        </motion.button>
      ))}
    </div>
  );
}

/** Autoplay: pick the field, then the chosen road, like a person would. */
function useAutoWelcome(auto: boolean, field: Field, path: Path, onField: (f: Field) => void, onPath: (p: Path) => void) {
  useEffect(() => {
    if (!auto) return;
    const a = window.setTimeout(() => { setField(field); onField(field); }, 1100);
    const b = window.setTimeout(() => onPath(path), 2500);
    return () => { window.clearTimeout(a); window.clearTimeout(b); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);
}

const Heading = ({ children }: { children: React.ReactNode }) => <h1 className="lp-display ob-wel__h">{children}</h1>;

/** Two quiet screens on their own page, before the app. */
export function WelcomePage({ auto, field: autoField, path: autoPath, onDone }: { auto: boolean; field: Field; path: Path; onDone: (p: Path) => void }) {
  const reduce = usePrefersReducedMotion();
  const [field, pick] = useState<Field | null>(null);
  const [step, setStep] = useState(0);
  useAutoWelcome(auto, autoField, autoPath, (f) => { pick(f); window.setTimeout(() => setStep(1), 500); }, onDone);
  const go = (f: Field) => { pick(f); window.setTimeout(() => setStep(1), 220); };
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
              <Heading>Welcome, mara.</Heading>
              <p className="ob-sub">What do you engineer? We tune your bullets and skills to it.</p>
              <FieldCards value={field} onPick={go} />
            </>
          ) : (
            <>
              <Heading>Let’s get your profile set up.</Heading>
              <p className="ob-sub">Importing is fastest. Everything stays editable.</p>
              <PathList onPick={onDone} />
              <button type="button" className="ob-skip" onClick={() => setStep(0)}>← Change field</button>
            </>
          )}
        </motion.section>
      </div>
    </div>
  );
}
