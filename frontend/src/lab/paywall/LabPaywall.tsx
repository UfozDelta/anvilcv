/* /lab/paywall — three places to put the upgrade ask in first-run onboarding. Placeholder data, no billing. */
import { useEffect, useRef, useState } from 'react';
import { NavStrip } from '../app/AppNav';
import { BULLETS, FREE_TAILORS, JOB, PAGE, PLANS, RESET, STEPS } from './data';
import '../../styles/landing.css';
import '../../styles/ledger.css';
import '../../components/onboarding/onboarding.css';
import './paywall.css';

type Variant = 'a' | 'b' | 'c';

const VARIANTS: { key: Variant; label: string }[] = [
  { key: 'a', label: 'A · Lock the PDF' },
  { key: 'b', label: 'B · Free quota' },
  { key: 'c', label: 'C · After 3 bullets' },
];

const NOTES: Record<Variant, { gate: string; free: string; why: string[]; risk: string }> = {
  a: {
    gate: 'The first Download PDF on a tailored page.',
    free: 'Import, entries, generating, approving, tailoring, the full page preview.',
    why: [
      'Asks at proven value: the page is on screen before the ask.',
      'Paid unit = the file sent to an employer; nothing built is held back.',
    ],
    risk: 'No free tailor at all. Contradicts the "3 a month, forever" free tier on /pricing.',
  },
  b: {
    gate: 'The 4th tailored page in a month. A meter shows usage from the first one.',
    free: `${FREE_TAILORS} tailors a month, everything before them. A tailor is one job: the page, its cover letter and re-downloads count once.`,
    why: [
      'User finishes onboarding (a downloaded page) before any ask.',
      'Usage-based freemium: the limit is visible early, so the gate is never a surprise.',
      'Matches the plans and free tier already on /pricing.',
    ],
    risk: 'Someone applying to 3 jobs never pays. That is the free tier working, not a leak.',
  },
  c: {
    gate: 'A dialog right after the 3rd bullet is approved.',
    free: 'Same as B. The dialog is a soft ask; Continue free is a full button.',
    why: [
      'Catches peak motivation, the first "this is good" moment.',
      'Accessible dialog: focus trapped, Esc closes, focus returns.',
    ],
    risk: 'Asks before the tailored page, the real product, has been seen. Likely lower conversion and more early drop-off.',
  },
};

export function LabPaywall() {
  const [v, setV] = useState<Variant>('b');
  const [phone, setPhone] = useState(false);
  const n = NOTES[v];
  return (
    <div className="ap-root pw-page">
      <NavStrip />
      <div className="shell pw-lab">
        <div className="pw-bar">
          <div className="pw-seg" role="group" aria-label="Gate placement">
            {VARIANTS.map((x) => (
              <button key={x.key} type="button" aria-pressed={v === x.key} onClick={() => setV(x.key)}>{x.label}</button>
            ))}
          </div>
          <div className="pw-seg" role="group" aria-label="Width">
            <button type="button" aria-pressed={!phone} onClick={() => setPhone(false)}>Desktop</button>
            <button type="button" aria-pressed={phone} onClick={() => setPhone(true)}>Phone 390</button>
          </div>
        </div>

        <dl className="pw-notes">
          <div><dt>Gate</dt><dd>{n.gate}</dd></div>
          <div><dt>Stays free</dt><dd>{n.free}</dd></div>
          <div><dt>Why this works</dt><dd><ul>{n.why.map((w) => <li key={w}>{w}</li>)}</ul></dd></div>
          <div><dt>Risk</dt><dd>{n.risk}</dd></div>
        </dl>
      </div>

      <div className="shell">
        <div className="pw-stage" data-phone={phone || undefined}>
          {/* key resets each mock's state when switching placements */}
          {v === 'a' && <LockPdf key="a" />}
          {v === 'b' && <Quota key="b" />}
          {v === 'c' && <AfterApprove key="c" />}
        </div>
      </div>
    </div>
  );
}

/** The real guide strip, frozen at a step. */
function Strip({ done }: { done: number }) {
  const now = STEPS[done] ?? 'All set';
  return (
    <div className="ob-strip" role="status">
      <span className="ob-strip__k">Next</span>
      <strong className="ob-strip__now">{now}</strong>
      <ol className="ob-strip__seg" aria-label={`${done} of ${STEPS.length} done`}>
        {STEPS.map((s, i) => <li key={s} data-on={i < done || undefined} title={s} />)}
      </ol>
      <button type="button" className="ob-skip">Hide tips</button>
    </div>
  );
}

function AppHead() {
  return (
    <div className="pw-head">
      <span className="ap-label ap-muted">Application</span>
      <h1 className="lp-display pw-title">{JOB.role}</h1>
      <span className="ap-label">{JOB.company} · tailored just now</span>
    </div>
  );
}

/** Stand-in for the PDF iframe: the full page is always visible, locked or not. */
function Page() {
  return (
    <div className="pw-sheet" aria-label="Résumé preview">
      <strong className="pw-sheet__name">{PAGE.name}</strong>
      <span className="pw-sheet__contact">{PAGE.contact}</span>
      <span className="pw-sheet__h">Experience</span>
      <span className="pw-sheet__role">Software Intern · Acme Freight · 2025</span>
      <ul>{BULLETS.map((b) => <li key={b}>{b}</li>)}</ul>
      <span className="pw-sheet__h">Skills</span>
      <span className="pw-sheet__role">Java · Spring Boot · Kafka · PostgreSQL</span>
    </div>
  );
}

/** Prices from /pricing. Stripe Checkout is not built yet; picking one only says so. */
function Plans() {
  const [picked, setPicked] = useState<string | null>(null);
  return (
    <div className="pw-plans">
      {PLANS.map((p) => (
        <div key={p.id} className="pw-plan" data-featured={p.featured || undefined}>
          <span className="pw-plan__name">{p.name}{p.featured && <em> · most popular</em>}</span>
          <span className="pw-plan__price"><b>${p.price}</b> {p.per}</span>
          <span className="pw-plan__line">{p.line}</span>
          <button type="button" className={`ap-btn ${p.featured ? 'ap-btn--acid' : 'ap-btn--ghost'} pw-plan__btn`} onClick={() => setPicked(p.name)}>
            Choose {p.name}
          </button>
        </div>
      ))}
      <p className="pw-fine">
        {picked ? `Lab mock: Stripe Checkout for ${picked} would open here. No payment is wired.` : 'Cancel monthly plans any time. Prices in USD. Full details on /pricing.'}
      </p>
    </div>
  );
}

/* ── A: the PDF download is the paid step ── */
function LockPdf() {
  const [open, setOpen] = useState(true);
  return (
    <>
      <Strip done={5} />
      <div className="pw-app">
        <AppHead />
        <div className="pw-split">
          <Page />
          <aside className="pw-side">
            {open ? (
              <section className="pw-lock" aria-labelledby="pw-a-h">
                <span className="ap-label">Your page is ready</span>
                <h2 id="pw-a-h" className="pw-lock__h">Download it with a plan.</h2>
                <p>Everything you built stays free: entries, bullets, tailoring and this preview. The tailored PDF is the paid part.</p>
                <Plans />
                <button type="button" className="ob-skip" onClick={() => setOpen(false)}>Not now, keep editing</button>
              </section>
            ) : (
              <div className="pw-dl">
                <button type="button" className="minibtn pw-dl__btn" disabled>↓ Download PDF</button>
                <span className="pw-fine">Downloads need a plan.</span>
                <button type="button" className="minibtn pw-dl__btn" onClick={() => setOpen(true)}>See plans</button>
              </div>
            )}
          </aside>
        </div>
      </div>
    </>
  );
}

/* ── B: free monthly quota with a meter ── */
function Quota() {
  const [used, setUsed] = useState(0);
  const out = used >= FREE_TAILORS;
  return (
    <>
      <div className="pw-labrow">
        <span className="ap-label ap-muted">Lab: free tailors used</span>
        {[0, 1, 2, 3].map((k) => (
          <button key={k} type="button" className={`minibtn${used === k ? ' is-on' : ''}`} onClick={() => setUsed(k)}>{k}</button>
        ))}
      </div>
      <Strip done={used > 0 ? 6 : 5} />
      <div className="pw-app">
        <AppHead />
        <div className="pw-split">
          <Page />
          <aside className="pw-side">
            <div className="pw-dl">
              <button type="button" className="ap-btn ap-btn--acid pw-dl__main" disabled={out} onClick={() => setUsed((u) => u + 1)}>
                ↓ Download PDF
              </button>
            </div>
            <Meter used={used} />
            {used > 0 && !out && (
              <p className="pw-win" role="status">
                Downloaded. This is the page you’d send. {FREE_TAILORS - used} free tailor{FREE_TAILORS - used === 1 ? '' : 's'} left this month.
              </p>
            )}
            {out && (
              <section className="pw-lock" aria-labelledby="pw-b-h">
                <span className="ap-label">{FREE_TAILORS} of {FREE_TAILORS} free tailors used</span>
                <h2 id="pw-b-h" className="pw-lock__h">That’s this month’s free tailors.</h2>
                <p>More free ones on {RESET}. Or pick a plan to keep applying today. Your preview and edits stay free either way.</p>
                <Plans />
                <div className="pw-free">
                  <span className="ap-label">Or stay free</span>
                  <span>Wait for {RESET}, or self-host AnvilCV and run it on your own AI key.</span>
                </div>
              </section>
            )}
          </aside>
        </div>
      </div>
    </>
  );
}

function Meter({ used }: { used: number }) {
  return (
    <div className="pw-meter">
      <ol aria-hidden>{Array.from({ length: FREE_TAILORS }, (_, i) => <li key={i} data-on={i < used || undefined} />)}</ol>
      <span>{Math.min(used, FREE_TAILORS)} of {FREE_TAILORS} free tailors this month · resets {RESET}</span>
    </div>
  );
}

/* ── C: soft ask in a dialog right after the 3rd approval ── */
function AfterApprove() {
  const [open, setOpen] = useState(true);
  const reopen = useRef<HTMLButtonElement>(null);
  return (
    <>
      <Strip done={4} />
      <div className="pw-app">
        <div className="pw-head">
          <span className="ap-label ap-muted">Project</span>
          <h1 className="lp-display pw-title">Order sync service</h1>
          <span className="ap-label">3 of 3 approved</span>
        </div>
        <ul className="pw-bullets">
          {BULLETS.map((b) => <li key={b}><span className="pw-ok" aria-label="Approved">✓</span>{b}</li>)}
        </ul>
        {!open && <button ref={reopen} type="button" className="minibtn pw-dl__btn" onClick={() => setOpen(true)}>Lab: show the dialog again</button>}
      </div>
      {open && <Dialog onClose={() => setOpen(false)} returnTo={reopen} />}
    </>
  );
}

const FOCUSABLE = 'button:not([disabled]), a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

/** Modal inside the stage: focus moves in, Tab cycles, Esc closes, focus goes back to the trigger. */
function Dialog({ onClose, returnTo }: { onClose: () => void; returnTo: React.RefObject<HTMLButtonElement> }) {
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    box.current?.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    return () => {
      // The reopen button mounts after close; fall back to whatever had focus.
      requestAnimationFrame(() => (returnTo.current ?? prev)?.focus());
    };
  }, [returnTo]);

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') { e.stopPropagation(); onClose(); return; }
    if (e.key !== 'Tab' || !box.current) return;
    const els = [...box.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
    if (!els.length) return;
    const first = els[0], last = els[els.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  };

  return (
    <div className="pw-scrim" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div ref={box} className="pw-dialog" role="dialog" aria-modal="true" aria-labelledby="pw-c-h" aria-describedby="pw-c-d" onKeyDown={onKey}>
        <button type="button" className="pw-x" aria-label="Close" onClick={onClose}>✕</button>
        <span className="ap-label">3 of 3 approved</span>
        <h2 id="pw-c-h" className="pw-lock__h">Your first three bullets are ready.</h2>
        <p id="pw-c-d">Next: paste a job and we fit them to one page. Free covers {FREE_TAILORS} tailored pages a month. Plans cover more.</p>
        <button type="button" className="ap-btn ap-btn--ink pw-cont" data-autofocus onClick={onClose}>Continue free → tailor to a job</button>
        <Plans />
      </div>
    </div>
  );
}
