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
