import { useEffect, useMemo, useRef, useState } from 'react';
import { api, CATEGORIES, type Project, type RepoMap, type RepoTree } from '../../lib/api';
import { EventStream } from '../EventStream';
import { RepoPicker } from '../github/RepoPicker';
import { RepoMapView } from './RepoMapView';

const MAX_TREE_ROWS = 500;

/**
 * Repo view: the linked repo's tree and files on the left, the repo map and the explorer on the
 * right. The user steers the explorer (pin / exclude paths, notes, lenses) and runs it; bullets
 * are written from the Generate tab.
 */
export function RepoTab({ id, project, onChanged }: { id: string; project: Project; onChanged: () => void }) {
  const linked = !!project.repoCommitSha;
  const [relinking, setRelinking] = useState(false);
  const [ack, setAck] = useState(false);
  const warn = project.kind === 'EXPERIENCE';
  const blocked = repoActionBlocked(project.kind, ack);

  if (!linked || relinking) {
    return (
      <div className="stack-sm">
        {warn && <RepoWarning ack={ack} onAck={setAck} />}
        <RepoPicker
          projectId={id}
          onLinked={() => { setRelinking(false); onChanged(); }}
          onCancel={linked ? () => setRelinking(false) : undefined}
        />
      </div>
    );
  }
  return (
    <div className="stack-sm">
      {warn && <RepoWarning ack={ack} onAck={setAck} />}
      <LinkedRepo id={id} project={project} onChanged={onChanged} onRelink={() => setRelinking(true)} blocked={blocked} />
    </div>
  );
}

/** Work code may need the employer's consent before it goes to a third-party AI provider. Only experiences show it. */
export function repoActionBlocked(kind: string, acknowledged: boolean): boolean {
  return kind === 'EXPERIENCE' && !acknowledged;
}

export const REPO_WARNING_TEXT =
  "Work code may be under NDA or your employer's confidentiality terms. Repo map reads your code and sends excerpts to an AI provider. Use at your own risk — you are responsible for what you submit.";

export const REPO_STORAGE_TEXT =
  'What AnvilCV saves: a summary of each module, and up to 15 lines of source for each claim the explorer cites, on this experience. The full repository is held in memory during a run and is not saved.';

function RepoWarning({ ack, onAck }: { ack: boolean; onAck: (v: boolean) => void }) {
  return (
    <div className="panel panel--inset stack-sm" role="note" style={{ borderColor: 'var(--rust)' }}>
      <div className="label" style={{ color: 'var(--rust)' }}>Read before running the repo map</div>
      <p style={{ margin: 0 }}>{REPO_WARNING_TEXT}</p>
      <p className="label muted" style={{ margin: 0 }}>{REPO_STORAGE_TEXT}</p>
      <label className="na-check">
        <input type="checkbox" checked={ack} onChange={e => onAck(e.target.checked)} />
        <span>I have the right to use this code with AnvilCV and accept this risk.</span>
      </label>
    </div>
  );
}

function LinkedRepo({ id, project, onChanged, onRelink, blocked }: { id: string; project: Project; onChanged: () => void; onRelink: () => void; blocked: boolean }) {
  const repoName = (project.githubUrl ?? '').replace('https://github.com/', '');
  const [tree, setTree] = useState<RepoTree | null>(null);
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState<{ path: string; content: string; from?: number; to?: number } | null>(null);
  const [pins, setPins] = useState<Set<string>>(new Set());
  const [excludes, setExcludes] = useState<Set<string>>(new Set());
  const [notes, setNotes] = useState('');
  const [lenses, setLenses] = useState<Set<string>>(new Set(CATEGORIES.map(c => c.slug)));
  const [exploring, setExploring] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [pulling, setPulling] = useState(false);
  const [map, setMap] = useState<RepoMap | null>(null);
  const [focusSubs, setFocusSubs] = useState<Set<string>>(new Set());
  const [rebuildMap, setRebuildMap] = useState(false);

  useEffect(() => {
    setTree(null);
    api.get<RepoTree>(`/api/github/projects/${id}/tree`).then(setTree).catch(e => setErr(e?.message || 'Could not load repo tree'));
  }, [id, project.repoCommitSha]);

  useEffect(() => {
    // 204 (no body) until the first exploration builds the map.
    api.get<RepoMap | ''>(`/api/github/projects/${id}/map`).then(m => setMap(m || null)).catch(() => setMap(null));
  }, [id, project.repoCommitSha, project.repoContextReady, exploring]);

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
      onChanged();
    } catch (e: any) {
      setErr(e?.message || 'Pull failed');
    } finally {
      setPulling(false);
    }
  }

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
          {map && (
            <RepoMapView map={map} picked={focusSubs}
              onToggle={name => toggle(focusSubs, setFocusSubs, name)}
              onOpenFile={path => openFile(path)} />
          )}

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
              <button className="btn btn--acid btn--sm" onClick={() => { setRebuildMap(false); setExploring(true); }} disabled={exploring || blocked}>
                {exploring ? <span className="spinner">EXPLORING</span> : map ? '⌕ EXPLORE REPO' : '⌕ MAP + EXPLORE REPO'}
              </button>
              {map && (
                <button className="btn btn--ghost btn--sm" onClick={() => { setRebuildMap(true); setExploring(true); }} disabled={exploring || blocked}
                  title="Re-summarize the map even though the commit hasn't changed">
                  ↻ REBUILD MAP
                </button>
              )}
            </div>
            <div className="label muted">
              Explore maps the repo (once per commit) and fills the context fields that bullet generation reads from verified code.
            </div>
          </div>

        </div>
      </div>

      {exploring && (
        <EventStream
          submitUrl={`/api/github/projects/${id}/explore/submit${rebuildMap ? '?rebuildMap=true' : ''}`}
          submitBody={{ pinPaths: [...pins], excludePaths: [...excludes], notes, lenses: [...lenses] }}
          pollUrl={jobId => `/api/projects/jobs/${jobId}/progress`}
          onDone={() => { setExploring(false); onChanged(); }}
          onClose={() => setExploring(false)}
          title="EXPLORING REPO..."
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
