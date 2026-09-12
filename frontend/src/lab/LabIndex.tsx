import { Link } from 'react-router-dom';
import { LabChrome } from './LabChrome';

/**
 * The lab is a log, not a menu. Every redesign pass gets one entry, newest first,
 * carrying its prototypes and what it changed. Entries stay after they ship so the
 * reasoning behind a shipped screen is still readable months later — add a new
 * entry rather than editing an old one.
 */
type Proto = { to: string; name: string; line: string };

type Entry = {
  /** ISO date the pass was done — sorts the timeline and renders the rail label. */
  iso: string;
  title: string;
  status: 'shipped' | 'prototype' | 'abandoned';
  summary: React.ReactNode;
  protos: Proto[];
  /** Real routes the pass landed on, once it shipped. */
  shipped?: { to: string; label: string }[];
  fixes: string[];
  principles?: string[];
};

const ENTRIES: Entry[] = [
  {
    iso: '2026-09-12',
    title: 'Masthead nav rework',
    status: 'prototype',
    summary: (
      <>
        Prompted by a real bug: merging Projects and Experiences behind one nav item left the
        page keeping its own in-page kind-switch too — the same choice offered twice. Landed on
        keeping them as separate nav items and routes; this pass fixes what made the nav itself
        fragile instead.
      </>
    ),
    protos: [
      {
        to: '/lab/masthead',
        name: 'Masthead nav rework',
        line: 'Nav numbering derived from array position; settings menu split into Account / Tools.',
      },
    ],
    fixes: [
      'Nav item numbers (00, 01, 02…) now come from array position, not a typed-in digit — '
        + 'adding, removing, or reordering an item can no longer leave a gap or a duplicate',
      'Active-state matching is config-driven, one shared function over the nav array, instead '
        + 'of a one-off pathname.startsWith() check bolted onto a single link',
      'Settings dropdown splits Account (log out) from Tools (Upload Resume, Docs) — previously '
        + 'one flat list mixing both',
    ],
    principles: [
      'Anything that can desync when routes change (numbering, active-state) should be derived, '
        + 'never hand-typed per link.',
    ],
  },
  {
    iso: '2026-09-11',
    title: 'Projects & Experiences rework',
    status: 'prototype',
    summary: (
      <>
        Same audit as the Applications pass, pointed at the two oldest screens in the app. Not
        promoted yet — prototype only, reusing the primitives (<code>statrow</code>,{' '}
        <code>tabs</code>, the row menu) that already shipped from the Applications rework
        rather than inventing new ones.
      </>
    ),
    protos: [
      {
        to: '/lab/projects',
        name: 'Projects list v2',
        line: 'Projects and Experiences share one list — kind switch, search, sort, bullet counts.',
      },
      {
        to: '/lab/project-detail',
        name: 'Projects bank v2',
        line: 'The bullet bank editor — one health strip, generate/info moved behind tabs.',
      },
    ],
    fixes: [
      'Positional index (01, 02…) removed — same issue as the Applications list',
      'Delete moved off the native window.confirm() dialog into a menu with an in-place confirm',
      'Each row now shows how many bullets are in its bank — previously invisible until opened',
      'Search and sort added to a list that had neither',
      '"⬡ repo cached" faint inline text replaced with a real badge',
      'Edit Info, Context, the lens picker, filter pills and the bank itself — six panels always '
        + 'stacked in one column — now three tabs, and only one is a lens picker, revealed on demand',
      'A single health strip (bank size, categories covered, average line cost, context '
        + 'completeness) replaces numbers scattered across the page',
      'Projects and Experiences, previously two near-identical page files, share one list '
        + 'component with a kind switch',
      'Generate tab: each lens now badges how many bullets it already has, so picking one is a '
        + 'choice instead of a guess',
      'Generate tab: select-empty-lenses and clear shortcuts added next to the one-by-one toggle',
      'Generate tab: an empty Context nudges you to fill it before generating, with a jump link',
      'Generate tab: the time estimate now comes with an expected bullet-count range',
      'Generate tab rebuilt again: the 8-category lens picker is gone entirely. It asked users '
        + 'to guess a taxonomy the backend barely uses downstream (category is a sort key, not a '
        + 'matching signal) and the lenses overlap by construction — idempotency shows up in '
        + 'backend, security, and systems lens prompts at once',
      'Generate tab tried "no picker" evidence coverage — rejected. It read as an audit '
        + '(red NO BULLET YET rows) before producing anything, and gated on filling context first',
      'Generate tab rebuilt a third time: lenses kept, made dynamic per project instead of the '
        + "fixed 8. One lens per technology the project's own tech stack names, one per "
        + 'narrative field actually filled in (Scale & impact, Hardest problem…) — a different '
        + 'project gets a different list, nothing is a guessed taxonomy',
      'Generate tab: added a free-text "describe one yourself" line — type a moment, get one '
        + 'bullet for it, no lens required at all',
      'Secondary categories (tried, then reverted): multi-category tagging solved a label-'
        + 'accuracy problem for a label almost nothing downstream reads — dropped in favor of '
        + 'the single primary category exactly as it was',
      'Custom-bullet line now suggests a target instead of sitting empty: the least-covered '
        + 'lens for this project surfaces as a dismissible nudge ("Nothing covers Kubernetes '
        + 'yet") with a one-click starter that pre-fills the box — a recommendation, not '
        + 'another audit to clear',
      'Generate tab now leads with the 5 names as an actual checkbox picker (Backend/ML/'
        + 'DevOps/Infra/Frontend), same interaction as the original fixed picker, generating a '
        + 'generic bullet per bucket checked — the dynamic per-project lens list moved below it '
        + 'as a smaller, denser pill row and kept its own default (nothing picked there still '
        + 'means "generate from all of them"), independent of the buckets above',
      'Info & Context: the paste-extract sidebar (parse & auto-fill from the Context Extractor '
        + 'output) is back — dropped in the first prototype pass, now a sticky sidebar instead of '
        + 'a slide-over drawer',
      'Info & Context: Experiences and Projects now get separate forms — Projects never had '
        + 'title/company/location/dates, so those inputs no longer render empty for them',
      'Bullets tab: the full workflow ported over — approve/reject, add, edit, delete, refit, '
        + 'sort by category or date, and a page-budget PDF preview',
      'Approve/reject is a labelled toggle (✓ APPROVED / PENDING), not an underlined word',
      'Delete moved into a row menu with an in-place confirm, off the bare DELETE button',
      'Each bullet shows its real length-fit band (ONE_LINE, TOO_LONG, DEAD ZONE…) as a badge, '
        + 'not just a line count',
      'REFIT shows how many bullets are off-band before you run it, not just a bare count after',
      'Generate now runs a visible progress bar and drops new bullets straight into the Bank tab',
    ],
    principles: [
      'Reuse the primitives a prior pass already validated before inventing new ones.',
      'A count of related items (bullets, applications) belongs on the row that owns them.',
    ],
  },
  {
    iso: '2026-09-11',
    title: 'Applications rework',
    status: 'shipped',
    summary: (
      <>
        The Applications index and editor, audited and rebuilt. Three prototypes on
        placeholder data, then promoted onto the real pages — the primitives they invented
        (labelled stats, meters, the IN/OUT bullet row, the tabbed viewer) now live in{' '}
        <code>styles/ui.css</code> and ship.
      </>
    ),
    protos: [
      {
        to: '/lab/list',
        name: 'List v2',
        line: 'The applications index — search, sort, counts, in-place status, safe delete.',
      },
      {
        to: '/lab/detail',
        name: 'Detail v2',
        line: 'The editor — one health strip, one tabbed viewer, a real line budget.',
      },
      {
        to: '/lab/rows',
        name: 'Bullet row A/B',
        line: 'The shipped row and the proposed row, side by side on shared state.',
      },
    ],
    // The detail page has no standalone route without an id, so the list is the way in.
    shipped: [
      { to: '/applications', label: 'Applications → open any row for the detail rework' },
    ],
    fixes: [
      'FIT and PAGE now say what they measure, with a one-line legend',
      'Positional index (01, 02…) removed — it renumbered on every filter',
      'Delete moved out of the row link into a menu with in-place confirm',
      'Filter tabs carry counts from the whole set, not the filtered one',
      'Skeleton rows instead of blanking the list to the word LOADING',
      'Four health numbers in three places → one labelled strip at the top',
      'Five stacked panels in one column → four tabs with attention badges',
      'Page budget promoted from small text to the meter it always was',
      'Section numbering (03.A … 03.D2 … 03.E) dropped for plain headings',
      '“~4L · 3/7” spelled out as “3 of 7 included · about 4 lines”',
      'Fit and Recruiter panels merged into one Review tab, ordered by what to do',
      'Include/exclude is a labelled button, not a click on a rank number',
      'Excluded rows keep full contrast — you can still read what you removed',
      'Each row shows what it costs against the page budget',
    ],
    principles: [
      'No number without the word for what it measures, in the place it is shown.',
      'State is written out, never encoded in opacity alone.',
      'One primary action per screen, visually distinct at rest — not only on hover.',
      'Destructive actions are never the same weight as navigation.',
      'Explanations live on the page, not in title tooltips.',
      'Panels compete for one viewport: put them behind tabs and badge the ones that need attention.',
    ],
  },
];

const STATUS_LABEL: Record<Entry['status'], string> = {
  shipped: 'Shipped',
  prototype: 'Prototype only',
  abandoned: 'Abandoned',
};

function fmt(iso: string) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  });
}

export function LabIndex() {
  const entries = [...ENTRIES].sort((a, b) => b.iso.localeCompare(a.iso));

  return (
    <LabChrome
      title="UI lab"
      note={
        <>
          A dated log of redesign passes. Each entry keeps its prototypes running on
          placeholder data — no login, no backend, nothing writes anywhere. Entries stay up
          after they ship, so the reasoning behind a screen outlives the pull request.
        </>
      }
    >
      <div className="tl">
        {entries.map(e => (
          <article className="tl__entry" key={e.iso + e.title}>
            <div className="tl__rail">
              <span className="tl__dot" aria-hidden />
              <time className="tl__date" dateTime={e.iso}>{fmt(e.iso)}</time>
            </div>

            <div className="tl__body">
              <div className="tl__head">
                <h2 className="display tl__title">{e.title}</h2>
                <span className={`tl__status tl__status--${e.status}`}>{STATUS_LABEL[e.status]}</span>
              </div>

              <p className="tl__summary">{e.summary}</p>

              <div className="eyebrow" style={{ marginBottom: 6 }}>Prototypes</div>
              <div className="tl__protos">
                {e.protos.map(p => (
                  <Link key={p.to} to={p.to} className="tl__proto">
                    <div className="tl__proto-name">{p.name} <span className="eyebrow">open →</span></div>
                    <div className="tl__proto-line">{p.line}</div>
                  </Link>
                ))}
              </div>

              {e.shipped && e.shipped.length > 0 && (
                <>
                  <div className="eyebrow" style={{ margin: '16px 0 6px' }}>Landed on</div>
                  <div className="tl__landed">
                    {e.shipped.map(sh => (
                      <Link key={sh.label} to={sh.to} className="minibtn" style={{ textDecoration: 'none' }}>
                        {sh.label}
                      </Link>
                    ))}
                  </div>
                </>
              )}

              <div className="eyebrow" style={{ margin: '16px 0 6px' }}>What changed ({e.fixes.length})</div>
              <ul className="tl__list">
                {e.fixes.map(f => <li key={f}>{f}</li>)}
              </ul>

              {e.principles && e.principles.length > 0 && (
                <>
                  <div className="eyebrow" style={{ margin: '16px 0 6px' }}>Principles this pass set</div>
                  <ul className="tl__list tl__list--principles">
                    {e.principles.map(p => <li key={p}>{p}</li>)}
                  </ul>
                </>
              )}
            </div>
          </article>
        ))}
      </div>
    </LabChrome>
  );
}
