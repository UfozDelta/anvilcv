import { useState } from 'react';
import { LabChrome } from './LabChrome';

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

function isActive(item: NavItem, current: string) {
  return current === item.to || current.startsWith(item.to + '/');
}

function LabNav({ current, showAdmin }: { current: string; showAdmin: boolean }) {
  const items = showAdmin ? [...NAV, ADMIN_ITEM] : NAV;
  return (
    <nav className="nav shell">
      {items.map((item, i) => (
        <a
          key={item.to}
          href="#"
          onClick={e => e.preventDefault()}
          className={isActive(item, current) ? 'active' : ''}
        >
          {String(i).padStart(2, '0')} — {item.label.toUpperCase()}
        </a>
      ))}
    </nav>
  );
}

function LabSettingsMenu({ open, onToggle }: { open: boolean; onToggle: () => void }) {
  return (
    <div className="masthead__settings">
      <button className="masthead__settings-btn" onClick={onToggle} aria-label="Settings menu">⚙</button>
      {open && (
        <div className="masthead__settings-menu">
          <div className="label muted" style={{ padding: '4px 10px', fontSize: 9.5 }}>ACCOUNT</div>
          <a href="#" onClick={e => e.preventDefault()}>Log out</a>
          <div className="label muted" style={{ padding: '4px 10px', fontSize: 9.5, marginTop: 4 }}>TOOLS</div>
          <a href="#" onClick={e => e.preventDefault()}>Upload Resume</a>
          <a href="#" onClick={e => e.preventDefault()}>Docs</a>
        </div>
      )}
    </div>
  );
}

function LabMastheadDemo({ current }: { current: string }) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const today = new Date().toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: '2-digit',
  }).toUpperCase();

  return (
    <div style={{ border: '2px solid var(--ink)' }}>
      <header className="masthead shell">
        <span className="masthead__brand">
          Anvil<span style={{ fontStyle: 'normal', fontFamily: 'var(--mono)', fontWeight: 700, fontSize: '0.55em' }}> // </span>CV
        </span>
        <div className="masthead__rule" />
        <div className="masthead__meta">
          VOL.0 — {today}
          <br />
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
            demo-user · <a href="#" onClick={e => e.preventDefault()}>LOG OUT</a>
            <LabSettingsMenu open={settingsOpen} onToggle={() => setSettingsOpen(o => !o)} />
          </span>
        </div>
      </header>
      <LabNav current={current} showAdmin={false} />
    </div>
  );
}

export function LabMasthead() {
  return (
    <LabChrome
      title="Masthead — nav rework"
      note={
        <>
          Prompted by a real bug: merging the Projects and Experiences pages behind one nav item
          left them keeping their own in-page kind-switch too — the same choice offered twice,
          out of sync. Decision landed on keeping Projects and Experiences as separate nav items
          and separate routes, so this pass instead fixes what actually made the merge fragile:
          nav numbering came from a typed-in digit per link, and one link had an ad-hoc{' '}
          <code>pathname.startsWith(...)</code> check nothing else used. Numbering here comes
          from array position instead, so a future add or reorder can't leave a gap or a
          duplicate. The settings dropdown also gets its two unrelated jobs — signing out vs.
          opening a tool — visually split instead of stacked as one flat list.
        </>
      }
    >
      <div className="eyebrow" style={{ marginBottom: 8 }}>On /projects</div>
      <LabMastheadDemo current="/projects" />

      <div className="eyebrow" style={{ margin: '28px 0 8px' }}>On /experiences/3f0a</div>
      <LabMastheadDemo current="/experiences/3f0a" />
    </LabChrome>
  );
}
