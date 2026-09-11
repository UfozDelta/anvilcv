import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { api, UnauthorizedError } from './api';

interface Identity { username: string; isAdmin: boolean }

interface AuthState {
  username: string | null;
  isAdmin: boolean;
  loading: boolean;
  /**
   * /api/me never answered — tunnel blip, cold backend, dropped connection. This is
   * NOT the same as being logged out: the session is probably still valid on the
   * server, so RequireAuth holds the route rather than bouncing to /login. Treating
   * the two as one is what made a momentary network hiccup look like a logout.
   */
  unreachable: boolean;
  retry: () => void;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/** Waits between /api/me attempts. Gives the backend ~3.5s total to answer. */
const PROBE_BACKOFF_MS = [500, 1000, 2000];

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null);
  // Gates the admin nav link and route only. The server enforces ROLE_ADMIN on
  // /api/admin/** independently, so flipping this in devtools buys nothing.
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [unreachable, setUnreachable] = useState(false);

  const clear = () => { setUsername(null); setIsAdmin(false); };
  const accept = (r: Identity) => {
    setUsername(r.username);
    setIsAdmin(!!r.isAdmin);
    setUnreachable(false);
  };

  const probe = useCallback(async () => {
    setLoading(true);
    setUnreachable(false);
    for (let attempt = 0; ; attempt++) {
      try {
        accept(await api.get<Identity>('/api/me'));
        setLoading(false);
        return;
      } catch (e) {
        // A 401 is a real answer — there is no session, so send them to log in.
        // Anything else means we never got an answer at all; retry before
        // concluding anything about the session.
        if (e instanceof UnauthorizedError) {
          clear();
          setLoading(false);
          return;
        }
        if (attempt >= PROBE_BACKOFF_MS.length) {
          clear();
          setUnreachable(true);
          setLoading(false);
          return;
        }
        await new Promise(r => setTimeout(r, PROBE_BACKOFF_MS[attempt]));
      }
    }
  }, []);

  useEffect(() => { void probe(); }, [probe]);

  const login = async (u: string, p: string) => {
    accept(await api.post<Identity>('/api/login', { username: u, password: p }));
  };

  const logout = async () => {
    try { await api.post('/api/logout'); } catch { /* ignore */ }
    clear();
    setUnreachable(false);
  };

  const register = async (u: string, email: string, p: string) => {
    accept(await api.post<Identity>('/api/register', { username: u, email, password: p }));
  };

  return (
    <AuthContext.Provider
      value={{ username, isAdmin, loading, unreachable, retry: probe, login, logout, register }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside provider');
  return ctx;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { username, loading, unreachable, retry } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => {
    // Only a definitive "no session" redirects. An unreachable backend used to land
    // here too, which turned every blip into an apparent logout.
    if (!loading && !unreachable && !username) {
      nav('/login', { replace: true, state: { from: loc.pathname } });
    }
  }, [loading, unreachable, username, nav, loc.pathname]);

  if (loading) return <div className="center-page"><span className="spinner">LOADING</span></div>;

  if (unreachable) {
    return (
      <div className="center-page">
        <div style={{ width: 460, maxWidth: '100%', textAlign: 'center' }}>
          <div className="err">Can&rsquo;t reach the server.</div>
          <div className="muted" style={{ fontSize: 13, margin: '10px 0 18px' }}>
            Your session is probably still fine &mdash; the backend just didn&rsquo;t answer.
          </div>
          <button className="btn btn--acid" onClick={retry}>RETRY</button>
        </div>
      </div>
    );
  }

  if (!username) return null;
  return <>{children}</>;
}

export { UnauthorizedError };
