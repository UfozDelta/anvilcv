import { Link, useLocation } from 'react-router-dom';
import { useState, useEffect, useRef } from 'react';
import { useAuth } from '../lib/auth';

type NavItem = { to: string; label: string };

// Numbering is derived from array position, not typed by hand — reordering or splitting
// entries can never leave a gap or a duplicate number behind.
const NAV: NavItem[] = [
  { to: '/profile', label: 'Profile' },
  { to: '/projects', label: 'Projects' },
  { to: '/experiences', label: 'Experiences' },
  { to: '/applications', label: 'Applications' },
  { to: '/new', label: 'New application' },
  { to: '/flow', label: 'Outcome flow' },
  { to: '/settings', label: 'Settings' },
];
const ADMIN_ITEM: NavItem = { to: '/admin', label: 'Admin' };

function isActive(item: NavItem, pathname: string) {
  return pathname === item.to || pathname.startsWith(item.to + '/');
}

export function Masthead() {
  const { username, isAdmin, logout } = useAuth();
  const location = useLocation();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);

  const today = new Date().toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: '2-digit'
  }).toUpperCase();

  useEffect(() => {
    if (!settingsOpen) return;
    function handleClick(e: MouseEvent) {
      if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
        setSettingsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [settingsOpen]);

  const items = isAdmin ? [...NAV, ADMIN_ITEM] : NAV;

  return (
    <>
      <header className="masthead shell">
        <Link to="/" className="masthead__brand" style={{ textDecoration: 'none', color: 'var(--ink)' }}>
          Anvil<span style={{ fontStyle: 'normal', fontFamily: 'var(--mono)', fontWeight: 700, fontSize: '0.55em' }}> // </span>CV
        </Link>
        <div className="masthead__rule" />
        <div className="masthead__meta">
          VOL.0 — {today}
          <br />
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
            {username ? <>
              {username} · <a href="#" onClick={(e) => { e.preventDefault(); logout(); }}>LOG OUT</a>
            </> : 'GUEST'}
            <div className="masthead__settings" ref={settingsRef}>
              <button
                className="masthead__settings-btn"
                onClick={() => setSettingsOpen(o => !o)}
                aria-label="Settings menu"
              >⚙</button>
              {settingsOpen && (
                <div className="masthead__settings-menu">
                  <div className="label muted" style={{ padding: '4px 10px', fontSize: 9.5 }}>ACCOUNT</div>
                  <a href="#" onClick={(e) => { e.preventDefault(); setSettingsOpen(false); logout(); }}>Log out</a>
                  <div className="label muted" style={{ padding: '4px 10px', fontSize: 9.5, marginTop: 4 }}>TOOLS</div>
                  <Link to="/upload" onClick={() => setSettingsOpen(false)}>Upload Resume</Link>
                  <Link to="/docs" onClick={() => setSettingsOpen(false)}>Docs</Link>
                </div>
              )}
            </div>
          </span>
        </div>
      </header>
      <nav className="nav shell">
        {items.map((item, i) => (
          <Link key={item.to} to={item.to} className={isActive(item, location.pathname) ? 'active' : ''}>
            {String(i).padStart(2, '0')} — {item.label.toUpperCase()}
          </Link>
        ))}
      </nav>
    </>
  );
}
