import { Routes, Route } from 'react-router-dom';
import { LabIndex } from './LabIndex';
import { LabList } from './LabList';
import { LabDetail } from './LabDetail';
import { LabRows } from './LabRows';
import { LabProjectsList } from './LabProjectsList';
import { LabProjectDetail } from './LabProjectDetail';
import { LabMasthead } from './LabMasthead';
import { LabHero } from './hero/LabHero';
import { LabLanding } from './landing/LabLanding';
import { LabLandingV3 } from './landing/LabLandingV3';
import { LabHow } from './how/LabHow';
import { LabApp, LabNav } from './app/LabApp';
import { LabApplications } from './app/LabApplications';
import { LabExperiences } from './app/LabExperiences';
import { LabNewApplication } from './app/LabNewApplication';
import { LabJobs } from './app/LabJobs';
import { LabProfile } from './app/LabProfile';
import { LabSettings } from './app/LabSettings';
import { LabFlow } from './app/LabFlow';
import { LabLandingV2 } from './landing-v2/LabLandingV2';
import { LabStories } from './stories/LabStories';
import { LabStoriesLean } from './stories-lean/LabStoriesLean';
import { LabProjectSplit } from './project-split/LabProjectSplit';
import { LabWorkspace } from './workspace/LabWorkspace';
import { LabWorkspaceTabs } from './workspace-tabs/LabWorkspaceTabs';
import { LabStoryFlow } from './story-flow/LabStoryFlow';
import { LabLists } from './lists/LabLists';
import { LabPaywall } from './paywall/LabPaywall';
import { LabWalkthrough } from './walkthrough/LabWalkthrough';

/** Dev-only prototype pages, mounted at /lab/* by App. Unauthenticated: they touch no API. */
export default function LabRoutes() {
  return (
    <Routes>
      <Route index element={<LabIndex />} />
      <Route path="list" element={<LabList />} />
      <Route path="detail" element={<LabDetail />} />
      <Route path="rows" element={<LabRows />} />
      <Route path="projects" element={<LabProjectsList />} />
      <Route path="project-detail" element={<LabProjectDetail />} />
      <Route path="masthead" element={<LabMasthead />} />
      <Route path="hero" element={<LabHero />} />
      <Route path="landing" element={<LabLanding />} />
      <Route path="landing-v3" element={<LabLandingV3 />} />
      <Route path="how" element={<LabHow />} />
      <Route path="app" element={<LabApp />} />
      <Route path="nav" element={<LabNav />} />
      <Route path="applications" element={<LabApplications />} />
      <Route path="experiences" element={<LabExperiences />} />
      <Route path="new-application" element={<LabNewApplication />} />
      <Route path="jobs" element={<LabJobs />} />
      <Route path="profile" element={<LabProfile />} />
      <Route path="settings" element={<LabSettings />} />
      <Route path="flow" element={<LabFlow />} />
      <Route path="landing-v2" element={<LabLandingV2 />} />
      <Route path="stories" element={<LabStories />} />
      <Route path="stories-lean" element={<LabStoriesLean />} />
      <Route path="project-split" element={<LabProjectSplit />} />
      <Route path="workspace" element={<LabWorkspace />} />
      <Route path="workspace-tabs" element={<LabWorkspaceTabs />} />
      <Route path="story-flow" element={<LabStoryFlow />} />
      <Route path="lists" element={<LabLists />} />
      <Route path="paywall" element={<LabPaywall />} />
      <Route path="walkthrough" element={<LabWalkthrough />} />
    </Routes>
  );
}
