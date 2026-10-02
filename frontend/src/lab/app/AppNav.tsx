import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { AccountMenu, AppNav, Brand, NavEnvContext, NavItem, NewButton, PRIMARY, PROFILE, ACCOUNT, type Item, type NavEnv } from '../../components/ledger/AppNav';
import { inert } from './shared';

// Prototype nav. The real nav lives in components/ledger/AppNav; here it is pointed at the lab
// prototypes (where one exists the link goes there, the rest stay inert) with a demo user.
// "Current" comes from the lab route (falls back to Projects, e.g. /lab/nav).
const CURRENT = '/projects';
const LAB: Record<string, string> = {
  '/profile': '/lab/profile',
  '/projects': '/lab/app',
  '/experiences': '/lab/experiences',
  '/applications': '/lab/applications',
  '/new': '/lab/new-application',
  '/jobs': '/lab/jobs',
  '/flow': '/lab/flow',
  '/settings': '/lab/settings',
};
const REAL_BY_LAB = Object.fromEntries(Object.entries(LAB).map(([real, lab]) => [lab, real]));

type Zone = 'BUILD' | 'APPLY';
const ZONES: Record<Zone, Item[]> = {
  BUILD: [PROFILE, { to: '/projects', label: 'Projects' }, { to: '/experiences', label: 'Experiences' }],
  APPLY: [{ to: '/applications', label: 'Applications' }, { to: '/jobs', label: 'Jobs' }, { to: '/flow', label: 'Outcome flow' }],
};
const DOCK: Item[] = PRIMARY.filter((i) => i.to !== PROFILE.to);

function useCurrent() {
  const { pathname } = useLocation();
  return REAL_BY_LAB[pathname] ?? CURRENT;
}

function LabEnv({ children }: { children: React.ReactNode }) {
  const current = useCurrent();
  const env: NavEnv = { current, href: (to) => LAB[to] ?? null, brandTo: '/lab', user: 'maks', isAdmin: true, logout: () => {} };
  return <NavEnvContext.Provider value={env}>{children}</NavEnvContext.Provider>;
}

/** Strip — the real nav under the lab's link targets. */
export function NavStrip() {
  return <LabEnv><AppNav /></LabEnv>;
}

/** Two-zone — information architecture: BUILD your bank, APPLY with it; "+ New" at the seam. */
export function NavZones() {
  return <LabEnv><NavZonesInner /></LabEnv>;
}
function NavZonesInner() {
  const current = useCurrent();
  const [zone, setZone] = useState<Zone>(() => (ZONES.APPLY.some((i) => i.to === current) ? 'APPLY' : 'BUILD'));
  const rest = ACCOUNT.filter((a) => a.to !== '/flow');
  return (
    <header className="an-bar an-bar--zones">
      <div className="shell an-bar__inner">
        <Brand />
        <nav className="an-zones" aria-label="Main">
          {(['BUILD', 'APPLY'] as const).map((z, zi) => (
            <div key={z} className="an-zone" data-zone={z}>
              {zi === 1 && <NewButton current={current} />}
              <span className="an-zone__label">{z}</span>
              {ZONES[z].map((i) => <NavItem key={i.to} item={i} current={current} upper />)}
            </div>
          ))}
        </nav>
        <AccountMenu items={rest} current={current} />
      </div>
      {/* Phone: one segmented control picks the zone; its three links sit under it. */}
      <div className="an-seg shell">
        <div className="an-seg__ctl" role="group" aria-label="Section">
          {(['BUILD', 'APPLY'] as const).map((z) => (
            <button key={z} type="button" aria-pressed={zone === z} onClick={() => setZone(z)}>{z}</button>
          ))}
          <NewButton compact current={current} />
        </div>
        <div className="an-seg__links">
          {ZONES[zone].map((i) => <NavItem key={i.to} item={i} current={current} />)}
        </div>
      </div>
    </header>
  );
}

/** Dock — interaction model: slim top bar; on phones the main places become a bottom tab bar. */
export function NavDock() {
  return <LabEnv><NavDockInner /></LabEnv>;
}
function NavDockInner() {
  const current = useCurrent();
  return (
    <>
      <header className="an-bar an-bar--dock">
        <div className="shell an-bar__inner">
          <Brand />
          <nav className="an-links" aria-label="Main">
            {PRIMARY.map((i) => <NavItem key={i.to} item={i} current={current} upper />)}
          </nav>
          <div className="an-right">
            <NewButton current={current} />
            <AccountMenu items={[PROFILE, ...ACCOUNT]} current={current} />
          </div>
        </div>
      </header>
      <nav className="an-dock" aria-label="Main">
        {DOCK.slice(0, 2).map((i) => <DockTab key={i.to} item={i} current={current} />)}
        <Link to={LAB['/new']} className="an-dock__new" aria-label="New application">+</Link>
        {DOCK.slice(2).map((i) => <DockTab key={i.to} item={i} current={current} />)}
      </nav>
    </>
  );
}

function DockTab({ item, current }: { item: Item; current: string }) {
  const aria = item.to === current ? 'page' : undefined;
  const body = (<><span className="an-dock__dot" aria-hidden="true" />{item.label}</>);
  const lab = LAB[item.to];
  return lab
    ? <Link to={lab} className="an-dock__tab" aria-current={aria}>{body}</Link>
    : <a href="#" onClick={inert} className="an-dock__tab" aria-current={aria}>{body}</a>;
}
