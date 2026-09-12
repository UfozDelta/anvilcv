import { Link, useLocation } from 'react-router-dom';
import '../styles/lab.css';

// Promoted to the shipped pages; re-exported so the lab prototypes keep their imports.
export { RichText } from '../components/RichText';
export { Stat } from '../components/Stat';

const ROUTES = [
  { to: '/lab', label: 'Index' },
  { to: '/lab/list', label: 'List v2' },
  { to: '/lab/detail', label: 'Detail v2' },
  { to: '/lab/rows', label: 'Bullet row A/B' },
  { to: '/lab/projects', label: 'Projects list v2' },
  { to: '/lab/project-detail', label: 'Projects bank v2' },
];

/**
 * Wrapper for every /lab route: the acid bar marking these pages as prototypes,
 * plus the prototype switcher. Deliberately ugly-obvious so a lab screenshot is
 * never mistaken for the shipped product.
 */
export function LabChrome({ title, note, children }: {
  title: string;
  note?: React.ReactNode;
  children: React.ReactNode;
}) {
  const { pathname } = useLocation();
  return (
    <div className="lab">
      <div className="lab-bar">
        <span className="lab-bar__tag">UI Lab</span>
        {ROUTES.map(r => (
          <Link key={r.to} to={r.to} className={pathname === r.to ? 'is-active' : ''}>
            {r.label}
          </Link>
        ))}
        <span style={{ marginLeft: 'auto', fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '0.16em' }}>
          PLACEHOLDER DATA · NO LOGIN · NOTHING SAVES
        </span>
      </div>

      <h1 className="display" style={{ fontSize: 38, lineHeight: 1.05, margin: '0 0 10px' }}>{title}</h1>
      {note && <p className="lab-note">{note}</p>}
      {children}
    </div>
  );
}
