import { useLocation, useNavigate } from 'react-router-dom';
import { getRoad, type Progress, type Road } from './store';

export type StepId = 'import' | 'profile' | 'entry' | 'generate' | 'approve' | 'tailor' | 'page';
export type Step = { id: StepId; label: string; title: string; body: string; done: boolean; path: string };

const LIST = /^\/(experiences|projects)$/;
const DETAIL = /^\/(experiences|projects)\/[^/]+$/;
const HERE: Record<StepId, RegExp> = {
  import: /^\/(upload|welcome)$/,
  profile: /^\/profile$/,
  entry: LIST,
  generate: DETAIL,
  approve: DETAIL,
  tailor: /^\/(new|jobs)$/,
  page: /^\/applications\/[^/]+$/,
};

/** Who follows which road: import fills the bank for you (contact details wait until the page needs them); the other two build it by hand. */
const ORDER = {
  import: ['import', 'approve', 'tailor', 'profile', 'page'],
  manual: ['profile', 'entry', 'generate', 'approve', 'tailor', 'page'],
  skip: ['entry', 'generate', 'approve', 'profile', 'tailor', 'page'],
  /** Résumé bullets arrive approved, so the first goal is a tailored page; contact details finish it. Download is always free, so it is no milestone. */
  fast: ['import', 'tailor', 'profile'],
} satisfies Record<string, StepId[]>;

const APPROVE_GOAL = 3;

/** The road's steps with their wording, each done when the data exists. Pure, so it is tested without a browser. */
export function deriveSteps(p: Progress, field: string, road: Road): Step[] {
  const mech = field === 'mechanical';
  const fast = road === 'fast';
  const s = p.status;
  const detail = p.firstEntryPath ?? '/experiences';
  const approved = Math.min(s.approvedBullets, APPROVE_GOAL);

  const all: Record<StepId, Step> = {
    import: { id: 'import', label: 'Import your résumé', done: s.entries > 0, path: road === 'fast' ? '/welcome' : '/upload',
      title: 'Import your résumé',
      body: fast ? 'Paste it, then check the bullets we found. They stay in your words.' : 'Paste it and we’ll fill in your experiences and projects.' },
    profile: { id: 'profile', label: 'Add your details', done: p.profileDone, path: '/profile',
      title: 'Add your contact details', body: 'Name, email and links head every page you send.' },
    entry: { id: 'entry', label: 'Add your first entry', done: s.entries > 0, path: '/experiences',
      title: mech ? 'Add your first role or design project' : 'Add your first job or project',
      body: mech
        ? 'An internship, a team like Formula SAE, a test rig. Everything we write starts from what you describe here.'
        : 'A job, an internship or a side project. Everything we write starts from what you describe here.' },
    generate: { id: 'generate', label: 'Draft your bullets', done: s.bullets > 0, path: detail,
      title: 'Draft your bullets',
      body: mech
        ? 'Open it, go to Generate and hit Generate. We write bullets from the parts, analysis and results you described.'
        : 'Open it, go to Generate and hit Generate. We write bullets from what you described.' },
    approve: { id: 'approve', label: 'Approve 3 bullets', done: s.approvedBullets >= APPROVE_GOAL, path: detail,
      title: 'Approve three bullets',
      body: road === 'import' && s.entries > 0
        ? `We drafted bullets from your ${p.experiences} experience${p.experiences === 1 ? '' : 's'} and ${p.projects} project${p.projects === 1 ? '' : 's'}. ${approved} of ${APPROVE_GOAL} approved.`
        : `${approved} of ${APPROVE_GOAL} approved. Only approved bullets can reach a page.` },
    tailor: { id: 'tailor', label: fast ? 'Pick a job' : 'Tailor to a job', done: s.applications > 0, path: fast ? '/jobs' : '/new',
      title: fast ? 'Pick a job to tailor to' : 'Tailor to a job',
      body: fast ? 'Choose a posting and press Tailor. We fit your bullets to it on one page.' : 'Paste a posting. We rank your approved bullets and fit one page.' },
    page: { id: 'page', label: 'Download your page', done: s.downloaded, path: p.firstApplicationPath ?? '/applications',
      title: 'Download your page', body: 'Check the PDF, then download it. This is the page you’d send.' },
  };
  return ORDER[road].map((id) => all[id]);
}

/** Where the user is in the first run, for the road they chose at the welcome and the field they picked. */
export function useGuide(p: Progress, field: string) {
  const { pathname } = useLocation();
  const nav = useNavigate();
  const steps = deriveSteps(p, field, getRoad());
  const current = steps.find((x) => !x.done);
  const here = current ? HERE[current.id].test(pathname) : false;
  return { steps, current, done: steps.filter((x) => x.done).length, here, go: () => current && nav(current.path), nav, pathname };
}

const q = <T extends Element = HTMLElement>(sel: string) => document.querySelector<T>(sel);
const byText = (sel: string, re: RegExp) => [...document.querySelectorAll<HTMLElement>(sel)].find((e) => re.test(e.textContent ?? '')) ?? null;

/** Off the step's page: point at the nav item that leads there, so the box keeps guiding across pages. */
function navTarget(id: StepId): HTMLElement | null {
  if (id === 'import') return q('.an-acct__btn');
  if (id === 'profile') return q('.an-links a[href="/profile"]');
  if (id === 'entry' || id === 'generate' || id === 'approve') return q('.an-links a[href="/experiences"]');
  if (id === 'tailor') return getRoad() === 'fast' ? q('.an-links a[href="/jobs"]') : q('.an-new');
  return q('.an-links a[href="/applications"]');
}

/** The real control a coachmark points at: on the step's page the control itself, elsewhere the nav item that leads there. */
export function findTarget(id: StepId, pathname: string): { el: HTMLElement | null; onPage: boolean } {
  const onPage = (() => {
    if (id === 'import' && pathname === '/welcome') {
      const ta = q<HTMLTextAreaElement>('textarea.fr-paste');
      return q('.fr-go') ?? (ta && !ta.value ? ta : q('.fr-parse'));
    }
    if (id === 'import' && pathname === '/upload') {
      const ta = q<HTMLTextAreaElement>('textarea.field__textarea');
      return ta && !ta.value ? ta : byText('button.btn--acid', /parse|import/i) ?? ta;
    }
    if (id === 'profile' && pathname === '/profile') return q('.pf-field input');
    if (id === 'entry' && LIST.test(pathname)) return q('.nf') ?? q('main .ap-btn--acid');
    if (id === 'generate') {
      if (DETAIL.test(pathname)) return q('.sf-gen .sf-go') ?? byText('button[role="tab"]', /Generate/);
      if (LIST.test(pathname)) return q('.ld-link');
    }
    if (id === 'approve') {
      if (DETAIL.test(pathname)) return q('.sf-b__ok[aria-pressed="false"]');
      if (LIST.test(pathname)) return q('.ld-link');
    }
    if (id === 'tailor' && pathname === '/jobs') return q('.ap-btn--acid.jb-btn');
    if (id === 'tailor' && pathname === '/new') {
      const ta = q<HTMLTextAreaElement>('textarea.na-text');
      const url = q<HTMLInputElement>('input.na-url');
      return ta && !ta.value && !url?.value ? ta : q('button[type="submit"]');
    }
    if (id === 'page') {
      if (/^\/applications\/./.test(pathname)) return byText('a.minibtn', /Download PDF/);
      if (pathname === '/applications') return q('.ld-link');
    }
    return null;
  })();
  return { el: onPage ?? navTarget(id), onPage: !!onPage };
}

/** The step's wording for the screen the user is on: the same step reads differently on Jobs, on New application and while confirming bullets. */
export function textFor(step: Step, pathname: string): { title: string; body: string } {
  if (step.id === 'tailor' && pathname === '/new') return { title: 'Run it', body: 'Check the posting, then press Run. We fit your bullets to one page.' };
  if (step.id === 'import' && pathname === '/welcome' && q('.fr-go')) return { title: 'Check your bullets', body: 'Fix a line or uncheck one, then press “Looks right”.' };
  return { title: step.title, body: step.body };
}
