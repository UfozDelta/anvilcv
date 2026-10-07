import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { useMenu } from './shared';

export type Item = { to: string; label: string; admin?: boolean };

export const PROFILE: Item = { to: '/profile', label: 'Profile' };
export const PRIMARY: Item[] = [
  PROFILE,
  { to: '/projects', label: 'Projects' },
  { to: '/experiences', label: 'Experiences' },
  { to: '/applications', label: 'Applications' },
  { to: '/jobs', label: 'Jobs' },
];
export const ACCOUNT: Item[] = [
  { to: '/flow', label: 'Outcome flow' },
  { to: '/settings', label: 'Settings' },
  { to: '/upload', label: 'Upload résumé' },
  { to: '/admin', label: 'Admin', admin: true },
];
const NEW: Item = { to: '/new', label: 'New application' };
/** What a signed-out visitor gets: only places that work without an account. */
export const GUEST: Item[] = [
  { to: '/jobs', label: 'Jobs' },
  { to: '/pricing', label: 'Pricing' },
];

/**
 * What the nav needs from its surroundings. The real app derives it from the router and the
 * session; the /lab prototypes override it (their own link targets, a demo user).
 */
export type NavEnv = {
  /** The real path of the page being shown, or '' when none of the nav's paths match. */
  current: string;
  /** Where a nav path goes; null renders an inert link. */
  href: (to: string) => string | null;
  brandTo: string;
  /** Null for a guest. */
  user: string | null;
  isAdmin: boolean;
  logout: () => void;
};

export const NavEnvContext = createContext<NavEnv | null>(null);

const KNOWN = [...PRIMARY, NEW, ...ACCOUNT, ...GUEST].map((i) => i.to);
/** Prefix match, so /projects/:id still lights Projects. */
const match = (pathname: string) => KNOWN.find((p) => pathname === p || pathname.startsWith(p + '/')) ?? '';

function useNavEnv(): NavEnv {
  const override = useContext(NavEnvContext);
  const { pathname } = useLocation();
  const { username, isAdmin, logout } = useAuth();
  return override ?? { current: match(pathname), href: (to) => to, brandTo: '/', user: username, isAdmin, logout: () => { void logout(); } };
}

const inert = (e: React.MouseEvent) => e.preventDefault();

/** A nav link: to its real page, or inert when the environment has no target for it. */
export function NavItem({ item, current, upper, role }: { item: Item; current: string; upper?: boolean; role?: string }) {
  const env = useNavEnv();
  const label = upper ? item.label.toUpperCase() : item.label;
  const aria = item.to === current ? 'page' : undefined;
  const to = env.href(item.to);
  return to
    ? <Link to={to} role={role} aria-current={aria}>{label}</Link>
    : <a href="#" onClick={inert} role={role} aria-current={aria}>{label}</a>;
}

export function Brand() {
  const env = useNavEnv();
  return <Link to={env.brandTo} className="an-brand">ANVIL <span>//</span> CV</Link>;
}

/** "+ New application" is its own header item, current on the new-application page. */
export function NewButton({ compact, current }: { compact?: boolean; current: string }) {
  const env = useNavEnv();
  const to = env.href(NEW.to);
  const props = { className: 'an-new', 'aria-label': 'New application', 'aria-current': current === NEW.to ? ('page' as const) : undefined };
  const body = compact ? '+' : <>+<span className="an-new__label">NEW APPLICATION</span></>;
  return to ? <Link to={to} {...props}>{body}</Link> : <a href="#" onClick={inert} {...props}>{body}</a>;
}

/** One account menu: everything secondary lives here, Log out exactly once. */
export function AccountMenu({ items = ACCOUNT, current }: { items?: Item[]; current: string }) {
  const env = useNavEnv();
  const { open, setOpen, close, ref, trigger, onKeyDown } = useMenu();
  const name = env.user ?? '';
  return (
    <div className="an-acct" ref={ref} onKeyDown={onKeyDown}>
      <button ref={trigger} type="button" className="an-acct__btn" aria-expanded={open} aria-haspopup="menu" onClick={() => setOpen((o) => !o)}>
        <span className="an-acct__avatar">{name.charAt(0).toUpperCase()}</span>
        <span className="an-acct__name">{name}</span>
      </button>
      {open && (
        <div className="an-acct__menu" role="menu" onClick={() => close()}>
          {items.filter((i) => !i.admin || env.isAdmin).map((i) => <NavItem key={i.to} item={i} current={current} role="menuitem" />)}
          <hr />
          <a href="#" role="menuitem" onClick={(e) => { e.preventDefault(); env.logout(); }}>Log out</a>
        </div>
      )}
    </div>
  );
}

/** Phone: an overlay sheet over a dimming scrim, not a block that shoves the page down. */
function Sheet({ open, onClose, current }: { open: boolean; onClose: () => void; current: string }) {
  const env = useNavEnv();
  const closeBtn = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    closeBtn.current?.focus();
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', esc);
    return () => { document.body.style.overflow = prev; document.removeEventListener('keydown', esc); };
  }, [open, onClose]);
  const all: Item[] = env.user
    ? [...PRIMARY.slice(0, 4), NEW, PRIMARY[4], ...ACCOUNT.filter((i) => !i.admin || env.isAdmin)]
    : GUEST;
  return (
    <div className="an-sheet" data-open={open || undefined} aria-hidden={!open}>
      <div className="an-sheet__scrim" onClick={onClose} />
      <nav className="an-sheet__panel" aria-label="Menu" role="dialog" aria-modal="true" onClick={onClose}>
        <button ref={closeBtn} type="button" className="an-sheet__close" aria-label="Close menu" onClick={onClose}>✕</button>
        {all.map((i) => <NavItem key={i.to} item={i} current={current} />)}
        {env.user
          ? <a href="#" onClick={(e) => { e.preventDefault(); env.logout(); }} className="an-sheet__out">Log out</a>
          : <>
              <Link to="/login">Log in</Link>
              <Link to="/register">Sign up</Link>
            </>}
      </nav>
    </div>
  );
}

/** Strip — the app's sticky 52px bar: brand, five places, + New, account menu (a sheet on phones).
 *  Wired to the router and the session; wrap in a `NavEnvContext` to override. */
export function AppNav() {
  const env = useNavEnv();
  const [sheet, setSheet] = useState(false);
  const burger = useRef<HTMLButtonElement>(null);
  const closeSheet = () => { setSheet(false); burger.current?.focus(); };
  return (
    <>
      <header className="an-bar">
        <div className="shell an-bar__inner">
          <Brand />
          <nav className="an-links" aria-label="Main">
            {(env.user ? PRIMARY : GUEST).map((i) => <NavItem key={i.to} item={i} current={env.current} upper />)}
          </nav>
          <div className="an-right">
            {env.user ? (
              <>
                <NewButton current={env.current} />
                <AccountMenu current={env.current} />
              </>
            ) : (
              <>
                <Link to="/login" className="an-login">LOG IN</Link>
                <Link to="/register" className="an-new an-new--text">SIGN UP</Link>
              </>
            )}
            <button ref={burger} type="button" className="an-burger" aria-label="Menu" aria-expanded={sheet} onClick={() => setSheet(true)}>☰</button>
          </div>
        </div>
      </header>
      <Sheet open={sheet} onClose={closeSheet} current={env.current} />
    </>
  );
}
