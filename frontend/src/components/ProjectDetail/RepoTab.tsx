import { useEffect, useMemo, useRef, useState } from 'react';
import { api, CATEGORIES, type Bullet, type BulletSource, type Project, type RepoTree } from '../../lib/api';
import { markdownBoldToHtml } from '../../lib/markdown';
import { EventStream } from '../EventStream';
import { RepoPicker } from '../github/RepoPicker';
import type { useProjectDetail } from '../../hooks/useProjectDetail';

type S = ReturnType<typeof useProjectDetail>;

const MAX_TREE_ROWS = 500;

/**
 * Side-loaded repo view: the linked repo's tree and files on the left, the bullet bank on the
 * right. The user steers the explorer (pin / exclude paths, notes, lenses), runs it, generates,
 * and triages — each bullet links back to the files or commits it traces to.
 */
export function RepoTab({ s, id, project }: { s: S; id: string; project: Project }) {
  const linked = !!project.repoCommitSha;
  const [relinking, setRelinking] = useState(false);

  if (!linked || relinking) {
    return (
      <RepoPicker
        projectId={id}
        onLinked={() => { setRelinking(false); s.load(); }}
        onCancel={linked ? () => setRelinking(false) : undefined}
      />
    );
  }
  return <LinkedRepo s={s} id={id} project={project} onRelink={() => setRelinking(true)} />;
}

function LinkedRepo({ s, id, project, onRelink }: { s: S; id: string; project: Project; onRelink: () => void }) {
  const repoName = (project.githubUrl ?? '').replace('https://github.com/', '');
  const [tree, setTree] = useState<RepoTree | null>(null);
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState<{ path: string; content: string; from?: number; to?: number } | null>(null);
  const [pins, setPins] = useState<Set<string>>(new Set());
  const [excludes, setExcludes] = useState<Set<string>>(new Set());
  const [notes, setNotes] = useState('');
  const [lenses, setLenses] = useState<Set<string>>(new Set(s.picked));
  const [exploring, setExploring] = useState(false);
  const [sources, setSources] = useState<Record<string, BulletSource[]>>({});
  const [err, setErr] = useState<string | null>(null);
  const [pulling, setPulling] = useState(false);

  useEffect(() => {
    setTree(null);
    api.get<RepoTree>(`/api/github/projects/${id}/tree`).then(setTree).catch(e => setErr(e?.message || 'Could not load repo tree'));
  }, [id, project.repoCommitSha]);

  useEffect(() => {
    api.get<Record<string, BulletSource[]>>(`/api/github/projects/${id}/bullet-sources`).then(setSources).catch(() => setSources({}));
  }, [id, s.bullets, project.repoContextReady]);

  const rows = useMemo(() => {
    const n = filter.trim().toLowerCase();
    return (tree?.entries ?? []).filter(e => !n || e.path.toLowerCase().includes(n));
  }, [tree, filter]);

  async function openFile(path: string, from?: number, to?: number) {
    setErr(null);
    try {
      const content = open?.path === path ? open.content : await api.get<string>(`/api/github/projects/${id}/file?path=${encodeURIComponent(path)}`);
      setOpen({ path, content, from, to });
    } catch (e: any) {
      setErr(e?.message || `Could not open ${path}`);
    }
  }

  function toggle(set: Set<string>, setter: (s: Set<string>) => void, v: string) {
    const next = new Set(set);
    next.has(v) ? next.delete(v) : next.add(v);
    setter(next);
  }

  async function pullLatest() {
    setPulling(true); setErr(null);
    try {
      await api.post('/api/github/link', { projectId: id, fullName: repoName, branch: project.repoBranch });
      await s.load();
    } catch (e: any) {
      setErr(e?.message || 'Pull failed');
    } finally {
      setPulling(false);
    }
  }

  const bank = s.bullets.filter(b => b.status !== 'REJECTED');

  return (
    <div>
      <div className="row row--between row--centered" style={{ marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
        <span className="label">
          <a href={project.githubUrl ?? '#'} target="_blank" rel="noreferrer">{repoName}</a>
          {' '}· {project.repoBranch} @ {project.repoCommitSha?.slice(0, 7)}
        </span>
        <span className="row" style={{ gap: 8 }}>
          <button className="btn btn--ghost btn--sm" onClick={pullLatest} disabled={pulling}>{pulling ? 'PULLING…' : '↻ PULL LATEST'}</button>
          <button className="btn btn--ghost btn--sm" onClick={onRelink}>CHANGE REPO</button>
        </span>
      </div>
      {err && <div className="err" style={{ marginBottom: 12 }}>{err}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 380px), 1fr))', gap: 20, alignItems: 'start' }}>
        {/* ---------------------------------------------------------- repo side */}
        <div className="stack-sm" style={{ minWidth: 0 }}>
          <input className="field__input" placeholder={tree ? `Filter ${tree.entries.length} files…` : 'Loading tree…'} value={filter} onChange={e => setFilter(e.target.value)} />
          {tree?.truncated && <div className="label muted">GitHub truncated this tree — very large repo, some files missing.</div>}
          <div style={{ maxHeight: 280, overflowY: 'auto', border: '1px solid var(--ink)', fontFamily: 'var(--mono)', fontSize: 12 }}>
            {rows.slice(0, MAX_TREE_ROWS).map(e => {
              const ex = excludes.has(e.path), pin = pins.has(e.path);
              return (
                <div key={e.path} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '3px 6px', background: open?.path === e.path ? 'var(--acid)' : undefined }}>
                  <button type="button" title="Pin: the explorer reads this first" onClick={() => toggle(pins, setPins, e.path)}
                    style={{ all: 'unset', cursor: 'pointer', width: 14, opacity: pin ? 1 : 0.35 }}>{pin ? '★' : '☆'}</button>
                  <button type="button" title="Exclude: the explorer may not read this" onClick={() => toggle(excludes, setExcludes, e.path)}
                    style={{ all: 'unset', cursor: 'pointer', width: 14, opacity: ex ? 1 : 0.35 }}>⊘</button>
                  <button type="button" onClick={() => openFile(e.path)}
                    style={{ all: 'unset', cursor: 'pointer', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textDecoration: ex ? 'line-through' : undefined }}>
                    {e.path}
                  </button>
                </div>
              );
            })}
            {rows.length > MAX_TREE_ROWS && <div className="label muted" style={{ padding: 6 }}>{rows.length - MAX_TREE_ROWS} more — narrow the filter.</div>}
          </div>
          {open && <FileViewer file={open} onClose={() => setOpen(null)} />}
        </div>

        {/* ---------------------------------------------------------- bank side */}
        <div className="stack-sm" style={{ minWidth: 0 }}>
          <div className="panel panel--inset stack-sm">
            <div className="label">STEER THE EXPLORER</div>
            <div className="row" style={{ flexWrap: 'wrap', gap: 6 }}>
              {CATEGORIES.map(c => (
                <button key={c.slug} type="button" className="btn btn--sm" title={c.blurb}
                  onClick={() => toggle(lenses, setLenses, c.slug)}
                  style={{ background: lenses.has(c.slug) ? 'var(--ink)' : 'var(--paper)', color: lenses.has(c.slug) ? 'var(--paper)' : 'var(--ink)' }}>
                  {c.label}
                </button>
              ))}
            </div>
            <textarea className="field__textarea" style={{ minHeight: 60 }} value={notes} onChange={e => setNotes(e.target.value)}
              placeholder="Notes — e.g. 'I wrote the ingest pipeline, not the UI', 'focus on the auth rewrite'" />
            {(pins.size > 0 || excludes.size > 0) && (
              <div className="label muted">{pins.size} PINNED · {excludes.size} EXCLUDED</div>
            )}
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <button className="btn btn--acid btn--sm" onClick={() => setExploring(true)} disabled={exploring || s.generating}>
                {exploring ? <span className="spinner">EXPLORING</span> : '⌕ EXPLORE REPO'}
              </button>
              <button className="btn btn--sm" onClick={() => s.generateBank(lenses)} disabled={s.generating || exploring || lenses.size === 0}
                title="Generate bullets from the explored context, one batch per lens">
                {s.generating ? <span className="spinner">GENERATING</span> : '↻ GENERATE BANK'}
              </button>
            </div>
            <div className="label muted">Explore fills Info &amp; Context from verified code. Generate writes PENDING bullets — approve the ones you want.</div>
          </div>

          <div className="label">BANK · {bank.length}</div>
          {bank.length === 0 && <div className="label muted">No bullets yet — explore, then generate.</div>}
          {bank.map(b => (
            <BankRow key={b.id} bullet={b} sources={sources[b.id] ?? []} githubUrl={project.githubUrl ?? ''}
              onOpen={(src) => src.path && openFile(src.path, src.startLine, src.endLine)}
              onStatus={(st) => s.setBulletStatus(b, st)} />
          ))}
        </div>
      </div>

      {exploring && (
        <EventStream
          submitUrl={`/api/github/projects/${id}/explore/submit`}
          submitBody={{ pinPaths: [...pins], excludePaths: [...excludes], notes, lenses: [...lenses] }}
          pollUrl={jobId => `/api/projects/jobs/${jobId}/progress`}
          onDone={() => { setExploring(false); s.load(); }}
          onClose={() => setExploring(false)}
          title="EXPLORING REPO..."
          doneLabel=""
        />
      )}
      {s.generating && (
        <EventStream
          submitUrl={`/api/projects/${id}/bullets/generate-bank/submit`}
          submitBody={{ categories: [...lenses] }}
          pollUrl={jobId => `/api/projects/jobs/${jobId}/progress`}
          onDone={() => { s.setGenerating(false); s.load(); }}
          onClose={() => s.setGenerating(false)}
          title="GENERATING BULLETS..."
          doneLabel=""
        />
      )}
    </div>
  );
}

function FileViewer({ file, onClose }: { file: { path: string; content: string; from?: number; to?: number }; onClose: () => void }) {
  const hlRef = useRef<HTMLDivElement>(null);
  useEffect(() => { hlRef.current?.scrollIntoView({ block: 'center' }); }, [file]);
  const lines = file.content.split('\n');
  return (
    <div style={{ border: '1px solid var(--ink)' }}>
      <div className="row row--between row--centered" style={{ padding: '4px 8px', borderBottom: '1px solid var(--ink)' }}>
        <span className="label" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {file.path}{file.from ? `:${file.from}-${file.to}` : ''}
        </span>
        <button className="btn btn--ghost btn--sm" onClick={onClose}>✕</button>
      </div>
      <div style={{ maxHeight: 420, overflow: 'auto', fontFamily: 'var(--mono)', fontSize: 12, lineHeight: 1.5 }}>
        {lines.map((l, i) => {
          const n = i + 1;
          const hl = file.from !== undefined && n >= file.from && n <= (file.to ?? file.from);
          return (
            <div key={i} ref={hl && n === file.from ? hlRef : undefined} style={{ display: 'flex', background: hl ? 'var(--acid)' : undefined }}>
              <span style={{ width: 44, flexShrink: 0, textAlign: 'right', paddingRight: 8, color: 'var(--muted)', userSelect: 'none' }}>{n}</span>
              <span style={{ whiteSpace: 'pre' }}>{l}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function BankRow({ bullet, sources, githubUrl, onOpen, onStatus }: {
  bullet: Bullet;
  sources: BulletSource[];
  githubUrl: string;
  onOpen: (s: BulletSource) => void;
  onStatus: (s: Bullet['status']) => void;
}) {
  const approved = bullet.status === 'APPROVED';
  return (
    <div className="panel panel--inset" style={{ padding: '10px 12px', borderLeft: approved ? '4px solid var(--ink)' : undefined }}>
      <div dangerouslySetInnerHTML={{ __html: markdownBoldToHtml(bullet.text) }} />
      <div className="row" style={{ flexWrap: 'wrap', gap: 6, marginTop: 8, alignItems: 'center' }}>
        <span className="label muted">{bullet.category}</span>
        {sources.map((src, i) => src.path ? (
          <button key={i} type="button" className="kw" title={`${src.field}: ${src.claim}`} onClick={() => onOpen(src)} style={{ cursor: 'pointer' }}>
            {src.path.split('/').pop()}:{src.startLine}-{src.endLine}
          </button>
        ) : (
          <a key={i} className="kw" title={`${src.field}: ${src.claim}`} href={`${githubUrl}/commit/${src.commit}`} target="_blank" rel="noreferrer">
            commit {src.commit?.slice(0, 7)}
          </a>
        ))}
        {sources.length === 0 && <span className="label muted">no traced source</span>}
        <span style={{ flex: 1 }} />
        <button className="btn btn--sm" onClick={() => onStatus(approved ? 'PENDING' : 'APPROVED')}
          style={{ background: approved ? 'var(--ink)' : 'var(--paper)', color: approved ? 'var(--paper)' : 'var(--ink)' }}>
          {approved ? '✓ APPROVED' : 'APPROVE'}
        </button>
        <button className="btn btn--ghost btn--sm" onClick={() => onStatus('REJECTED')}>REJECT</button>
      </div>
    </div>
  );
}
