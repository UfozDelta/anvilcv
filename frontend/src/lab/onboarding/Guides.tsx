import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { fake, sampleJd, sampleResume } from './fakeApi';
import { click, clickText, field, find, findTarget, setValue, sleep, until, useGuide } from './guide';
import { EASE } from './shared';

/** Sticky bar under the nav: one next step, four-fifths of the app untouched. */
export function Strip() {
  const g = useGuide();
  const c = g.current;
  return (
    <div className="ob-strip" role="status">
      <span className="ob-strip__k">Next</span>
      <strong key={c?.id ?? 'done'} className="ob-strip__now">{c ? c.label : 'All set'}</strong>
      <ol className="ob-strip__seg" aria-label={`${g.done} of ${g.steps.length} done`}>
        {g.steps.map((s) => <li key={s.id} data-on={s.done || undefined} title={s.label} />)}
      </ol>
      <button type="button" className="ob-skip">Hide tips</button>
    </div>
  );
}

type Box = { x: number; y: number; w: number; h: number; onPage: boolean };

/** One spotlight on the real control for the current step. Falls back to a corner tip off the step's page. */
export function Coach() {
  const g = useGuide();
  const reduce = usePrefersReducedMotion();
  const [box, setBox] = useState<Box | null>(null);
  const raf = useRef(0);
  const id = g.current?.id;

  useLayoutEffect(() => {
    if (!id) { setBox(null); return; }
    const root = document.querySelector<HTMLElement>('.ob-app');
    const main = document.querySelector('main.ap-main');
    if (!root || !main) return;
    const measure = () => {
      const { el, onPage } = findTarget(id, g.pathname);
      if (!el) { setBox(null); return; }
      const vr = el.getBoundingClientRect();
      if (vr.top < 70 || vr.bottom > window.innerHeight - 40) el.scrollIntoView?.({ block: 'center', behavior: reduce ? 'auto' : 'smooth' });
      const r = el.getBoundingClientRect();
      const rr = root.getBoundingClientRect();
      setBox({ x: r.left - rr.left - 6, y: r.top - rr.top - 6, w: r.width + 12, h: r.height + 12, onPage });
    };
    const queue = () => { cancelAnimationFrame(raf.current); raf.current = requestAnimationFrame(measure); };
    queue();
    const mo = new MutationObserver(queue);
    mo.observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['aria-pressed', 'class'] });
    window.addEventListener('resize', queue);
    return () => { mo.disconnect(); window.removeEventListener('resize', queue); cancelAnimationFrame(raf.current); };
  }, [id, g.here, g.pathname, reduce]);

  const c = g.current;
  const rootW = document.querySelector('.ob-app')?.clientWidth ?? 1200;
  return (
    <>
      <AnimatePresence>
        {c && box && (
          <>
            <motion.div key="ring" className="ob-ring" aria-hidden
              initial={{ opacity: 0, x: box.x, y: box.y, width: box.w, height: box.h }}
              animate={{ opacity: 1, x: box.x, y: box.y, width: box.w, height: box.h }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }} />
            <motion.div key="tip" className="ob-tip" role="status"
              initial={{ opacity: 0, x: Math.min(box.x, rootW - 316), y: box.y + box.h + 18 }}
              animate={{ opacity: 1, x: Math.max(8, Math.min(box.x, rootW - 316)), y: box.y + box.h + 12 }}
              exit={{ opacity: 0 }} transition={{ duration: reduce ? 0 : 0.3, ease: EASE }}>
              <span className="ap-label">{g.done + 1} of {g.steps.length}</span>
              <strong>{c.title}</strong>
              <span>{c.body}</span>
              <span className="ob-tip__acts">
                {!box.onPage && <button type="button" className="ob-tip__go" onClick={() => (g.here ? g.nav('/experiences') : g.go())}>Take me there</button>}
                <button type="button" className="ob-skip">Skip tips</button>
              </span>
            </motion.div>
          </>
        )}
      </AnimatePresence>
      {c && !box && (
        <div className="ob-corner" role="status">
          <span className="ap-label">{g.done + 1} of {g.steps.length}</span>
          <strong>{c.title}</strong>
          <span>{c.body}</span>
          {!g.here && <button type="button" className="ap-btn ap-btn--acid" onClick={g.go}>Take me there</button>}
        </div>
      )}
    </>
  );
}

const ENTRY = {
  software: { title: 'Backend Engineer', company: 'Northwind Labs', label: 'Northwind Labs', about: 'Built order routing on Spring Boot and Kafka.' },
  mechanical: { title: 'Powertrain Design Intern', company: 'Ridgeline Motors', label: 'Ridgeline Motors', about: 'Redesigned a transmission housing bracket; ran FEA and a DFMEA.' },
};

/** Autoplay: do the whole first run on the real controls, on the road the welcome chose, waiting for each control like a person would. */
export function AutoDrive() {
  const g = useGuide();
  const navRef = useRef(g.nav);
  navRef.current = g.nav;
  useEffect(() => {
    let dead = false;
    const { field: f, path } = fake();
    const nav = (to: string) => navRef.current(to);
    const first = () => { const p = fake().projects.find((x) => x.kind === 'EXPERIENCE') ?? fake().projects[0]; return p ? `/${p.kind === 'EXPERIENCE' ? 'experiences' : 'projects'}/${p.id}` : '/experiences'; };
    const step = async (ms: number, fn: () => void) => { await sleep(ms); if (!dead) fn(); };
    const sel = (css: string) => () => document.querySelector(css);

    const importResume = async () => {
      await step(300, () => nav('/upload'));
      const resume = await until(() => field('textarea.field__textarea'));
      await step(700, () => resume && setValue(resume, sampleResume(f)));
      await step(1500, () => clickText('button.btn--acid', /parse/i));
      await until(() => find('button.btn--acid', /import selected/i));
      await step(1400, () => clickText('button.btn--acid', /import selected/i));
      await until(() => fake().projects.length > 0);
      await sleep(2600);
    };
    const profile = async () => {
      await step(300, () => nav('/profile'));
      await until(() => field('.pf-field input'));
      await step(900, () => {
        const i = document.querySelectorAll<HTMLInputElement>('.pf-field input');
        if (i[0]) setValue(i[0], 'Mara Okafor');
        if (i[1]) setValue(i[1], 'mara@okafor.dev');
      });
      await until(sel('.pf-save button'));
      await step(900, () => click('.pf-save button'));
      await sleep(1800);
    };
    const addByHand = async () => {
      const e = ENTRY[f];
      await step(300, () => nav('/experiences'));
      await until(sel('main .ap-btn--acid'));
      await step(900, () => click('main .ap-btn--acid'));
      await until(sel('.nf'));
      await step(700, () => {
        const i = document.querySelectorAll<HTMLInputElement>('.nf .pf-field input');
        [e.title, e.company, '', '', e.label].forEach((v, n) => { if (v && i[n]) setValue(i[n], v); });
        const ta = document.querySelector<HTMLTextAreaElement>('.nf textarea');
        if (ta) setValue(ta, e.about);
      });
      await step(1100, () => click('.nf button[type="submit"]'));
      await until(sel('.ld-link'));
      await sleep(1000);
    };
    const generate = async () => {
      await step(300, () => click('.ld-link'));
      await until(() => find('button[role="tab"]', /Generate/));
      await step(900, () => clickText('button[role="tab"]', /Generate/));
      await until(sel('.sf-gen .sf-go:not(:disabled)'));
      await step(900, () => click('.sf-gen .sf-go'));
      await until(() => find('button', /^\s*close\s*$/i), 20000);
      await step(700, () => clickText('button', /^\s*close\s*$/i));
      // The bank only reloads when the page is opened again.
      await step(500, () => nav('/experiences'));
      await until(sel('.ld-link'));
      await step(900, () => click('.ld-link'));
    };
    const approve = async (open: boolean) => {
      if (open) await step(500, () => nav(first()));
      for (let n = 0; n < 3; n++) {
        await until(sel('.sf-b__ok[aria-pressed="false"]'));
        await step(1000, () => click('.sf-b__ok[aria-pressed="false"]'));
      }
    };
    const tailorAndView = async () => {
      await step(1600, () => nav('/new'));
      const jd = await until(() => field('textarea.na-text'));
      await step(700, () => jd && setValue(jd, sampleJd(f)));
      await step(1800, () => click('button[type="submit"]'));
      await until(() => find('button', /VIEW APPLICATION/i), 20000);
      await step(900, () => clickText('button', /VIEW APPLICATION/i));
    };

    (async () => {
      if (path === 'import') { await importResume(); await profile(); await approve(true); }
      else if (path === 'manual') { await profile(); await addByHand(); await generate(); await approve(false); }
      else { await addByHand(); await generate(); await approve(false); await profile(); }
      await tailorAndView();
    })();

    return () => { dead = true; };
  }, []);
  return null;
}
