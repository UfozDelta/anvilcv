import { useCallback, useEffect, useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { api, type Project } from '../lib/api';
import { useGenerationConfig } from '../hooks/useGenerationConfig';
import { useStoryBank } from '../hooks/useStoryBank';
import { bankCounts, isLive, STORY_MIN } from '../lib/storyBank';
import { MetaBlock } from '../components/ProjectDetail/MetaBlock';
import { RepoTab } from '../components/ProjectDetail/RepoTab';
import { StoryBullets } from '../components/ProjectDetail/StoryBullets';
import { StoryGenerate } from '../components/ProjectDetail/StoryGenerate';
import '../styles/story.css';

type Tab = 'bullets' | 'generate' | 'repo';

/** Project or experience page: header with the story count, description, then Bullets · Generate · Repo. */
export function ProjectDetail() {
  const { id = '' } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<Tab>(searchParams.get('tab') === 'repo' ? 'repo' : 'bullets');
  const [project, setProject] = useState<Project | null>(null);
  const [loading, setLoading] = useState(true);
  const { cfg } = useGenerationConfig();
  const sb = useStoryBank(id);

  const reloadProject = useCallback(async () => {
    setProject(await api.get<Project>(`/api/projects/${id}`));
  }, [id]);

  useEffect(() => {
    reloadProject().catch(() => setProject(null)).finally(() => setLoading(false));
  }, [reloadProject]);

  if (loading) return <div className="shell"><span className="spinner">LOADING</span></div>;
  if (!project) return <div className="shell">Not found.</div>;

  const isExp = project.kind === 'EXPERIENCE';
  const headline = isExp ? (project.title || project.name) : project.name;
  const counts = bankCounts(sb.stories, sb.cap);
  const liveCount = sb.bullets.filter(isLive).length;

  return (
    <div className="shell sf-page">
      <header className="sf-head">
        <div className="sf-head__id">
          <Link to={isExp ? '/experiences' : '/projects'} className="eyebrow sf-back">← {isExp ? 'Experiences' : 'Projects'}</Link>
          <h1 className="display sf-head__name">{headline}</h1>
        </div>
        <div className="sf-head__tools">
          <span className="sf-count" data-thin={counts.under || undefined}>
            <b>{counts.stories}</b> {counts.stories === 1 ? 'story' : 'stories'}
            {counts.under && ` ⚠ under ${STORY_MIN}`}
            {counts.full && <span className="sf-count__full">Full</span>}
          </span>
        </div>
      </header>

      {isExp && <MetaBlock project={project} onSaved={reloadProject} />}
      <DescBlock project={project} onSaved={reloadProject} />

      <div className="tabs sf-tabs" role="tablist" aria-label="Project">
        <button type="button" role="tab" aria-selected={tab === 'bullets'} className={tab === 'bullets' ? 'is-on' : ''} onClick={() => setTab('bullets')}>
          Bullets <span className="tabs__badge">{liveCount}</span>
        </button>
        <button type="button" role="tab" aria-selected={tab === 'generate'} className={tab === 'generate' ? 'is-on' : ''} onClick={() => setTab('generate')}>
          Generate
        </button>
        {!isExp && (
          <button type="button" role="tab" aria-selected={tab === 'repo'} className={tab === 'repo' ? 'is-on' : ''} onClick={() => setTab('repo')}>
            Repo
          </button>
        )}
      </div>

      <div className="tabpane sf-pane" role="tabpanel">
        {tab === 'bullets' && <StoryBullets cfg={cfg} sb={sb} />}
        {tab === 'generate' && <StoryGenerate sb={sb} />}
        {tab === 'repo' && !isExp && (
          <RepoTab id={id} project={project} onChanged={() => { reloadProject(); sb.load(); }} />
        )}
      </div>
    </div>
  );
}

/** Description: plain text, edited in place and saved only on Save. */
function DescBlock({ project, onSaved }: { project: Project; onSaved: () => void }) {
  const [edit, setEdit] = useState(false);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setErr(null);
    try {
      await api.put(`/api/projects/${project.id}`, { description: text });
      setEdit(false);
      onSaved();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Save failed.');
    } finally {
      setBusy(false);
    }
  }

  if (edit) {
    return (
      <div className="sf-desc" data-edit>
        <textarea className="field__textarea" aria-label="Description" value={text} autoFocus style={{ minHeight: 140 }}
          onChange={e => setText(e.target.value)} />
        {err && <div className="err">{err}</div>}
        <div className="sf-desc__acts">
          <button type="button" className="minibtn" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save'}</button>
          <button type="button" className="minibtn" disabled={busy} onClick={() => setEdit(false)}>Cancel</button>
        </div>
      </div>
    );
  }
  if (!project.description?.trim()) {
    return (
      <div className="sf-desc" data-empty>
        <button type="button" className="sf-desc__add" onClick={() => { setText(''); setEdit(true); }}>Add description</button>
      </div>
    );
  }
  return (
    <div className="sf-desc">
      <p className="sf-desc__text">{project.description}</p>
      <div className="sf-desc__acts">
        <button type="button" className="minibtn" onClick={() => { setText(project.description ?? ''); setEdit(true); }}>Edit</button>
      </div>
    </div>
  );
}
