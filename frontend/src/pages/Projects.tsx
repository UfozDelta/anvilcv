import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { api, type Project } from '../lib/api';
import { BulletBar, EXIT, EmptyState, PageTitle, RowMenu, UndoBar, ago, useUndoDelete } from '../components/ledger/shared';
import { usePrefersReducedMotion } from '../components/landing/useHeroLoop';
import { NewEntryForm } from '../components/ProjectsList/NewEntryForm';
import { RepoPicker } from '../components/github/RepoPicker';

type Sort = 'edited' | 'newest' | 'name';

const SORTS: { key: Sort; label: string }[] = [
  { key: 'edited', label: 'Most recently edited first' },
  { key: 'newest', label: 'Newest added first' },
  { key: 'name', label: 'A → Z' },
];

/** Bar scale for bullet counts (a full bank). */
const FULL_BANK = 12;
const edited = (p: Project) => p.updatedAt ?? p.createdAt ?? '';

/** What the API says about the linked repo: none, being read, linked but not yet read, or read. */
function repoState(p: Project): { state: 'none' | 'exploring' | 'linked' | 'explored'; label: string } {
  if (!p.githubUrl) return { state: 'none', label: 'No repo' };
  if (p.repoContextReady) return { state: 'explored', label: 'Repo read' };
  return p.repoCommitSha ? { state: 'linked', label: 'Repo linked' } : { state: 'exploring', label: 'Reading repo…' };
}

export function Projects() {
  const [rows, setRows] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('edited');
  const [showForm, setShowForm] = useState(false);
  const [importing, setImporting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const navigate = useNavigate();
  const reduced = usePrefersReducedMotion();

  const del = useUndoDelete(rows, setRows, (p) => `/api/projects/${p.id}`, (p, e) => setErr((e as Error)?.message || `Failed to delete "${p.name}"`), () => void load(true));

  async function load(quiet = false) {
    if (!quiet) setLoading(true);
    try { setRows((await api.get<Project[]>('/api/projects?kind=PROJECT')).filter((p) => !del.isPending(p.id))); }
    catch (e) { setErr((e as Error).message || 'Could not load projects'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const out = needle ? rows.filter((r) => (r.name ?? '').toLowerCase().includes(needle)) : rows;
    const by: Record<Sort, (a: Project, b: Project) => number> = {
      edited: (a, b) => edited(b).localeCompare(edited(a)),
      newest: (a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
      name: (a, b) => a.name.localeCompare(b.name),
    };
    return [...out].sort(by[sort]);
  }, [rows, q, sort]);

  async function dup(id: string) {
    setErr(null);
    try {
      await api.post(`/api/projects/${id}/duplicate`);
      await load(true);
    } catch (e) {
      setErr((e as Error)?.message || 'Failed to duplicate');
    }
  }

  function create(p: Project) {
    setRows((rs) => [p, ...rs]);
    setShowForm(false);
  }

  const has = rows.length > 0;
  const adding = showForm || importing;
  const openForm = () => { setImporting(false); setShowForm(true); };
  const openImport = () => { setShowForm(false); setImporting(true); };

  return (
    <div className="shell ap-page">
      <PageTitle
        title="Projects"
        count={has && <><strong>{rows.length}</strong> project{rows.length === 1 ? '' : 's'} · <strong>{rows.reduce((n, r) => n + (r.bulletCount ?? 0), 0)}</strong> bullets in your bank</>}
        actions={has && !adding && (
          <>
            <button type="button" className="ap-btn ap-btn--ghost" onClick={openImport}>Import from GitHub</button>
            <button type="button" className="ap-btn ap-btn--acid" onClick={openForm}>+ New project</button>
          </>
        )}
      />

      {err && <div className="err ap-err" role="alert">{err}</div>}

      {showForm && <div className="ap-form"><NewEntryForm kind="PROJECT" onCreate={create} onCancel={() => setShowForm(false)} /></div>}
      {importing && <div className="ap-form"><RepoPicker onLinked={(p) => navigate(`/projects/${p.id}?tab=repo`)} onCancel={() => setImporting(false)} /></div>}

      {loading ? <span className="spinner">LOADING</span> : !has ? (
        !adding && (
          <EmptyState title="No projects yet." sub="Every tailored résumé is built from your bank. Start with one project.">
            <div className="ap-empty__paths">
              <button type="button" className="ap-path ap-path--primary" onClick={openImport}>
                <span className="ap-path__num">01</span>
                <strong>Import a GitHub repo</strong>
                <span>We read the code and draft bullets for you.</span>
              </button>
              <button type="button" className="ap-path" onClick={openForm}>
                <span className="ap-path__num">02</span>
                <strong>Write one by hand</strong>
                <span>Name, a sentence, your role. Bullets come after.</span>
              </button>
            </div>
          </EmptyState>
        )
      ) : (
        <>
          <div className="ap-tools">
            <input className="ap-search" placeholder="Search projects" aria-label="Search projects" value={q} onChange={(e) => setQ(e.target.value)} />
            <select className="la-sort" value={sort} aria-label="Sort projects" onChange={(e) => setSort(e.target.value as Sort)}>
              {SORTS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </div>
          <div className="ld-table" role="table" aria-label="Projects">
            <div className="ld-row pj-row pj-row--head ld-row--head" role="row">
              <span role="columnheader">Project</span>
              <span role="columnheader" className="ld-hide-sm">Stack</span>
              <span role="columnheader">Bullets</span>
              <span role="columnheader" className="ld-hide-sm">Repo</span>
              <span role="columnheader" className="ld-hide-sm">Edited</span>
              <span role="columnheader"><span className="sr-only">Actions</span></span>
            </div>
            <AnimatePresence initial={false}>
              {shown.map((p) => {
                const repo = repoState(p);
                return (
                  <motion.div
                    key={p.id}
                    className="ld-row pj-row"
                    role="row"
                    layout={reduced ? false : 'position'}
                    initial={reduced ? false : { opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0, transition: EXIT }}
                    transition={EXIT}
                  >
                    <span className="ld-name" role="cell">
                      <strong><Link to={`/projects/${p.id}`} className="ld-link">{p.name}</Link></strong>
                      <span className="ld-desc">{p.description}</span>
                    </span>
                    <span role="cell" className="ld-hide-sm pj-stack" title={(p.techTerms ?? []).join(', ')}>
                      {(p.techTerms ?? []).length === 0 ? '—' : <>
                        {p.techTerms!.slice(0, 4).join(', ')}
                        {p.techTerms!.length > 4 && <span className="pj-more"> +{p.techTerms!.length - 4}</span>}
                      </>}
                    </span>
                    <span className="ld-bullets" role="cell">
                      <span className="ld-bullets__n">{p.bulletCount ?? 0}</span>
                      <BulletBar n={p.bulletCount ?? 0} max={FULL_BANK} />
                    </span>
                    <span role="cell" className="ld-hide-sm ld-repo" data-state={repo.state} title={p.githubUrl ?? undefined}>
                      {p.githubUrl ? p.githubUrl.replace('https://github.com/', '') : repo.label}
                    </span>
                    <span role="cell" className="ld-hide-sm ld-edited">{ago(edited(p))}</span>
                    <RowMenu label={p.name} onDelete={() => del.remove(p.id)} onDuplicate={() => dup(p.id)} />
                  </motion.div>
                );
              })}
            </AnimatePresence>
            {shown.length === 0 && (
              <div className="ap-nomatch">Nothing matches “{q}”. <button type="button" onClick={() => setQ('')}>Clear</button></div>
            )}
          </div>
        </>
      )}
      <UndoBar name={del.removed?.name ?? null} more={del.more} onUndo={del.undo} />
    </div>
  );
}
