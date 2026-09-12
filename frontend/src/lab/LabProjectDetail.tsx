import { useState } from 'react';
import type { Project } from '../lib/api';
import { LAB_PROJECTS } from './fixtures';
import { LabChrome } from './LabChrome';
import { ProjectWorkspace } from './projectDetail/ProjectWorkspace';

const DEMO_EXPERIENCE = LAB_PROJECTS.find(p => p.kind === 'EXPERIENCE')!;
const DEMO_PROJECT = LAB_PROJECTS.find(p => p.kind === 'PROJECT')!;

export function LabProjectDetail() {
  // The real page reads ?id= from the route; the lab always shows one of two fixture
  // records, switchable below so both the Experience and Project variants are reachable.
  const [project, setProject] = useState<Project>(DEMO_EXPERIENCE);
  const isExperience = project.kind === 'EXPERIENCE';

  return (
    <LabChrome
      title="Projects & Experiences — bank"
      note={
        <>
          Full pass: approve/reject, add, edit, delete, refit, sort, and a page-budget PDF
          preview — the same jobs the real bullet bank does — rebuilt with the fixes from the
          Applications rework. Delete lives in a menu with an in-place confirm instead of a bare
          DELETE button; approve/reject is a labelled toggle instead of an underline; each
          bullet's line-fit band is a real badge, and REFIT shows which bullets it will touch
          before you run it.
        </>
      }
    >
      <div className="row row--between row--centered" style={{ marginBottom: 8 }}>
        <div className="eyebrow">← {isExperience ? 'All experiences' : 'All projects'}</div>
        <div className="filterset" title="Lab-only — switches which fixture record this page demos">
          <button className={isExperience ? 'is-on' : ''} onClick={() => setProject(DEMO_EXPERIENCE)}>
            Viewing: Experience
          </button>
          <button className={!isExperience ? 'is-on' : ''} onClick={() => setProject(DEMO_PROJECT)}>
            Viewing: Project
          </button>
        </div>
      </div>

      <ProjectWorkspace key={project.id} project={project} isExperience={isExperience} />
    </LabChrome>
  );
}
