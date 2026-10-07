import { useState } from 'react';
import { CATEGORIES, type Project } from '../../lib/api';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';
import { parseExtract, type ExtractField } from '../../lib/parseExtract';
import { RichText } from '../../components/RichText';
import { RepoMapView } from '../../components/ProjectDetail/RepoMapView';
import { LAB_CFG } from '../fixtures';
import { isVanity, STORY_CAP, type WStatus } from '../stories/storyFixtures';
import { AddBulletForm } from '../projectDetail/AddBulletForm';
import { EditBulletForm } from '../projectDetail/EditBulletForm';
import { CONTEXT_FIELDS, REPO_MAP } from './data';
import { storyOf, type Bank, type SBullet } from './model';

const LENS = Object.fromEntries(CATEGORIES.map(c => [c.slug, c.label]));

export type Tab = 'bullets' | 'description' | 'context' | 'repo';
export const TAB_LABEL: Record<Tab, string> = { bullets: 'Bullets', description: 'Description', context: 'Context', repo: 'Repo' };

/* ── Head ── */

export function Meter({ used, usable }: { used: number; usable: number }) {
  const thin = usable < 3;
  return (
    <span className="ps-meter" data-thin={thin || undefined} title={`${used} of ${STORY_CAP} stories · ${usable} usable${thin ? ' · entry may print short' : ''}`}>
      <span className="ps-meter__cells" aria-hidden="true">
        {Array.from({ length: STORY_CAP }, (_, i) => <i key={i} data-on={i < used || undefined} />)}
      </span>
      <b>{usable}</b>/{STORY_CAP}{thin && ' ⚠'}
    </span>
  );
}

export function GenButton({ b, vertical }: { b: Bank; vertical?: boolean }) {
  return (
    <div className="ps-gen" data-vertical={vertical || undefined}>
      <button type="button" className="btn btn--sm btn--acid ps-gen__btn" onClick={b.generate} disabled={b.busy}
        title={b.used >= STORY_CAP ? 'Bank full' : 'Find new stories'}>
        {b.busy ? <span className="ps-spin" aria-label="Generating" /> : '✦'} Generate
      </button>
      {b.run && (
        <span className="ps-run" aria-live="polite">
          {b.run.full
            ? <span className="ps-run__c" data-bad title="Bank full: nothing generated">Full</span>
            : <>
                <span className="ps-run__c" data-good title={`${b.run.added} new stories`}>+{b.run.added}</span>
                <span className="ps-run__c" title={`${b.run.dropped} dropped: repeats or no evidence`}>−{b.run.dropped}</span>
              </>}
        </span>
      )}
    </div>
  );
}

export function Head({ b, children }: { b: Bank; children?: React.ReactNode }) {
  return (
    <header className="ps-head">
      <div className="ps-head__id">
        <div className="eyebrow">← Projects</div>
        <h1 className="display ps-head__name">{b.project.name}</h1>
      </div>
      <div className="ps-head__tools">
        <Meter used={b.used} usable={b.usable} />
        {children}
      </div>
    </header>
  );
}

export function Tabs({ tabs, tab, setTab, b }: { tabs: Tab[]; tab: Tab; setTab: (t: Tab) => void; b: Bank }) {
  return (
    <div className="tabs ps-tabs" role="tablist">
      {tabs.map(t => (
        <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'is-on' : ''} onClick={() => setTab(t)}>
          {TAB_LABEL[t]}
          {t === 'bullets' && <span className="tabs__badge">{b.bullets.length}</span>}
        </button>
      ))}
    </div>
  );
}

/** Phone only: switches which half of the split is on screen. */
export function PaneSwitch({ names, on, setOn }: { names: string[]; on: number; setOn: (i: number) => void }) {
  return (
    <div className="filterset ps-seg">
      {names.map((n, i) => <button key={n} className={on === i ? 'is-on' : ''} onClick={() => setOn(i)}>{n}</button>)}
    </div>
  );
}

/* ── Bullet row ── */

function Fit({ text }: { text: string }) {
  const fit = fitOf(text, LAB_CFG);
  if (fit === 'OFF') return null;
  const bad = needsRefit(fit);
  return (
    <span className="ps-fit" data-bad={bad || undefined} title={bad ? fitHint(text, LAB_CFG) : `${estimatedLines(text)} line${estimatedLines(text) === 1 ? '' : 's'}`}>
      {bad && '⚠ '}{FIT_LABEL[fit]} · {charCount(text)}c
    </span>
  );
}

export function StoryMark({ b, bullet }: { b: Bank; bullet: SBullet }) {
  const s = storyOf(bullet.storyId);
  if (!s) return null;
  const sibs = b.siblings(bullet).filter(w => w.status !== 'REJECTED');
  const prints = b.printed.has(bullet.id);
  return (
    <>
      <span className="ps-glyph" title={`${s.title} · ${sibs.length} wording${sibs.length === 1 ? '' : 's'}, one prints`}
        onMouseEnter={() => b.setHoverStory(s.id)} onMouseLeave={() => b.setHoverStory(null)}>
        {s.glyph}{sibs.length > 1 && <sup>{sibs.length}</sup>}
      </span>
      {prints && sibs.length > 1 && <span className="ps-prints" title="Prints" aria-label="Prints">▶</span>}
    </>
  );
}

const STATE_LABEL: Record<WStatus, string> = { APPROVED: '✓ APPROVED', PENDING: 'PENDING', REJECTED: '✕ REJECTED' };

export function Row({ b, bullet, onEdit, onPick, compact }: {
  b: Bank; bullet: SBullet; onEdit?: () => void; onPick?: () => void; compact?: boolean;
}) {
  const [menu, setMenu] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const st = bullet.status;
  const sib = !!bullet.storyId && b.hoverStory === bullet.storyId;
  const cls = ['brow', 'ps-row', st === 'APPROVED' ? 'is-in' : '', st === 'REJECTED' ? 'is-out' : '', b.selectedId === bullet.id ? 'is-sel' : '']
    .filter(Boolean).join(' ');
  return (
    <div className={cls} data-sib={sib || undefined} data-hit={b.cites(bullet) || undefined} data-new={b.newIds.has(bullet.id) || undefined}
      onClick={() => { b.setSelectedId(bullet.id); onPick?.(); }}>
      <button className="brow__toggle" aria-pressed={st === 'APPROVED'} title={st === 'APPROVED' ? 'Unapprove' : 'Approve'}
        onClick={e => { e.stopPropagation(); b.patch(bullet.id, { status: st === 'APPROVED' ? 'PENDING' : 'APPROVED' }); }}>
        <span className={`brow__state ${st === 'APPROVED' ? 'brow__state--in' : 'brow__state--out'}`}>{STATE_LABEL[st]}</span>
        <span className="brow__rank">{estimatedLines(bullet.text)}L</span>
      </button>
      <div style={{ minWidth: 0 }}>
        <div className="brow__text" data-clamp={compact || undefined}><RichText text={bullet.text} /></div>
        <div className="ps-meta">
          <Fit text={bullet.text} />
          <StoryMark b={b} bullet={bullet} />
          {isVanity(bullet.text) && st !== 'APPROVED' && <span className="ps-fit" data-bad title="Activity count: held back unless approved">#</span>}
          {bullet.tags.map(t => <span key={t} className="ps-tag">{t}</span>)}
        </div>
        {!compact && (
          <div className="brow__under">
            <div className="brow__actions" style={{ marginLeft: 0 }} onClick={e => e.stopPropagation()}
              onMouseLeave={() => { setMenu(false); setConfirm(false); }}>
              {onEdit && <button className="minibtn" onClick={onEdit}>Edit</button>}
              <div className="rowmenu" style={{ display: 'inline-block' }}>
                <button className="rowmenu__btn" aria-label="Bullet actions" aria-expanded={menu} onClick={() => setMenu(o => !o)}>⋯</button>
                {menu && (
                  <div className="rowmenu__pop">
                    <button onClick={() => { b.patch(bullet.id, { status: st === 'REJECTED' ? 'PENDING' : 'REJECTED' }); setMenu(false); }}>
                      {st === 'REJECTED' ? 'Restore' : 'Reject'}
                    </button>
                    <button className="is-danger" onClick={() => (confirm ? b.remove(bullet.id) : setConfirm(true))}>
                      {confirm ? 'Really delete?' : 'Delete'}
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Bullets by lens ── */

export function BulletsPane({ b, compact, onPick }: { b: Bank; compact?: boolean; onPick?: () => void }) {
  const [filter, setFilter] = useState<string | null>(null);
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const toggle = (slug: string) => setClosed(s => {
    const n = new Set(s);
    n.has(slug) ? n.delete(slug) : n.add(slug);
    return n;
  });
  const shown = filter ? b.groups.filter(g => g.slug === filter) : b.groups;

  return (
    <div>
      <div className="ps-tools">
        <div className="ps-chips">
          <button className="minibtn" aria-pressed={filter === null} data-on={filter === null || undefined} onClick={() => setFilter(null)}>All</button>
          {b.groups.map(g => (
            <button key={g.slug} className="minibtn" data-on={filter === g.slug || undefined} aria-pressed={filter === g.slug}
              onClick={() => setFilter(f => (f === g.slug ? null : g.slug))}>
              {g.label} <span className="ps-chips__n">{g.rows.length}</span>
            </button>
          ))}
        </div>
        {!compact && (
          <button className="minibtn" onClick={() => { setAdding(a => !a); setEditing(null); }}>{adding ? '✕ Cancel' : '+ Add'}</button>
        )}
      </div>

      {adding && <AddBulletForm onSave={(t, tg, c) => { b.add(t, tg, c); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {shown.map(g => {
        const open = !closed.has(g.slug);
        return (
          <section key={g.slug} className="ps-group">
            <div className="ps-group__head" role="button" tabIndex={0} aria-expanded={open} onClick={() => toggle(g.slug)}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(g.slug); } }}>
              <span className="label">{open ? '▾' : '▸'} {g.label}</span>
              <span className="label muted">{g.rows.length}</span>
            </div>
            {open && g.rows.map(r => (editing === r.id ? (
              <EditBulletForm key={r.id} bullet={{ ...r, projectId: '', category: r.lens, createdAt: '', updatedAt: '' }}
                onSave={(text, tags) => { b.patch(r.id, { text, tags }); setEditing(null); }} onCancel={() => setEditing(null)} />
            ) : (
              <Row key={r.id} b={b} bullet={r} compact={compact} onPick={onPick}
                onEdit={compact ? undefined : () => { setEditing(r.id); setAdding(false); }} />
            )))}
          </section>
        );
      })}
    </div>
  );
}

/* ── Sources ── */

export function DescriptionPane({ b }: { b: Bank }) {
  return (
    <label className="field">
      <div className="field__label">Description</div>
      <textarea className="field__textarea" value={b.project.description} style={{ minHeight: 180 }}
        onFocus={() => b.setSource('Description')} onBlur={() => b.setSource(null)}
        onChange={e => b.setField('description', e.target.value)} />
      <div className="ps-count">{b.project.description.length}c</div>
    </label>
  );
}

const PASTE_TO_KEY: Record<ExtractField, keyof Project> = {
  techStack: 'techStack', yourRole: 'yourRole', ownership: 'ownership', scaleImpact: 'scaleImpact',
  hardestProblem: 'hardestProblem', technicalDecisions: 'technicalDecisions', userImpact: 'userImpact',
  securityPosture: 'securityPosture', description: 'contextDescription',
};

export function ContextPane({ b }: { b: Bank }) {
  const [paste, setPaste] = useState<string | null>(null);
  const filled = CONTEXT_FIELDS.filter(f => String(b.project[f.key] ?? '').trim()).length;

  function fill() {
    const got = parseExtract(paste ?? '');
    for (const [k, v] of Object.entries(got)) if (v) b.setField(PASTE_TO_KEY[k as ExtractField], v);
    setPaste(null);
  }

  return (
    <div className="stack-sm">
      <div className="ps-tools">
        <span className="label muted" title="Context fields filled">{filled}/{CONTEXT_FIELDS.length}</span>
        <button className="minibtn" onClick={() => setPaste(p => (p === null ? '' : null))} title="Paste extractor output to fill every field">
          {paste === null ? 'Paste' : '✕ Cancel'}
        </button>
      </div>
      {paste !== null && (
        <div className="panel panel--inset stack-sm" style={{ padding: 12 }}>
          <textarea className="field__textarea" style={{ minHeight: 120 }} value={paste} autoFocus
            placeholder={'## Tech Stack\n…'} onChange={e => setPaste(e.target.value)} />
          <button className="btn btn--acid btn--sm" onClick={fill} disabled={!paste.trim()}>Fill</button>
        </div>
      )}
      <div className="grid-2 ps-ctx">
        <label className="field">
          <div className="field__label">Name</div>
          <input className="field__input" value={b.project.name} onChange={e => b.setField('name', e.target.value)} />
        </label>
        <label className="field">
          <div className="field__label" title="GitHub URL">GitHub</div>
          <input className="field__input" value={b.project.githubUrl ?? ''} onChange={e => b.setField('githubUrl', e.target.value)} />
        </label>
        {CONTEXT_FIELDS.map(f => (
          <label key={f.key} className="field" data-long={f.long || undefined}>
            <div className="field__label" title={f.hint}>{f.label}</div>
            {f.long ? (
              <textarea className="field__textarea" style={{ minHeight: 64 }} value={String(b.project[f.key] ?? '')}
                onFocus={() => b.setSource(f.src)} onBlur={() => b.setSource(null)}
                onChange={e => b.setField(f.key, e.target.value)} />
            ) : (
              <input className="field__input" value={String(b.project[f.key] ?? '')}
                onFocus={() => b.setSource(f.src)} onBlur={() => b.setSource(null)}
                onChange={e => b.setField(f.key, e.target.value)} />
            )}
          </label>
        ))}
      </div>
    </div>
  );
}

export function RepoPane({ b }: { b: Bank }) {
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const p = b.project;
  const repo = (p.githubUrl ?? '').replace('https://github.com/', '');
  function toggle(name: string) {
    const n = new Set(picked);
    const on = !n.has(name);
    on ? n.add(name) : n.delete(name);
    setPicked(n);
    b.setSource(on ? `repo map: ${name}` : null);
  }
  return (
    <div className="stack-sm">
      <div className="ps-tools">
        <span className="label" title="Linked repo, branch and pinned commit">
          <a href={p.githubUrl ?? '#'} target="_blank" rel="noreferrer">{repo}</a> · {p.repoBranch} @ {p.repoCommitSha?.slice(0, 7)}
        </span>
        <span className="row" style={{ gap: 6 }}>
          <button className="minibtn" title="Pull latest commit">↻ Pull</button>
          <button className="minibtn" title="Link another repo">Change</button>
        </span>
      </div>
      <RepoMapView map={REPO_MAP} picked={picked} onToggle={toggle} onOpenFile={() => {}} />
    </div>
  );
}

export function SourceBody({ tab, b }: { tab: Tab; b: Bank }) {
  if (tab === 'description') return <DescriptionPane b={b} />;
  if (tab === 'context') return <ContextPane b={b} />;
  if (tab === 'repo') return <RepoPane b={b} />;
  return null;
}

/* ── Second panes ── */

/** One-page resume entry: what prints for this project, the selected bullet lit. */
export function Sheet({ b }: { b: Bank }) {
  const rows = b.bullets.filter(x => b.printed.has(x.id))
    .sort((x, y) => Number(y.status === 'APPROVED') - Number(x.status === 'APPROVED'));
  const sel = b.selected;
  const stand = sel && !b.printed.has(sel.id) ? b.siblings(sel).find(w => b.printed.has(w.id))?.id : undefined;
  const lines = rows.reduce((n, r) => n + estimatedLines(r.text), 0);
  return (
    <div className="ps-sheet-wrap">
      <div className="ps-tools">
        <span className="label">Page</span>
        <span className="label muted" title="Lines this entry takes">~{lines}L</span>
      </div>
      <div className="ps-sheet">
        <div className="ps-sheet__head">
          <b>{b.project.name}</b>
          <i>{b.project.techStack}</i>
        </div>
        <ul>
          {rows.map(r => (
            <li key={r.id} data-on={sel?.id === r.id || undefined} data-stand={stand === r.id || undefined}
              title={stand === r.id ? 'Prints instead of the selected wording' : undefined}
              onClick={() => b.setSelectedId(r.id)}>
              <RichText text={r.text} />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** Everything about one bullet: text, status, lens, tags, its story's other wordings, evidence. */
export function Detail({ b, onBack }: { b: Bank; onBack?: () => void }) {
  const [confirm, setConfirm] = useState<string | null>(null);
  const r = b.selected;
  if (!r) return <p className="ps-empty">—</p>;
  const s = storyOf(r.storyId);
  const sibs = b.siblings(r).filter(w => w.id !== r.id);
  return (
    <div className="ps-detail stack-sm">
      <div className="ps-tools">
        {onBack && <button className="minibtn ps-back" onClick={onBack}>← List</button>}
        <span className="ps-meta">
          <StoryMark b={b} bullet={r} />
          {s && <span className="label muted ps-detail__title" title={s.title}>{s.title}</span>}
        </span>
      </div>
      <textarea className="field__textarea" style={{ minHeight: 96 }} value={r.text}
        onChange={e => b.patch(r.id, { text: e.target.value })} />
      <div className="ps-meta"><Fit text={r.text} /><span className="brow__rank">{estimatedLines(r.text)}L</span></div>
      <div className="filterset ps-status">
        {(['APPROVED', 'PENDING', 'REJECTED'] as WStatus[]).map(st => (
          <button key={st} className={r.status === st ? 'is-on' : ''} onClick={() => b.patch(r.id, { status: st })}>
            {STATE_LABEL[st]}
          </button>
        ))}
      </div>
      <div className="grid-2">
        <label className="field">
          <div className="field__label">Lens</div>
          <select className="field__input" value={r.lens} onChange={e => b.patch(r.id, { lens: e.target.value })}>
            {CATEGORIES.map(c => <option key={c.slug} value={c.slug} title={c.blurb}>{c.label}</option>)}
          </select>
        </label>
        <label className="field">
          <div className="field__label">Tags</div>
          <input className="field__input" value={r.tags.join(', ')}
            onChange={e => b.patch(r.id, { tags: e.target.value.split(',').map(t => t.trim()).filter(Boolean) })} />
        </label>
      </div>

      {sibs.length > 0 && (
        <div>
          <div className="eyebrow ps-sub">{s?.glyph} Same story</div>
          {sibs.map(w => (
            <button key={w.id} className="ps-sib" data-s={w.status} onClick={() => b.setSelectedId(w.id)} title={w.text.replace(/\*\*/g, '')}>
              <span className="ps-sib__lens">{LENS[w.lens]}</span>
              <span className="ps-sib__text"><RichText text={w.text} /></span>
              <span className="ps-sib__st">{b.printed.has(w.id) ? '▶' : w.status === 'APPROVED' ? '✓' : w.status === 'REJECTED' ? '✕' : '·'}</span>
            </button>
          ))}
        </div>
      )}

      {s && s.evidence.length > 0 && (
        <div>
          <div className="eyebrow ps-sub">“ Evidence</div>
          <ul className="ps-quotes">
            {s.evidence.map(e => <li key={e.quote}><q>{e.quote}</q> <span>{e.from}</span></li>)}
          </ul>
        </div>
      )}

      <div className="row">
        <button className="btn btn--ghost btn--sm btn--rust" onClick={() => (confirm === r.id ? b.remove(r.id) : setConfirm(r.id))}>
          {confirm === r.id ? 'Really delete?' : 'Delete'}
        </button>
      </div>
    </div>
  );
}
