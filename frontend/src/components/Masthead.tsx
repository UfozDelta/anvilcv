import { Link, NavLink } from 'react-router-dom';
import { useState, useEffect, useRef } from 'react';
import { useAuth } from '../lib/auth';

export function Masthead() {
  const { username, logout } = useAuth();
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
                  <Link to="/upload" onClick={() => setSettingsOpen(false)}>Upload Resume</Link>
                  <Link to="/docs" onClick={() => setSettingsOpen(false)}>Docs</Link>
                </div>
              )}
            </div>
          </span>
        </div>
      </header>
      <nav className="nav shell">
        <NavLink to="/profile"      className={({ isActive }) => isActive ? 'active' : ''}>00 — PROFILE</NavLink>
        <NavLink to="/projects"     className={({ isActive }) => isActive ? 'active' : ''}>01 — PROJECTS</NavLink>
        <NavLink to="/experiences"  className={({ isActive }) => isActive ? 'active' : ''}>02 — EXPERIENCES</NavLink>
        <NavLink to="/applications" className={({ isActive }) => isActive ? 'active' : ''}>03 — APPLICATIONS</NavLink>
        <NavLink to="/new"          className={({ isActive }) => isActive ? 'active' : ''}>04 — NEW APPLICATION</NavLink>
        <NavLink to="/settings"     className={({ isActive }) => isActive ? 'active' : ''}>05 — SETTINGS</NavLink>
      </nav>
    </>
  );
}
