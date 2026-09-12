import { useMemo, useState } from 'react';
import type { Project } from '../lib/api';
import { LAB_BULLETS, LAB_PROJECTS } from './fixtures';
import { LabChrome } from './LabChrome';
import { NewEntryForm, type Kind } from './projectsList/NewEntryForm';
import { RowMenu } from './projectsList/RowMenu';

type Sort = 'newest' | 'most-bullets' | 'name';

const KINDS: { key: Kind; label: string }[] = [
  { key: 'EXPERIENCE', label: 'Experiences' },
  { key: 'PROJECT', label: 'Projects' },
];

const SORTS: { key: Sort; label: string }[] = [
  { key: 'newest', label: 'Newest first' },
  { key: 'most-bullets', label: 'Most bullets first' },
  { key: 'name', label: 'A → Z' },
];

export function LabProjectsList() {
  const [rows, setRows] = useState<Project[]>(LAB_PROJECTS);
  const [kind, setKind] = useState<Kind>('EXPERIENCE');
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('newest');
  const [loading, setLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);

  const bulletCount = useMemo(() => {
    const c: Record<string, number> = {};
    for (const b of LAB_BULLETS) c[b.projectId] = (c[b.projectId] ?? 0) + 1;
    return c;
  }, []);

  // Counts come from the whole set, so the kind switch stays stable while you search.
  const counts = useMemo(() => {
    const c: Record<Kind, number> = { PROJECT: 0, EXPERIENCE: 0 };
    for (const r of rows) c[r.kind as Kind]++;
    return c;
  }, [rows]);

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    let out = rows.filter(r => r.kind === kind);
    if (needle) {
      out = out.filter(r =>
        (r.name ?? '').toLowerCase().includes(needle) ||
        (r.title ?? '').toLowerCase().includes(needle) ||
        (r.company ?? '').toLowerCase().includes(needle));
    }
    const by: Record<Sort, (a: Project, b: Project) => number> = {
      newest: (a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
      'most-bullets': (a, b) => (bulletCount[b.id] ?? 0) - (bulletCount[a.id] ?? 0),
      name: (a, b) => (a.title || a.name).localeCompare(b.title || b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, kind, q, sort, bulletCount]);

  function del(id: string) {
    setRows(rs => rs.filter(r => r.id !== id));
  }
  function dup(p: Project) {
    setRows(rs => [{ ...p, id: `${p.id}-copy`, name: `${p.name} (copy)`, createdAt: new Date().toISOString() }, ...rs]);
  }
  function create(p: Project) {
    setRows(rs => [p, ...rs]);
    setShowForm(false);
  }

  return (
    <LabChrome
      title="Projects & Experiences — list"
      note={
        <>
          Same audit pass as the Applications list, applied here: the positional index (01, 02…)
          is gone since it renumbered on every search; delete moved out of the row link and off
          the native <code>window.confirm()</code> dialog into a menu with an in-place confirm;
          each row now says how many bullets are in its bank instead of leaving that invisible
          until you open it; search and sort exist; a repo-cached badge replaces the faint inline
          text that was easy to miss. Projects and Experiences share one list component with a
          kind switch, since they were always the same screen with different fields.
        </>
      }
    >
      <div className="toolbar">
        <div className="filterset">
          {KINDS.map(k => (
            <button key={k.key} className={kind === k.key ? 'is-on' : ''} onClick={() => { setKind(k.key); setShowForm(false); }}>
              {k.label} <span className="filterset__count">{counts[k.key]}</span>
            </button>
          ))}
        </div>

        <input
          className="toolbar__search"
          placeholder={kind === 'PROJECT' ? 'Search projects…' : 'Search role or company…'}
          value={q}
          onChange={e => setQ(e.target.value)}
        />

        <select
          className="approw__status"
          style={{ width: 'auto' }}
          value={sort}
          onChange={e => setSort(e.target.value as Sort)}
        >
          {SORTS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
        </select>

        <button
          className="minibtn"
          style={{ marginLeft: 'auto', padding: '8px 10px' }}
          onClick={() => { setLoading(true); window.setTimeout(() => setLoading(false), 1400); }}
        >
          Test loading state
        </button>
      </div>

      <div className="applist">
        {loading
          ? Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="approw" aria-hidden>
                <div>
                  <div style={{ height: 17, width: '48%', background: 'var(--paper-2)', marginBottom: 6 }} />
                  <div style={{ height: 11, width: '32%', background: 'var(--paper-2)' }} />
                </div>
                <div style={{ height: 22, background: 'var(--paper-2)' }} />
                <div style={{ height: 11, background: 'var(--paper-2)' }} />
                <div />
              </div>
            ))
          : shown.map(p => {
              const count = bulletCount[p.id] ?? 0;
              const title = kind === 'EXPERIENCE' ? (p.title || p.name) : p.name;
              const meta = kind === 'EXPERIENCE'
                ? [p.company, p.location, p.dates].filter(Boolean).join(' · ') || '—'
                : (p.description?.slice(0, 72) ?? '') + ((p.description?.length ?? 0) > 72 ? '…' : '');
              return (
                <div className="approw" key={p.id} style={{ gridTemplateColumns: 'minmax(0,1fr) 150px 34px' }}>
                  <a className="approw__link" href={`/lab/project-detail?id=${p.id}`}>{title}</a>

                  <div>
                    <h3 className="approw__title">{title}</h3>
                    <div className="approw__role">{meta}</div>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                    <span className="kw kw--hit" title="Bullets currently in the bank for this entry">
                      {count} bullet{count === 1 ? '' : 's'}
                    </span>
                    {kind === 'PROJECT' && p.githubUrl && (
                      <span className="kw" title={p.githubUrl}>
                        {p.repoContextReady ? 'repo cached' : 'repo fetching…'}
                      </span>
                    )}
                  </div>

                  <RowMenu onDelete={() => del(p.id)} onDuplicate={() => dup(p)} />
                </div>
              );
            })}

        {!loading && shown.length === 0 && (
          <div style={{ padding: '44px 0', textAlign: 'center', borderBottom: 'var(--rule-thin)' }}>
            <div className="editorial" style={{ fontSize: 17, marginBottom: 6 }}>
              {q ? `Nothing matches “${q}”.` : `No ${kind === 'PROJECT' ? 'projects' : 'experiences'} yet.`}
            </div>
            <button className="minibtn" onClick={() => setQ('')}>Clear search</button>
          </div>
        )}
      </div>

      <div style={{ marginTop: 28 }}>
        {!showForm ? (
          <button className="btn btn--acid" onClick={() => setShowForm(true)}>
            {kind === 'PROJECT' ? '+ NEW PROJECT' : '+ NEW EXPERIENCE'}
          </button>
        ) : (
          <NewEntryForm kind={kind} onCreate={create} onCancel={() => setShowForm(false)} />
        )}
      </div>
    </LabChrome>
  );
}
