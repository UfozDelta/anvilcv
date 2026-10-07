import { useEffect, useMemo, useRef, useState } from 'react';
import { Picker } from '../hero/LabHero';
import { NavStrip } from '../app/AppNav';
import { PageTitle, useDemoPage } from '../app/shared';
import { RichText } from '../../components/RichText';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { LAB_CFG } from '../fixtures';
import {
  DISMISSED, isVanity, LAST_RUN, LENS_LABEL, LOOSE, MAX_NEW_STORIES, PROJECT, REPO, RESERVE,
  SOURCE_FIELDS, STORIES, STORY_CAP, type LooseBullet, type RunSummary, type StoryFx, type WStatus, type Wording,
} from './storyFixtures';
import '../../styles/landing.css';
import '../picker.css';
import '../../styles/ledger.css';
import './stories.css';

type Variant = 'lenses' | 'general';
type Scenario = 'healthy' | 'thin' | 'full';

const ALL_LENSES = Object.keys(LENS_LABEL);

const SCENARIOS: { id: Scenario; label: string }[] = [
  { id: 'healthy', label: 'Healthy' },
  { id: 'thin', label: 'Thin' },
  { id: 'full', label: 'Full' },
];

function seed(s: Scenario): { stories: StoryFx[]; loose: LooseBullet[] } {
  if (s === 'thin') return { stories: STORIES.slice(0, 2), loose: [] };
  if (s === 'full') return { stories: [...STORIES, ...RESERVE, ...DISMISSED], loose: LOOSE };
  return { stories: [...STORIES, ...DISMISSED], loose: LOOSE };
}

function initialStatus(): Record<string, WStatus> {
  const out: Record<string, WStatus> = {};
  for (const s of [...STORIES, ...RESERVE, ...DISMISSED]) {
    for (const w of [...s.byLens, ...s.general]) out[w.id] = w.status;
  }
  for (const b of LOOSE) out[b.id] = b.status;
  return out;
}

/** Mirrors BulletTextRules.autoSelectable: a pending vanity count is held back, approved stays. */
const selectable = (status: WStatus, text: string) =>
  status !== 'REJECTED' && (status === 'APPROVED' || !isVanity(text));

const STAGES_A = ['Reading source and repo map slice', 'Finding stories', 'Dropping repeats', 'Writing one wording per story-lens', 'Repair pass', 'Saving'];
const STAGES_B = ['Reading source and repo map', 'Finding stories', 'Dropping repeats', 'Writing one wording per story', 'Repair pass', 'Saving'];
const STAGE_MS = 650;

export function LabStories() {
  const { mountKey, replay } = useDemoPage(false);
  const [variant, setVariant] = useState<Variant>('lenses');

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === '1') setVariant('lenses');
      else if (e.key === '2') setVariant('general');
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="ap-root">
      <NavStrip />
      <StoriesPage key={mountKey} variant={variant} />
      <Picker
        names={['A · Lenses', 'B · General']}
        current={variant === 'lenses' ? 0 : 1}
        onPick={(i) => setVariant(i === 0 ? 'lenses' : 'general')}
        onReplay={replay}
      />
    </div>
  );
}

function StoriesPage({ variant }: { variant: Variant }) {
  const A = variant === 'lenses';
  const [scenario, setScenario] = useState<Scenario>('healthy');
  const [bank, setBank] = useState(() => seed('healthy'));
  const [status, setStatus] = useState(initialStatus);
  const [newIds, setNewIds] = useState<Set<string>>(new Set());
  const [picked, setPicked] = useState<Set<string>>(new Set(LAST_RUN.lenses));
  const [run, setRun] = useState<{ step: number } | null>(null);
  const [summary, setSummary] = useState<RunSummary>(LAST_RUN);
  const timer = useRef<number>();

  const wordings = (s: StoryFx): Wording[] => (A ? s.byLens : s.general);
  const live = (s: StoryFx) => wordings(s).some(w => status[w.id] !== 'REJECTED');

  const liveStories = bank.stories.filter(live);
  const dismissed = bank.stories.filter(s => !live(s));
  const usable =
    liveStories.filter(s => wordings(s).some(w => selectable(status[w.id], w.text))).length
    + bank.loose.filter(b => selectable(status[b.id], b.text)).length;
  const room = Math.max(0, Math.min(MAX_NEW_STORIES, STORY_CAP - liveStories.length));
  const full = room === 0;
  const stages = A ? STAGES_A : STAGES_B;
  const running = run !== null;

  function switchScenario(s: Scenario) {
    window.clearTimeout(timer.current);
    setRun(null);
    setScenario(s);
    setBank(seed(s));
    setStatus(initialStatus());
    setNewIds(new Set());
    setSummary(LAST_RUN);
  }

  function setOne(id: string, st: WStatus) {
    setStatus(m => ({ ...m, [id]: st }));
  }

  function restore(s: StoryFx) {
    setStatus(m => {
      const next = { ...m };
      for (const w of wordings(s)) next[w.id] = 'PENDING';
      return next;
    });
  }

  // A demo clock, not a real job. A full bank short-circuits exactly as generateStories does:
  // no model call, one line saying why.
  function generate() {
    if (full) {
      setSummary({
        label: 'This run', lenses: A ? [...picked] : null, bankFull: true, found: 0, kept: [],
        overlaps: [], noEvidence: 0, written: 0, repairs: [], nearDuplicates: 0,
      });
      return;
    }
    setRun({ step: 0 });
  }

  useEffect(() => {
    if (!run) return;
    if (run.step < stages.length) {
      timer.current = window.setTimeout(() => setRun({ step: run.step + 1 }), STAGE_MS);
      return () => window.clearTimeout(timer.current);
    }
    const have = new Set(bank.stories.map(s => s.id));
    const add = RESERVE.filter(s => !have.has(s.id)).slice(0, Math.min(2, room));
    setBank(b => ({ ...b, stories: [...add, ...b.stories] }));
    setNewIds(new Set(add.map(s => s.id)));
    setSummary({
      label: 'This run',
      lenses: A ? [...picked] : null,
      bankFull: false,
      found: add.length + 2,
      kept: add.map(s => s.title),
      overlaps: [{ title: 'Board keeps working without the feed', repeats: 'Board stays live when the operator feed goes silent' }],
      noEvidence: 1,
      written: add.reduce((n, s) => n + wordings(s).length, 0),
      repairs: [{ kind: 'Padding', note: 'filler clause “to improve reliability” cut, then rewritten' }],
      nearDuplicates: 0,
    });
    setRun(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run]);

  const togglePick = (l: string) =>
    setPicked(p => {
      const next = new Set(p);
      next.has(l) ? next.delete(l) : next.add(l);
      return next;
    });

  const order = [...liveStories].sort((x, y) => Number(newIds.has(y.id)) - Number(newIds.has(x.id)));

  return (
    <div className="shell ap-page sb-page">
      <PageTitle
        title={PROJECT.name}
        count={<>{PROJECT.line}. <strong>{liveStories.length} stories</strong> in the bank; a resume prints one wording of each.</>}
        actions={
          <div className="sb-seg" role="group" aria-label="Demo bank state">
            <span className="sb-seg__k">Demo bank</span>
            {SCENARIOS.map(s => (
              <button key={s.id} type="button" aria-pressed={scenario === s.id} onClick={() => switchScenario(s.id)}>{s.label}</button>
            ))}
          </div>
        }
      />

      <p className="sb-variant">
        Viewing <strong>{A ? 'A · Lenses' : 'B · General'}</strong>:{' '}
        {A
          ? 'you tick lenses, stories are tagged with them, one wording per story-lens.'
          : 'no lens picking, one general wording per story (sometimes a short/long pair), lenses only as detected tags.'}
        <span className="sb-variant__keys"> Switch at the bottom, or press 1 / 2.</span>
      </p>

      <Health live={liveStories.length} usable={usable} dismissed={dismissed.length} loose={bank.loose.length} newCount={newIds.size} />

      <div className="sb-grid">
        <main className="sb-main">
          <h2 className="sb-h2">Stories <span>{liveStories.length}</span></h2>
          <p className="sb-sub">
            A story is one piece of work. Its wordings say the same thing different ways, so the
            resume picks at most one per story.
          </p>
          {order.length === 0 && <p className="sb-empty">No stories yet. Generate to find them.</p>}
          <div className="sb-stories">
            {order.map(s => (
              <StoryCard key={s.id} story={s} variant={variant} status={status} onStatus={setOne} isNew={newIds.has(s.id)} />
            ))}
          </div>

          {bank.loose.length > 0 && (
            <section className="sb-aside">
              <h2 className="sb-h2">Older bullets, no story <span>{bank.loose.length}</span></h2>
              <p className="sb-sub">
                Written before stories existed. Each counts as its own story, and the next run is
                told not to repeat them.
              </p>
              {bank.loose.map(b => (
                <WordingRow key={b.id} id={b.id} text={b.text} label="No story" status={status[b.id]} onStatus={setOne} />
              ))}
            </section>
          )}

          {dismissed.length > 0 && (
            <details className="sb-aside sb-dismissed">
              <summary>
                <span className="sb-h2">Dismissed <span>{dismissed.length}</span></span>
                <span className="sb-sub">Every wording rejected. Kept so the next run won’t find them again.</span>
              </summary>
              {dismissed.map(s => (
                <div className="sb-dis" key={s.id}>
                  <div>
                    <div className="sb-dis__title">{s.title}</div>
                    <div className="sb-dis__n">{wordings(s).length} rejected {wordings(s).length === 1 ? 'wording' : 'wordings'}</div>
                  </div>
                  <button type="button" className="ap-btn ap-btn--ghost sb-btn-sm" onClick={() => restore(s)}>Restore</button>
                </div>
              ))}
            </details>
          )}
        </main>

        <aside className="sb-side">
          <section className="sb-panel" aria-labelledby="sb-gen">
            <h2 className="sb-h2" id="sb-gen">Generate</h2>

            <div className="sb-in">
              <div className="sb-in__k">Source fields <span>{SOURCE_FIELDS.filter(f => f.filled).length} of {SOURCE_FIELDS.length} filled</span></div>
              <div className="sb-chips">
                {SOURCE_FIELDS.map(f => (
                  <span key={f.label} className={`sb-chip ${f.filled ? '' : 'sb-chip--empty'}`}>
                    {f.label}{!f.filled && <i> empty</i>}
                  </span>
                ))}
              </div>
            </div>

            <div className="sb-in">
              <div className="sb-in__k">Repo map <span>@{REPO.sha}</span></div>
              <ul className="sb-repo">
                {REPO.subsystems.map(sub => {
                  const inFocus = !A || picked.size === 0 || sub.lenses.some(l => picked.has(l));
                  return (
                    <li key={sub.name} data-in={inFocus}>
                      <code>{sub.name}</code>
                      <span>{inFocus ? 'sent' : 'left out'}</span>
                    </li>
                  );
                })}
              </ul>
              <p className="sb-note">
                {A
                  ? 'Only subsystems tagged with a ticked lens are sent, so lenses also decide what the model reads.'
                  : 'No lenses to slice by: the most central subsystems are sent.'}
              </p>
            </div>

            <div className="sb-in">
              <div className="sb-in__k">Lenses</div>
              {A ? (
                <>
                  <div className="na-lenses sb-lenses" role="group" aria-label="Lenses to generate for">
                    {ALL_LENSES.map(l => (
                      <button key={l} type="button" aria-pressed={picked.has(l)} onClick={() => togglePick(l)} disabled={running}>
                        {LENS_LABEL[l]}
                      </button>
                    ))}
                  </div>
                  <p className="sb-note">A lens no story fits gets no wordings. Nothing is stretched to cover it.</p>
                </>
              ) : (
                <p className="sb-note">Nothing to pick. Lenses are detected per story and shown as tags.</p>
              )}
            </div>

            <div className="sb-go">
              {running ? (
                <div className="na-run">
                  <span className="na-run__label" role="status">
                    {stages[Math.min(run.step, stages.length - 1)]}… <span>{Math.min(run.step + 1, stages.length)}/{stages.length}</span>
                  </span>
                  <span className="na-run__bar" role="progressbar" aria-label="Generate progress" aria-valuemin={0} aria-valuemax={stages.length} aria-valuenow={run.step}>
                    <i key={run.step} style={{ animationDuration: `${STAGE_MS}ms` }} />
                  </span>
                </div>
              ) : (
                <>
                  <button
                    type="button"
                    className="ap-btn ap-btn--acid sb-go__btn"
                    onClick={generate}
                    disabled={A && picked.size === 0}
                  >Find new stories</button>
                  <p className="sb-note">
                    {A && picked.size === 0
                      ? 'Tick at least one lens.'
                      : full
                        ? `Bank is full (${STORY_CAP}). A run makes no model call.`
                        : `Room for up to ${room} new ${room === 1 ? 'story' : 'stories'} (${STORY_CAP} cap, ${MAX_NEW_STORIES} per run).`}
                  </p>
                </>
              )}
            </div>

            <RunCard summary={summary} />
          </section>
        </aside>
      </div>

      <ReviewNotes variant={variant} />
    </div>
  );
}

function Health({ live, usable, dismissed, loose, newCount }: { live: number; usable: number; dismissed: number; loose: number; newCount: number }) {
  const thin = usable < 3;
  const full = live >= STORY_CAP;
  return (
    <section className="sb-health" aria-label="Bank health">
      <div className="sb-meter" role="img" aria-label={`${live} of ${STORY_CAP} story slots used`}>
        {Array.from({ length: STORY_CAP }, (_, i) => (
          <span key={i} data-state={i < newCount ? 'new' : i < live ? 'live' : 'free'} />
        ))}
      </div>
      <dl className="sb-stats">
        <div><dt>Story slots</dt><dd><strong>{live}</strong> of {STORY_CAP}</dd></div>
        <div><dt>Usable on a resume</dt><dd><strong className={thin ? 'is-bad' : ''}>{usable}</strong></dd></div>
        <div><dt>Older, no story</dt><dd><strong>{loose}</strong></dd></div>
        <div><dt>Dismissed</dt><dd><strong>{dismissed}</strong></dd></div>
      </dl>
      <p className="sb-health__legend">
        Usable counts stories with a wording the resume may pick on its own, plus each older
        bullet. Dismissed stories don’t take a slot.
      </p>
      {thin && (
        <p className="sb-warn" role="alert">
          Only {usable} usable {usable === 1 ? 'story' : 'stories'}. This entry may print short or
          repeat itself. Generate more, or fill in the empty source fields first.
        </p>
      )}
      {full && (
        <p className="sb-warn sb-warn--full">
          Bank full. Reject every wording of a story you don’t want to free its slot.
        </p>
      )}
    </section>
  );
}

function StoryCard({ story, variant, status, onStatus, isNew }: {
  story: StoryFx;
  variant: Variant;
  status: Record<string, WStatus>;
  onStatus: (id: string, s: WStatus) => void;
  isNew: boolean;
}) {
  const A = variant === 'lenses';
  const ws = A ? story.byLens : story.general;
  const active = ws.filter(w => status[w.id] !== 'REJECTED');
  const pairForms = ws.some(w => w.form);

  let rule: string;
  if (active.length <= 1) rule = 'Prints this wording';
  else if (A) rule = `Prints one of ${active.length}: the job decides which, approved first`;
  else rule = 'Prints one: long when the page has room, short when it is tight';

  return (
    <article className="sb-card" data-new={isNew || undefined}>
      <header className="sb-card__head">
        <h3 className="lp-display sb-card__title">{story.title}</h3>
        {isNew && <span className="sb-new">New</span>}
      </header>

      {A ? (
        <div className="sb-tagrow">
          <span className="sb-tagrow__k">Tagged</span>
          {story.lenses.map(l => <span key={l} className="sb-lens">{LENS_LABEL[l]}</span>)}
        </div>
      ) : (
        <div className="sb-tagrow sb-tagrow--detected">
          <span className="sb-tagrow__k">Detected</span>
          {story.lenses.map(l => <span key={l} className="sb-dtag">{LENS_LABEL[l]}</span>)}
        </div>
      )}

      {story.evidence.length > 0 && (
        <ul className="sb-evidence" aria-label="Evidence from the source">
          {story.evidence.map(e => (
            <li key={e.quote}><q>{e.quote}</q><span>{e.from}</span></li>
          ))}
        </ul>
      )}

      <div className="sb-wordings" data-multi={active.length > 1 || undefined}>
        <p className="sb-rule">{rule}</p>
        {ws.map(w => (
          <WordingRow
            key={w.id}
            id={w.id}
            text={w.text}
            label={A ? `${LENS_LABEL[w.lens ?? ''] ?? 'General'} wording` : pairForms ? (w.form === 'short' ? 'Short' : 'Long') : 'General'}
            status={status[w.id]}
            onStatus={onStatus}
          />
        ))}
      </div>
    </article>
  );
}

const STATUS_BTNS: { s: WStatus; label: string }[] = [
  { s: 'PENDING', label: 'Pending' },
  { s: 'APPROVED', label: 'Approve' },
  { s: 'REJECTED', label: 'Reject' },
];

function WordingRow({ id, text, label, status, onStatus }: {
  id: string; text: string; label: string; status: WStatus; onStatus: (id: string, s: WStatus) => void;
}) {
  const fit = fitOf(text, LAB_CFG);
  const vanity = isVanity(text);
  return (
    <div className="sb-w" data-status={status}>
      <div className="sb-w__top">
        <span className="sb-w__label">{label}</span>
        <div className="sb-status" role="group" aria-label="Wording status">
          {STATUS_BTNS.map(b => (
            <button key={b.s} type="button" aria-pressed={status === b.s} data-s={b.s} onClick={() => onStatus(id, b.s)}>
              {status === b.s && b.s !== 'PENDING' ? (b.s === 'APPROVED' ? 'Approved' : 'Rejected') : b.label}
            </button>
          ))}
        </div>
      </div>
      <p className="sb-w__text"><RichText text={text} /></p>
      <div className="sb-w__meta">
        <span className={needsRefit(fit) ? 'is-bad' : ''} title={fitHint(text, LAB_CFG) || undefined}>
          {FIT_LABEL[fit]} · {charCount(text)}c · {estimatedLines(text)}L
        </span>
        {needsRefit(fit) && <span className="sb-flag">Wraps awkwardly, refit</span>}
        {vanity && (
          <span className="sb-flag">
            Activity count{status === 'APPROVED' ? ', kept because you approved it' : ', held back from auto-pick'}
          </span>
        )}
      </div>
    </div>
  );
}

function RunCard({ summary: s }: { summary: RunSummary }) {
  return (
    <div className="sb-run" aria-live="polite">
      <div className="sb-in__k">{s.label}</div>
      {s.bankFull ? (
        <p className="sb-run__full">Bank full ({STORY_CAP}). Nothing generated, no model call. Reject a story’s wordings to make room.</p>
      ) : (
        <>
          <p className="sb-run__line">
            {s.lenses ? <>Lenses: {s.lenses.map(l => LENS_LABEL[l]).join(', ')}</> : 'No lenses picked'}
          </p>
          <dl className="sb-run__dl">
            <div><dt>Stories found</dt><dd>{s.found}</dd></div>
            <div><dt>Kept as new</dt><dd>{s.kept.length}</dd></div>
            <div><dt>Dropped, repeats the bank</dt><dd>{s.overlaps.length}</dd></div>
            <div><dt>Dropped, no quote in the source</dt><dd>{s.noEvidence}</dd></div>
            <div><dt>Wordings written</dt><dd>{s.written}</dd></div>
            <div><dt>Near-duplicates dropped</dt><dd>{s.nearDuplicates}</dd></div>
          </dl>
          {s.overlaps.map(o => (
            <p key={o.title} className="sb-run__note">“{o.title}” overlapped “{o.repeats}”.</p>
          ))}
          {s.repairs.map(r => (
            <p key={r.note} className="sb-run__note"><b>{r.kind} repaired:</b> {r.note}.</p>
          ))}
        </>
      )}
    </div>
  );
}

const NOTES: { topic: string; a: string; b: string }[] = [
  {
    topic: 'Effort before a run',
    a: 'Asks for a choice up front. Users guess a taxonomy, and a wrong guess silently narrows what the model reads.',
    b: 'One button. Nothing to get wrong before the first result.',
  },
  {
    topic: 'Wordings per story',
    a: 'One per tagged lens, so a 2-lens story gets two angles. More to review, more for the job match to choose from.',
    b: 'One, or a short/long pair. Half the review load. The angle is fixed at write time.',
  },
  {
    topic: 'Job fit',
    a: 'A backend posting can pick the backend wording of a story. This is the one thing lenses buy.',
    b: 'Ranking still scores the text against the posting, but every job sees the same sentence.',
  },
  {
    topic: 'Repo map',
    a: 'Ticked lenses pick which subsystems are sent (lensFocus). Lenses do two jobs at once.',
    b: 'Needs its own rule for which subsystems to send, e.g. the most central ones.',
  },
  {
    topic: 'Backend change',
    a: 'None. This is what generateBank does today.',
    b: 'Small. A single-lens story already gets a 1-line and a 2-line wording. B is that rule for every story; findStories keeps tagging lenses.',
  },
];

function ReviewNotes({ variant }: { variant: Variant }) {
  return (
    <section className="sb-review" aria-labelledby="sb-review-h">
      <h2 className="sb-h2" id="sb-review-h">Design review: lenses or not</h2>
      <p className="sb-sub">The open question. Both columns run on the same bank above.</p>
      <div className="sb-table" role="table">
        <div className="sb-table__row sb-table__row--head" role="row">
          <span role="columnheader" />
          <span role="columnheader" data-on={variant === 'lenses' || undefined}>A · Lenses</span>
          <span role="columnheader" data-on={variant === 'general' || undefined}>B · General</span>
        </div>
        {NOTES.map(n => (
          <div className="sb-table__row" role="row" key={n.topic}>
            <span role="rowheader">{n.topic}</span>
            <span role="cell" data-on={variant === 'lenses' || undefined}><i>A</i>{n.a}</span>
            <span role="cell" data-on={variant === 'general' || undefined}><i>B</i>{n.b}</span>
          </div>
        ))}
      </div>
      <p className="sb-review__lean">
        How to decide: count how often selection prints a story’s second lens wording instead
        of its first. If that is rare, lenses cost review time without changing the page.
      </p>
    </section>
  );
}
