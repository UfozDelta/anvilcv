import { useLocation, useNavigate } from 'react-router-dom';
import { fake, progressOf, useFakeVersion, type GuidePath } from './fakeApi';

export type StepId = 'import' | 'profile' | 'entry' | 'generate' | 'approve' | 'tailor' | 'page';
export type Step = { id: StepId; label: string; title: string; body: string; done: boolean; path: string };

const LIST = /^\/(experiences|projects)$/;
const DETAIL = /^\/(experiences|projects)\/[^/]+$/;
const HERE: Record<StepId, RegExp> = {
  import: /^\/upload$/,
  profile: /^\/profile$/,
  entry: LIST,
  generate: DETAIL,
  approve: DETAIL,
  tailor: /^\/new$/,
  page: /^\/applications\/[^/]+$/,
};

/** Who follows which road: import fills the bank for you; the other two build it by hand. */
const ORDER: Record<GuidePath, StepId[]> = {
  import: ['import', 'profile', 'approve', 'tailor', 'page'],
  manual: ['profile', 'entry', 'generate', 'approve', 'tailor', 'page'],
  skip: ['entry', 'generate', 'approve', 'profile', 'tailor', 'page'],
};

/**
 * Where the user is in the first run, derived from what is really in the (fake) account.
 * A step is done when the data exists, never because a tip was viewed. The steps and their
 * wording follow the road they chose at the welcome and the field they picked.
 */
export function useGuide() {
  useFakeVersion();
  const s = fake();
  const p = progressOf(s);
  const { pathname } = useLocation();
  const nav = useNavigate();
  const mech = s.field === 'mechanical';
  const nExp = s.projects.filter((x) => x.kind === 'EXPERIENCE').length;
  const nProj = s.projects.length - nExp;
  const first = s.projects.find((x) => x.kind === 'EXPERIENCE') ?? s.projects[0];
  const detail = first ? `/${first.kind === 'EXPERIENCE' ? 'experiences' : 'projects'}/${first.id}` : '/experiences';

  const all: Record<StepId, Step> = {
    import: { id: 'import', label: 'Import your résumé', done: p.imported, path: '/upload',
      title: 'Import your résumé', body: 'Paste it and we’ll fill in your experiences and projects.' },
    profile: { id: 'profile', label: 'Add your details', done: p.profile, path: '/profile',
      title: 'Add your contact details', body: 'Name, email and links head every page you send.' },
    entry: { id: 'entry', label: 'Add your first entry', done: p.imported, path: '/experiences',
      title: mech ? 'Add your first role or design project' : 'Add your first job or project',
      body: mech
        ? 'An internship, a team like Formula SAE, a test rig. Everything we write starts from what you describe here.'
        : 'A job, an internship or a side project. Everything we write starts from what you describe here.' },
    generate: { id: 'generate', label: 'Draft your bullets', done: s.bullets.length > 0, path: detail,
      title: 'Draft your bullets',
      body: mech
        ? 'Open it, go to Generate and hit Generate. We write bullets from the parts, analysis and results you described.'
        : 'Open it, go to Generate and hit Generate. We write bullets from what you described.' },
    approve: { id: 'approve', label: 'Approve 3 bullets', done: p.approved >= 3, path: detail,
      title: 'Approve three bullets',
      body: s.path === 'import' && p.imported
        ? `We drafted bullets from your ${nExp} experience${nExp === 1 ? '' : 's'} and ${nProj} project${nProj === 1 ? '' : 's'}. ${p.approved} of 3 approved.`
        : `${p.approved} of 3 approved. Only approved bullets can reach a page.` },
    tailor: { id: 'tailor', label: 'Tailor to a job', done: p.tailored, path: '/new',
      title: 'Tailor to a job', body: 'Paste a posting. We rank your approved bullets and fit one page.' },
    page: { id: 'page', label: 'Open your page', done: p.pdfViewed, path: s.apps[0] ? `/applications/${s.apps[0].id}` : '/applications',
      title: 'Open your page', body: 'Check the PDF. This is the page you’d send.' },
  };
  // Hand-built banks count "entry" as done once any entry exists, whatever put it there.
  all.entry.done = s.projects.length > 0;

  const steps = ORDER[s.path].map((id) => all[id]);
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
  if (id === 'tailor') return q('.an-new');
  return q('.an-links a[href="/applications"]');
}

/** The real control a coachmark points at: on the step's page the control itself, elsewhere the nav item that leads there. */
export function findTarget(id: StepId, pathname: string): { el: HTMLElement | null; onPage: boolean } {
  const onPage = (() => {
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
    if (id === 'tailor' && pathname === '/new') {
      const ta = q<HTMLTextAreaElement>('textarea.na-text');
      return ta && !ta.value ? ta : q('button[type="submit"]');
    }
    if (id === 'page') {
      if (/^\/applications\/./.test(pathname)) return byText('a.minibtn', /Download PDF/);
      if (pathname === '/applications') return q('.ld-link');
    }
    return null;
  })();
  return { el: onPage ?? navTarget(id), onPage: !!onPage };
}

/** Autoplay helpers: drive the real controls the way a person would. */
export function setValue(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')?.set?.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
export const clickText = (sel: string, re: RegExp) => byText(sel, re)?.click();
export const click = (sel: string) => q(sel)?.click();
export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
/** Poll until `get` returns something, or give up after `ms`. */
export async function until<T>(get: () => T | null | undefined | false, ms = 8000): Promise<T | null> {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { const v = get(); if (v) return v; await sleep(120); }
  return null;
}
export const find = (sel: string, re: RegExp) => byText(sel, re);
export const field = (sel: string) => q<HTMLInputElement | HTMLTextAreaElement>(sel);
