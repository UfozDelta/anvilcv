import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, RequireAuth, useAuth } from './lib/auth';
import { AppNav } from './components/ledger/AppNav';
import { Landing } from './pages/Landing';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { Projects } from './pages/Projects';
import { Experiences } from './pages/Experiences';
import { ProjectDetail } from './pages/ProjectDetail';
import { NewApplication } from './pages/NewApplication';
import { Applications } from './pages/Applications';
import { ApplicationDetail } from './pages/ApplicationDetail';
import { OutcomeFlow } from './pages/OutcomeFlow';
import { ProfilePage } from './pages/ProfilePage';
import { SettingsPage } from './pages/SettingsPage';
import { AdminPage } from './pages/AdminPage';
import { DocsPage } from './pages/DocsPage';
import { UploadPage } from './pages/UploadPage';
import { Jobs } from './pages/Jobs';
import { Pricing } from './pages/Pricing';
import { OnboardingGuide } from './components/onboarding/Guides';
import { PaywallModal } from './components/PaywallModal';
import { WelcomeFast } from './components/onboarding/WelcomeFast';


// UI prototypes on placeholder data. Dev only: DEV is a build-time constant, so the dynamic import
// and the whole lab/ tree are dropped from the production bundle.
const LabRoutes = import.meta.env.DEV ? lazy(() => import('./lab/LabRoutes')) : null;

/** The router keeps the old scroll position across navigation, so a link clicked at the bottom of a page opened the next one mid-way. */
function ScrollToTop() {
  const { pathname, hash } = useLocation();
  useEffect(() => { if (!hash) window.scrollTo(0, 0); }, [pathname, hash]);
  return null;
}

function AuthedShell({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <div className="ap-root">
        <AppNav />
        <OnboardingGuide />
        <main className="ap-main" style={{ paddingBottom: 80 }}>{children}</main>
      </div>
    </RequireAuth>
  );
}

/** Public, but a signed-in newcomer still gets the first-run guide here: picking a posting is a step. */
function JobsShell() {
  const { username } = useAuth();
  return (
    <div className="ap-root">
      <AppNav />
      {username && <OnboardingGuide />}
      <main className="ap-main" style={{ paddingBottom: 80 }}><Jobs /></main>
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <ScrollToTop />
      <PaywallModal />
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/welcome"              element={<RequireAuth><OnboardingGuide /><WelcomeFast /></RequireAuth>} />
        <Route path="/projects"             element={<AuthedShell><Projects /></AuthedShell>} />
        <Route path="/experiences"          element={<AuthedShell><Experiences /></AuthedShell>} />
        <Route path="/projects/:id"         element={<AuthedShell><ProjectDetail /></AuthedShell>} />
        <Route path="/experiences/:id"      element={<AuthedShell><ProjectDetail /></AuthedShell>} />
        <Route path="/new"                  element={<AuthedShell><NewApplication /></AuthedShell>} />
        <Route path="/applications"         element={<AuthedShell><Applications /></AuthedShell>} />
        <Route path="/applications/:id"     element={<AuthedShell><ApplicationDetail /></AuthedShell>} />
        <Route path="/flow"                 element={<AuthedShell><OutcomeFlow /></AuthedShell>} />
        <Route path="/profile"              element={<AuthedShell><ProfilePage /></AuthedShell>} />
        <Route path="/settings"             element={<AuthedShell><SettingsPage /></AuthedShell>} />
        <Route path="/admin"                element={<AuthedShell><AdminPage /></AuthedShell>} />
        <Route path="/upload"               element={<AuthedShell><UploadPage /></AuthedShell>} />
        <Route path="/docs"                 element={<DocsPage />} />
        <Route path="/pricing"              element={<Pricing />} />
        {/* Public on purpose: guests browse the feed; Tailor sends them to log in. */}
        <Route path="/jobs"                 element={<JobsShell />} />
        {LabRoutes && <Route path="/lab/*" element={<Suspense fallback={null}><LabRoutes /></Suspense>} />}
        <Route path="*"                     element={<Navigate to="/projects" replace />} />
      </Routes>
    </AuthProvider>
  );
}
