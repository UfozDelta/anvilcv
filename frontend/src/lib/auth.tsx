import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { api, UnauthorizedError } from './api';

interface Identity { username: string; isAdmin: boolean }

interface AuthState {
  username: string | null;
  isAdmin: boolean;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null);
  // Gates the admin nav link and route only. The server enforces ROLE_ADMIN on
  // /api/admin/** independently, so flipping this in devtools buys nothing.
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);

  const clear = () => { setUsername(null); setIsAdmin(false); };
  const accept = (r: Identity) => { setUsername(r.username); setIsAdmin(!!r.isAdmin); };

  useEffect(() => {
    api.get<Identity>('/api/me')
      .then(accept)
      .catch(clear)
      .finally(() => setLoading(false));
  }, []);

  const login = async (u: string, p: string) => {
    accept(await api.post<Identity>('/api/login', { username: u, password: p }));
  };

  const logout = async () => {
    try { await api.post('/api/logout'); } catch { /* ignore */ }
    clear();
  };

  const register = async (u: string, email: string, p: string) => {
    accept(await api.post<Identity>('/api/register', { username: u, email, password: p }));
  };

  return (
    <AuthContext.Provider value={{ username, isAdmin, loading, login, logout, register }}>
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
  const { username, loading } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => {
    if (!loading && !username) nav('/login', { replace: true, state: { from: loc.pathname } });
  }, [loading, username, nav, loc.pathname]);
  if (loading) return <div className="center-page"><span className="spinner">LOADING</span></div>;
  if (!username) return null;
  return <>{children}</>;
}

export { UnauthorizedError };
