import { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, RequireAuth } from './lib/auth';
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
import { LabIndex } from './lab/LabIndex';
import { LabList } from './lab/LabList';
import { LabDetail } from './lab/LabDetail';
import { LabRows } from './lab/LabRows';
import { LabProjectsList } from './lab/LabProjectsList';
import { LabProjectDetail } from './lab/LabProjectDetail';
import { LabMasthead } from './lab/LabMasthead';
import { LabHero } from './lab/hero/LabHero';
import { LabLanding } from './lab/landing/LabLanding';
import { LabLandingV3 } from './lab/landing/LabLandingV3';
import { LabHow } from './lab/how/LabHow';
import { LabApp, LabNav } from './lab/app/LabApp';
import { LabApplications } from './lab/app/LabApplications';
import { LabExperiences } from './lab/app/LabExperiences';
import { LabNewApplication } from './lab/app/LabNewApplication';
import { LabJobs } from './lab/app/LabJobs';
import { LabProfile } from './lab/app/LabProfile';
import { LabSettings } from './lab/app/LabSettings';
import { LabFlow } from './lab/app/LabFlow';
import { LabLandingV2 } from './lab/landing-v2/LabLandingV2';
import { LabStories } from './lab/stories/LabStories';
import { LabStoriesLean } from './lab/stories-lean/LabStoriesLean';

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
        <main className="ap-main" style={{ paddingBottom: 80 }}>{children}</main>
      </div>
    </RequireAuth>
  );
}

export function App() {
  return (
    <AuthProvider>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
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
        <Route path="/jobs"                 element={<div className="ap-root"><AppNav /><main className="ap-main" style={{ paddingBottom: 80 }}><Jobs /></main></div>} />

        {/* UI prototypes on placeholder data. Unauthenticated on purpose: they touch
            no API and exist to review the Applications redesign without a login. */}
        <Route path="/lab"                  element={<LabIndex />} />
        <Route path="/lab/list"             element={<LabList />} />
        <Route path="/lab/detail"           element={<LabDetail />} />
        <Route path="/lab/rows"             element={<LabRows />} />
        <Route path="/lab/projects"         element={<LabProjectsList />} />
        <Route path="/lab/project-detail"   element={<LabProjectDetail />} />
        <Route path="/lab/masthead"         element={<LabMasthead />} />
        <Route path="/lab/hero"             element={<LabHero />} />
        <Route path="/lab/landing"          element={<LabLanding />} />
        <Route path="/lab/landing-v3"       element={<LabLandingV3 />} />
        <Route path="/lab/how"              element={<LabHow />} />
        <Route path="/lab/app"              element={<LabApp />} />
        <Route path="/lab/nav"              element={<LabNav />} />
        <Route path="/lab/applications"     element={<LabApplications />} />
        <Route path="/lab/experiences"      element={<LabExperiences />} />
        <Route path="/lab/new-application"  element={<LabNewApplication />} />
        <Route path="/lab/jobs"             element={<LabJobs />} />
        <Route path="/lab/profile"          element={<LabProfile />} />
        <Route path="/lab/settings"         element={<LabSettings />} />
        <Route path="/lab/flow"             element={<LabFlow />} />
        <Route path="/lab/landing-v2"       element={<LabLandingV2 />} />
        <Route path="/lab/stories"          element={<LabStories />} />
        <Route path="/lab/stories-lean"     element={<LabStoriesLean />} />
        <Route path="*"                     element={<Navigate to="/projects" replace />} />
      </Routes>
    </AuthProvider>
  );
}
