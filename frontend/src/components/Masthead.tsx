import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../lib/auth';

export function Masthead() {
  const { username, logout } = useAuth();
  const today = new Date().toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: '2-digit'
  }).toUpperCase();

  return (
    <>
      <header className="masthead shell">
        <Link to="/" className="masthead__brand" style={{ textDecoration: 'none', color: 'var(--ink)' }}>
          Resume<span style={{ fontStyle: 'normal', fontFamily: 'var(--mono)', fontWeight: 700, fontSize: '0.55em' }}> // </span>Pipeline
        </Link>
        <div className="masthead__rule" />
        <div className="masthead__meta">
          VOL.0 — {today}
          <br />
          {username ? <>
            {username} · <a href="#" onClick={(e) => { e.preventDefault(); logout(); }}>LOG OUT</a>
          </> : 'GUEST'}
        </div>
      </header>
      <nav className="nav shell">
        <NavLink to="/profile"      className={({ isActive }) => isActive ? 'active' : ''}>00 — PROFILE</NavLink>
        <NavLink to="/projects"     className={({ isActive }) => isActive ? 'active' : ''}>01 — PROJECTS</NavLink>
        <NavLink to="/experiences"  className={({ isActive }) => isActive ? 'active' : ''}>02 — EXPERIENCES</NavLink>
        <NavLink to="/applications" className={({ isActive }) => isActive ? 'active' : ''}>03 — APPLICATIONS</NavLink>
        <NavLink to="/new"          className={({ isActive }) => isActive ? 'active' : ''}>04 — NEW APPLICATION</NavLink>
      </nav>
    </>
  );
}
