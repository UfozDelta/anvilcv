import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, RequireAuth } from './lib/auth';
import { Masthead } from './components/Masthead';
import { Landing } from './pages/Landing';
import { Login } from './pages/Login';
import { Projects } from './pages/Projects';
import { Experiences } from './pages/Experiences';
import { ProjectDetail } from './pages/ProjectDetail';
import { NewApplication } from './pages/NewApplication';
import { Applications } from './pages/Applications';
import { ApplicationDetail } from './pages/ApplicationDetail';
import { ProfilePage } from './pages/ProfilePage';

function AuthedShell({ children }: { children: React.ReactNode }) {
  return (
    <RequireAuth>
      <Masthead />
      <main style={{ paddingBottom: 80 }}>{children}</main>
    </RequireAuth>
  );
}

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/projects"             element={<AuthedShell><Projects /></AuthedShell>} />
        <Route path="/experiences"          element={<AuthedShell><Experiences /></AuthedShell>} />
        <Route path="/projects/:id"         element={<AuthedShell><ProjectDetail /></AuthedShell>} />
        <Route path="/experiences/:id"      element={<AuthedShell><ProjectDetail /></AuthedShell>} />
        <Route path="/new"                  element={<AuthedShell><NewApplication /></AuthedShell>} />
        <Route path="/applications"         element={<AuthedShell><Applications /></AuthedShell>} />
        <Route path="/applications/:id"     element={<AuthedShell><ApplicationDetail /></AuthedShell>} />
        <Route path="/profile"              element={<AuthedShell><ProfilePage /></AuthedShell>} />
        <Route path="*"                     element={<Navigate to="/projects" replace />} />
      </Routes>
    </AuthProvider>
  );
}
