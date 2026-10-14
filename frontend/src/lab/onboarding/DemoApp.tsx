import { useEffect, useRef, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AppNav, NavEnvContext, type NavEnv } from '../../components/ledger/AppNav';
import { AuthProvider } from '../../lib/auth';
import { Projects } from '../../pages/Projects';
import { Experiences } from '../../pages/Experiences';
import { ProjectDetail } from '../../pages/ProjectDetail';
import { NewApplication } from '../../pages/NewApplication';
import { Applications } from '../../pages/Applications';
import { ApplicationDetail } from '../../pages/ApplicationDetail';
import { ProfilePage } from '../../pages/ProfilePage';
import { UploadPage } from '../../pages/UploadPage';
import { fake } from './fakeApi';

const NAV_PATHS = ['/profile', '/projects', '/experiences', '/applications', '/jobs', '/new', '/flow', '/settings', '/upload'];
const match = (pathname: string) => NAV_PATHS.find((p) => pathname === p || pathname.startsWith(p + '/')) ?? '';

function Shell({ banner, children }: { banner?: ReactNode; children?: ReactNode }) {
  const { pathname } = useLocation();
  const env: NavEnv = { current: match(pathname), href: (to) => to, brandTo: '/projects', user: fake().username, isAdmin: false, logout: () => {} };
  return (
    <NavEnvContext.Provider value={env}>
      <div className="ap-root ob-app">
        <AppNav />
        {banner}
        <main className="ap-main" style={{ paddingBottom: 80 }}>
          <Routes>
            <Route path="/projects" element={<Projects />} />
            <Route path="/experiences" element={<Experiences />} />
            <Route path="/projects/:id" element={<ProjectDetail />} />
            <Route path="/experiences/:id" element={<ProjectDetail />} />
            <Route path="/new" element={<NewApplication />} />
            <Route path="/applications" element={<Applications />} />
            <Route path="/applications/:id" element={<ApplicationDetail />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/upload" element={<UploadPage />} />
            <Route path="*" element={<Projects />} />
          </Routes>
        </main>
        <div className="ob-layer">{children}</div>
      </div>
    </NavEnvContext.Provider>
  );
}

/**
 * The real app: the real nav and the real pages, in their own router, running against the fake
 * backend. `banner` sits in flow under the nav; `children` float above the page.
 *
 * It lives in its own React root because the lab page already sits inside the app's router,
 * and React Router refuses to nest one router in another.
 */
export function DemoApp({ start, banner, children }: { start: string; banner?: ReactNode; children?: ReactNode }) {
  const host = useRef<HTMLDivElement>(null);
  const root = useRef<Root | null>(null);

  // A fresh container per mount: StrictMode runs this effect twice, and a root must not be
  // created twice on one element or torn down after its replacement is already mounted.
  useEffect(() => {
    const el = document.createElement('div');
    host.current!.appendChild(el);
    const r = createRoot(el);
    root.current = r;
    return () => {
      if (root.current === r) root.current = null;
      queueMicrotask(() => { r.unmount(); el.remove(); }); // not mid-render
    };
  }, []);

  // Every render of the caller re-renders the isolated tree, so its state and the guides stay in step.
  useEffect(() => {
    root.current?.render(
      <AuthProvider>
        <MemoryRouter initialEntries={[start]}>
          <Shell banner={banner}>{children}</Shell>
        </MemoryRouter>
      </AuthProvider>,
    );
  });

  return <div ref={host} />;
}
