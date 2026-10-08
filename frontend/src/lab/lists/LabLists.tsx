/* /lab/lists — Projects and Experiences lists on the story model (story count, lens tags, Generate). */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { NavStrip } from '../app/AppNav';
import { EXIT, ago } from '../../components/ledger/shared';
import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';
import { STORY_CAP } from '../stories/storyFixtures';
import { LENSES, LENS_OF, type Lens } from '../story-flow/data';
import { Spin } from '../workspace/parts';
import { EXPERIENCES, PROJECTS, type Item } from './data';
import '../../styles/landing.css';
import '../../styles/ledger.css';
import '../project-split/split.css';
import '../story-flow/flow.css';
import './lists.css';

type Kind = 'project' | 'exp';
type Sort = 'recent' | 'most' | 'fewest';
const SORTS: { key: Sort; label: string }[] = [
  { key: 'recent', label: 'Recent' },
  { key: 'most', label: 'Most stories' },
  { key: 'fewest', label: 'Fewest stories' },
];
const OPEN = '/lab/story-flow';

/** Per lens: stories that have it, and its wordings. */
function lensCounts(it: Item) {
  const m = new Map<Lens, { stories: number; bullets: number }>();
  for (const s of it.stories) {
    for (const l of new Set(s.lenses)) {
      const c = m.get(l) ?? { stories: 0, bullets: 0 };
      c.stories++;
      c.bullets += s.lenses.filter(x => x === l).length;
      m.set(l, c);
    }
  }
  return m;
}
const bulletsOf = (it: Item) => it.stories.reduce((n, s) => n + s.lenses.length, 0);
const okOf = (it: Item) => it.stories.reduce((n, s) => n + s.ok, 0);

export function LabLists() {
  const [items, setItems] = useState<Item[]>([...PROJECTS, ...EXPERIENCES]);
  const [kind, setKind] = useState<Kind>('project');
  const [lens, setLens] = useState<Lens | null>(null);
  const [sort, setSort] = useState<Sort>('recent');
  const [busy, setBusy] = useState<Set<string>>(new Set());
  const [fresh, setFresh] = useState<Set<string>>(new Set());
  const timers = useRef<number[]>([]);
  const reduced = usePrefersReducedMotion();
  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const counts = { project: items.filter(i => i.kind === 'project').length, exp: items.filter(i => i.kind === 'exp').length };
  const ofKind = items.filter(i => i.kind === kind);
  const shown = useMemo(() => {
    const out = items.filter(i => i.kind === kind && (!lens || i.stories.some(s => s.lenses.includes(lens))));
    const by: Record<Sort, (a: Item, b: Item) => number> = {
      recent: (a, b) => (b.generatedAt ?? '').localeCompare(a.generatedAt ?? ''),
      most: (a, b) => b.stories.length - a.stories.length,
      fewest: (a, b) => a.stories.length - b.stories.length,
    };
    return [...out].sort(by[sort]);
  }, [items, kind, lens, sort]);

  /** Simulated Generate: after a beat, one new story with one wording in its least-covered lens. */
  function generate(id: string) {
    const it = items.find(i => i.id === id);
    if (!it || busy.has(id) || it.stories.length >= STORY_CAP) return;
    setBusy(b => new Set(b).add(id));
    timers.current.push(window.setTimeout(() => {
      setItems(xs => xs.map(x => {
        if (x.id !== id || x.stories.length >= STORY_CAP) return x;
        const c = lensCounts(x);
        const pick = [...LENSES].sort((a, b) => (c.get(a.slug)?.stories ?? 0) - (c.get(b.slug)?.stories ?? 0))[0].slug;
        return { ...x, generatedAt: new Date().toISOString(), stories: [{ lenses: [pick], ok: 0 }, ...x.stories] };
      }));
      setBusy(b => { const n = new Set(b); n.delete(id); return n; });
      setFresh(f => new Set(f).add(id));
      timers.current.push(window.setTimeout(() => setFresh(f => { const n = new Set(f); n.delete(id); return n; }), 1800));
    }, 900));
  }

  const total = ofKind.reduce((n, i) => n + i.stories.length, 0);
  const isP = kind === 'project';

  return (
    <div className="ap-root">
      <NavStrip />
      <div className="shell ap-page ll-page">
        <header className="ap-head">
          <div>
            <h1 className="lp-display ap-title">{isP ? 'Projects' : 'Experiences'}</h1>
            <p className="ap-count"><strong>{ofKind.length}</strong> {isP ? 'projects' : 'roles'} · <strong>{total}</strong> stories</p>
          </div>
        </header>

        <div className="ap-tools ll-tools">
          <div className="la-tabs" role="group" aria-label="List">
            {(['project', 'exp'] as const).map(k => (
              <button key={k} type="button" aria-pressed={kind === k} onClick={() => setKind(k)}>
                {k === 'project' ? 'Projects' : 'Experiences'} <span className="la-tabs__n">{counts[k]}</span>
              </button>
            ))}
          </div>
          <select className="la-sort" value={sort} aria-label="Sort" onChange={e => setSort(e.target.value as Sort)}>
            {SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
          </select>
        </div>

        <div className="ll-lenses" role="group" aria-label="Filter by lens">
          <button type="button" className="ll-lf" aria-pressed={lens === null} onClick={() => setLens(null)}>All</button>
          {LENSES.map(l => (
            <button key={l.slug} type="button" className="ll-lf" aria-pressed={lens === l.slug} title={l.tip}
              onClick={() => setLens(v => (v === l.slug ? null : l.slug))}>{l.name}</button>
          ))}
        </div>

        <div className="ld-table" role="table" aria-label={isP ? 'Projects' : 'Experiences'}>
          <div className="ld-row ll-row ll-row--head ld-row--head" data-kind={kind} role="row">
            <span role="columnheader">{isP ? 'Project' : 'Role'}</span>
            <span role="columnheader">Stories</span>
            <span role="columnheader" className="ld-hide-sm">Lenses</span>
            <span role="columnheader" className="ld-hide-sm">Bullets</span>
            {isP && <span role="columnheader" className="ld-hide-sm">Repo</span>}
            <span role="columnheader" className="ld-hide-sm">Generated</span>
            <span role="columnheader"><span className="sr-only">Actions</span></span>
          </div>
          <AnimatePresence initial={false}>
            {shown.map(it => (
              <motion.div
                key={it.id}
                className="ld-row ll-row"
                data-kind={kind}
                data-new={fresh.has(it.id) || undefined}
                role="row"
                layout={reduced ? false : 'position'}
                initial={reduced ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0, transition: EXIT }}
                transition={EXIT}
              >
                <Row it={it} busy={busy.has(it.id)} fresh={fresh.has(it.id)} onGenerate={() => generate(it.id)} />
              </motion.div>
            ))}
          </AnimatePresence>
          {shown.length === 0 && (
            <div className="ap-nomatch">None with {lens && LENS_OF[lens].name}. <button type="button" onClick={() => setLens(null)}>Clear</button></div>
          )}
        </div>
      </div>
    </div>
  );
}

function Row({ it, busy, fresh, onGenerate }: { it: Item; busy: boolean; fresh: boolean; onGenerate: () => void }) {
  const n = it.stories.length;
  const full = n >= STORY_CAP;
  const bullets = bulletsOf(it), ok = okOf(it);
  const lenses = <Lenses it={it} />;
  const bul = <span className="ll-bul" title={`${bullets} bullets, ${ok} approved`}>{bullets}<span className="ll-ok"> · {ok}✓</span></span>;
  const gen = <span className="ld-edited" title={it.generatedAt ? new Date(it.generatedAt).toLocaleString() : 'Not generated yet'}>{it.generatedAt ? ago(it.generatedAt) : 'never'}</span>;
  return (
    <>
      <span className="ld-name" role="cell">
        <strong><Link to={OPEN} className="ld-link">{it.name}</Link></strong>
        <span className="ld-desc">
          {it.kind === 'project'
            ? it.stack.join(', ')
            : <>{it.company} · {it.dates}{it.now && <span className="ex-now">NOW</span>}</>}
        </span>
        <span className="ll-inline">{lenses}{bul}{it.kind === 'project' && !it.repo && <span className="ll-norepo">No repo</span>}</span>
      </span>
      <span role="cell" className="ll-stories">
        <StoryCount key={fresh ? `f${n}` : n} n={n} fresh={fresh} />
      </span>
      <span role="cell" className="ld-hide-sm">{lenses}</span>
      <span role="cell" className="ld-hide-sm">{bul}</span>
      {it.kind === 'project' && (
        <span role="cell" className="ld-hide-sm ld-repo" data-state={it.repo ? 'explored' : 'none'} title={it.repo ? `github.com/${it.repo}` : 'No repo linked'}>
          {it.repo ? 'Linked' : 'None'}
        </span>
      )}
      <span role="cell" className="ld-hide-sm">{gen}</span>
      <span role="cell" className="ll-acts ld-ctl">
        <Link to={OPEN} className="minibtn ll-open">Open</Link>
        <button type="button" className="minibtn ll-gen" disabled={busy || full} aria-busy={busy || undefined}
          title={full ? `Bank full (${STORY_CAP})` : 'Find one new story'} onClick={onGenerate}>
          {busy ? <Spin /> : 'Generate'}
        </button>
      </span>
    </>
  );
}

/** Same count as the story-flow header: ⚠ under 3, Full at the cap. */
function StoryCount({ n, fresh }: { n: number; fresh: boolean }) {
  const thin = n < 3, full = n >= STORY_CAP;
  return (
    <span className="sf-count ll-count" data-thin={thin || undefined} data-new={fresh || undefined}
      title={full ? `Bank full (${STORY_CAP})` : thin ? 'Few stories: entry may print short' : undefined}>
      <b>{n}</b> {n === 1 ? 'story' : 'stories'}{thin && ' ⚠'}{full && <span className="sf-count__full">Full</span>}
    </span>
  );
}

function Lenses({ it }: { it: Item }) {
  const c = lensCounts(it);
  if (c.size === 0) return <span className="ll-none">—</span>;
  return (
    <span className="ll-tags">
      {LENSES.filter(l => c.has(l.slug)).map(l => {
        const x = c.get(l.slug)!;
        return (
          <span key={l.slug} className="sf-lens ll-tag" data-lens={l.slug}
            title={`${l.name}: ${x.stories} ${x.stories === 1 ? 'story' : 'stories'}, ${x.bullets} ${x.bullets === 1 ? 'bullet' : 'bullets'}`}>
            {l.name}
          </span>
        );
      })}
    </span>
  );
}
