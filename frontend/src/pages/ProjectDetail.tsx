import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, type Project, type Bullet, CATEGORIES } from '../lib/api';
import { Section } from '../components/Section';
import { EventStream } from '../components/EventStream';
import { parseExtract } from '../lib/parseExtract';

export function ProjectDetail() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [bullets, setBullets] = useState<Bullet[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [picked, setPicked] = useState<Set<string>>(new Set(['ai-ml', 'backend']));
  const [sortMode, setSortMode] = useState<'category' | 'date'>('category');
  const [filterCat, setFilterCat] = useState<string | null>(null);
  const [enrichOpen, setEnrichOpen] = useState(false);
  const [enrichSaving, setEnrichSaving] = useState(false);
  const [enrichErr, setEnrichErr] = useState<string | null>(null);
  const [techStack, setTechStack] = useState('');
  const [yourRole, setYourRole] = useState('');
  const [ownership, setOwnership] = useState('');
  const [scaleImpact, setScaleImpact] = useState('');
  const [hardestProblem, setHardestProblem] = useState('');
  const [pasteOpen, setPasteOpen] = useState(false);
  const [pasteText, setPasteText] = useState('');
  const [pasteMsg, setPasteMsg] = useState<string | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [infoSaving, setInfoSaving] = useState(false);
  const [infoErr, setInfoErr] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editCompany, setEditCompany] = useState('');
  const [editLocation, setEditLocation] = useState('');
  const [editDates, setEditDates] = useState('');
  const [editDescription, setEditDescription] = useState('');

  async function load() {
    if (!id) return;
    setLoading(true);
    try {
      const [p, bs] = await Promise.all([
        api.get<Project>(`/api/projects/${id}`),
        api.get<Bullet[]>(`/api/projects/${id}/bullets`),
      ]);
      setProject(p); setBullets(bs);
      setTechStack(p.techStack || '');
      setYourRole(p.yourRole || '');
      setOwnership(p.ownership || '');
      setScaleImpact(p.scaleImpact || '');
      setHardestProblem(p.hardestProblem || '');
      setEditTitle(p.title || '');
      setEditCompany(p.company || '');
      setEditLocation(p.location || '');
      setEditDates(p.dates || '');
      setEditDescription(p.description || '');
    } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [id]);

  async function saveInfo() {
    if (!id) return;
    setInfoErr(null); setInfoSaving(true);
    try {
      await api.put(`/api/projects/${id}`, { title: editTitle, company: editCompany, location: editLocation, dates: editDates, description: editDescription });
      await load();
      setInfoOpen(false);
    } catch (e: any) {
      setInfoErr(e?.message || 'Save failed');
    } finally {
      setInfoSaving(false);
    }
  }

  async function saveEnrich() {
    if (!id) return;
    setEnrichErr(null); setEnrichSaving(true);
    try {
      await api.put(`/api/projects/${id}`, { techStack, yourRole, ownership, scaleImpact, hardestProblem, description: editDescription });
      await load();
      setEnrichOpen(false);
    } catch (e: any) {
      setEnrichErr(e?.message || 'Save failed');
    } finally {
      setEnrichSaving(false);
    }
  }

  function parseAndFill() {
    setPasteMsg(null);
    const fields = parseExtract(pasteText);
    const keys = Object.keys(fields) as (keyof typeof fields)[];
    if (keys.length === 0) {
      setPasteMsg('No recognized sections found. Paste the full extractor output.');
      return;
    }
    if (fields.techStack !== undefined) setTechStack(fields.techStack);
    if (fields.yourRole !== undefined) setYourRole(fields.yourRole);
    if (fields.ownership !== undefined) setOwnership(fields.ownership);
    if (fields.scaleImpact !== undefined) setScaleImpact(fields.scaleImpact);
    if (fields.hardestProblem !== undefined) setHardestProblem(fields.hardestProblem);
    if (fields.description !== undefined) setEditDescription(fields.description);
    setPasteMsg(`Filled ${keys.length}/6 fields — review below, then SAVE CONTEXT.`);
    setPasteOpen(false);
    setPasteText('');
  }

  function generateBank() {
    if (!id || picked.size === 0) return;
    setErr(null);
    setGenerating(true);
  }

  async function addBullet(text: string, tags: string[], category: string) {
    await api.post<Bullet>(`/api/projects/${id}/bullets`, { text, tags, category });
    setAdding(false);
    await load();
  }

  async function saveBullet(b: Bullet, text: string, tags: string[]) {
    await api.put<Bullet>(`/api/bullets/${b.id}`, { text, tags });
    setEditing(null);
    await load();
  }
  async function delBullet(b: Bullet) {
    if (!confirm(`Delete this bullet?`)) return;
    await api.del(`/api/bullets/${b.id}`);
    await load();
  }

  const categoryMap = useMemo(() => {
    const m = new Map<string, { label: string; blurb: string }>();
    for (const c of CATEGORIES) m.set(c.slug, { label: c.label, blurb: c.blurb });
    return m;
  }, []);

  const grouped = useMemo(() => {
    const map = new Map<string, Bullet[]>();
    for (const b of bullets) {
      const k = b.category || 'general';
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(b);
    }
    const ordered: { slug: string; label: string; blurb: string; rows: Bullet[] }[] = [];
    for (const c of CATEGORIES) {
      if (map.has(c.slug)) {
        ordered.push({ slug: c.slug, label: c.label, blurb: c.blurb, rows: map.get(c.slug)! });
        map.delete(c.slug);
      }
    }
    for (const [slug, rows] of map.entries()) {
      ordered.push({ slug, label: slug.toUpperCase(), blurb: '', rows });
    }
    return ordered;
  }, [bullets]);

  const visibleGroups = useMemo(() =>
    filterCat ? grouped.filter(g => g.slug === filterCat) : grouped,
  [grouped, filterCat]);

  const flatByDate = useMemo(() => {
    const src = filterCat ? bullets.filter(b => (b.category || 'general') === filterCat) : bullets;
    return [...src].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [bullets, filterCat]);

  const presentCats = useMemo(() => grouped.map(g => g.slug), [grouped]);

  function togglePick(slug: string) {
    setPicked(prev => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug); else next.add(slug);
      return next;
    });
  }

  if (loading) return <div className="shell"><span className="spinner">LOADING</span></div>;
  if (!project) return <div className="shell">Not found.</div>;

  const isExperience = project.kind === 'EXPERIENCE';
  const backHref = isExperience ? '/experiences' : '/projects';
  const backLabel = isExperience ? '← ALL EXPERIENCES' : '← ALL PROJECTS';
  const headline = isExperience ? (project.title || project.name) : project.name;

  return (
    <div className="shell">
      <div className="row row--between row--centered" style={{ marginBottom: 8 }}>
        <Link to={backHref} className="label muted" style={{ textDecoration: 'none' }}>{backLabel}</Link>
        <span className="label muted">KIND · {project.kind}</span>
      </div>

      <h1 className="display" style={{ fontSize: 56, margin: '8px 0 4px', lineHeight: 0.95 }}>{headline}</h1>
      {isExperience && (
        <div className="editorial" style={{ fontSize: 18, marginBottom: 12, color: 'var(--ink)' }}>
          {project.company}{project.location ? ` · ${project.location}` : ''}{project.dates ? ` · ${project.dates}` : ''}
        </div>
      )}
      <div className="editorial muted" style={{ fontSize: 16, marginBottom: 28, maxWidth: 760, whiteSpace: 'pre-wrap' }}>
        {project.description}
      </div>

      {/* Edit Info panel — experiences only */}
      {isExperience && (
        <div className="panel panel--inset stack-sm" style={{ marginBottom: 24 }}>
          <button
            type="button"
            style={{ all: 'unset', cursor: 'pointer', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
            onClick={() => setInfoOpen(o => !o)}
          >
            <span className="label">EDIT INFO</span>
            <span className="label muted">{infoOpen ? '▲ COLLAPSE' : '▼ EXPAND'}</span>
          </button>
          {infoOpen && (
            <div className="stack" style={{ marginTop: 8 }}>
              <label className="field">
                <div className="field__label">Title</div>
                <input className="field__input" value={editTitle} onChange={e => setEditTitle(e.target.value)} placeholder="Software Engineer Intern" />
              </label>
              <label className="field">
                <div className="field__label">Company</div>
                <input className="field__input" value={editCompany} onChange={e => setEditCompany(e.target.value)} placeholder="Acme Corp" />
              </label>
              <label className="field">
                <div className="field__label">Location</div>
                <input className="field__input" value={editLocation} onChange={e => setEditLocation(e.target.value)} placeholder="San Francisco, CA" />
              </label>
              <label className="field">
                <div className="field__label">Dates</div>
                <input className="field__input" value={editDates} onChange={e => setEditDates(e.target.value)} placeholder="Jun 2024 – Aug 2024" />
              </label>
              <label className="field">
                <div className="field__label">Description</div>
                <textarea className="field__textarea" value={editDescription} onChange={e => setEditDescription(e.target.value)} style={{ minHeight: 80 }} placeholder="What you did here…" />
              </label>
              {infoErr && <div className="err">{infoErr}</div>}
              <div className="row">
                <button className="btn btn--acid" onClick={saveInfo} disabled={infoSaving}>
                  {infoSaving ? <span className="spinner">SAVING</span> : 'SAVE INFO'}
                </button>
                <button className="btn btn--ghost" onClick={() => setInfoOpen(false)}>CANCEL</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Enrich Context panel */}
      {(() => {
        const filledCount = [project.techStack, project.yourRole, project.ownership, project.scaleImpact, project.hardestProblem].filter(Boolean).length;
        return (
          <div className="panel panel--inset stack-sm" style={{ marginBottom: 24 }}>
            <button
              type="button"
              style={{ all: 'unset', cursor: 'pointer', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
              onClick={() => setEnrichOpen(o => !o)}
            >
              <span className="label">ENRICH CONTEXT</span>
              <span className="label muted">{filledCount}/5 fields · {enrichOpen ? '▲ COLLAPSE' : '▼ EXPAND'}</span>
            </button>
            {filledCount === 0 && !enrichOpen && (
              <div style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.05em' }}>
                Add tech stack, role, and impact so AI generates stronger bullets.
              </div>
            )}
            {enrichOpen && (
              <div className="stack" style={{ marginTop: 8 }}>
                {/* Paste-from-extractor shortcut */}
                <div className="panel panel--inset stack-sm" style={{ background: 'var(--paper)' }}>
                  <button
                    type="button"
                    style={{ all: 'unset', cursor: 'pointer', width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                    onClick={() => { setPasteOpen(o => !o); setPasteMsg(null); }}
                  >
                    <span className="label">PASTE EXTRACT</span>
                    <span className="label muted">{pasteOpen ? '▲ COLLAPSE' : '▼ AUTO-FILL'}</span>
                  </button>
                  {!pasteOpen && (
                    <div style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.05em' }}>
                      Paste the Project Context Extractor output to auto-fill all fields below.
                    </div>
                  )}
                  {pasteOpen && (
                    <div className="stack-sm" style={{ marginTop: 8 }}>
                      <textarea
                        className="field__textarea"
                        value={pasteText}
                        onChange={e => setPasteText(e.target.value)}
                        style={{ minHeight: 160 }}
                        autoFocus
                        placeholder={"Paste the full extractor output here, e.g.\n\n## Tech Stack\nReact, PostgreSQL, AES-256-GCM…\n\n## Your Role\n…"}
                      />
                      <div className="row">
                        <button type="button" className="btn btn--acid btn--sm" onClick={parseAndFill} disabled={!pasteText.trim()}>PARSE &amp; FILL</button>
                        <button type="button" className="btn btn--ghost btn--sm" onClick={() => { setPasteOpen(false); setPasteText(''); }}>CANCEL</button>
                      </div>
                    </div>
                  )}
                  {pasteMsg && <div className="label muted" style={{ marginTop: 4 }}>{pasteMsg}</div>}
                </div>
                <label className="field">
                  <div className="field__label">Tech stack</div>
                  <input className="field__input" value={techStack} onChange={e => setTechStack(e.target.value)}
                    placeholder="React, PostgreSQL, FastAPI, Redis, Docker…" />
                </label>
                <label className="field">
                  <div className="field__label">Your role</div>
                  <input className="field__input" value={yourRole} onChange={e => setYourRole(e.target.value)}
                    placeholder="Solo / Lead / Contributor — e.g. 'Led backend, solo on infra'" />
                </label>
                <label className="field">
                  <div className="field__label">What you owned end-to-end</div>
                  <textarea className="field__textarea" value={ownership} onChange={e => setOwnership(e.target.value)}
                    style={{ minHeight: 80 }}
                    placeholder="I built the auth system, designed the DB schema, owned the data pipeline from ingestion to API…" />
                </label>
                <label className="field">
                  <div className="field__label">Scale & impact</div>
                  <input className="field__input" value={scaleImpact} onChange={e => setScaleImpact(e.target.value)}
                    placeholder="10k DAU, 200ms p99, reduced costs 40%, 3-person team…" />
                </label>
                <label className="field">
                  <div className="field__label">Hardest problem solved</div>
                  <textarea className="field__textarea" value={hardestProblem} onChange={e => setHardestProblem(e.target.value)}
                    style={{ minHeight: 80 }}
                    placeholder="Had to guarantee exactly-once delivery under network partitions…" />
                </label>
                <label className="field">
                  <div className="field__label">Description / architecture overview</div>
                  <textarea className="field__textarea" value={editDescription} onChange={e => setEditDescription(e.target.value)}
                    style={{ minHeight: 80 }}
                    placeholder="3–5 sentences: lead each with one subsystem + its technique or number…" />
                </label>
                {enrichErr && <div className="err">{enrichErr}</div>}
                <div className="row">
                  <button className="btn btn--acid" onClick={saveEnrich} disabled={enrichSaving}>
                    {enrichSaving ? <span className="spinner">SAVING</span> : 'SAVE CONTEXT'}
                  </button>
                  <button className="btn btn--ghost" onClick={() => setEnrichOpen(false)}>CANCEL</button>
                </div>
              </div>
            )}
          </div>
        );
      })()}

      <div className="row row--between row--centered" style={{ marginBottom: 10 }}>
        <Section num="01.A" title="Bullet Bank" count={bullets.length} />
        <div className="row" style={{ gap: 0 }}>
          <button
            className="btn btn--sm"
            onClick={() => { setAdding(a => !a); setEditing(null); }}
            style={{ background: adding ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)', marginRight: 8 }}
          >{adding ? '✕ CANCEL' : '＋ ADD BULLET'}</button>
          <button
            className="btn btn--sm"
            onClick={() => setSortMode('category')}
            style={{ background: sortMode === 'category' ? 'var(--ink)' : 'var(--paper)', color: sortMode === 'category' ? 'var(--paper)' : 'var(--ink)' }}
          >BY CATEGORY</button>
          <button
            className="btn btn--sm"
            onClick={() => setSortMode('date')}
            style={{ background: sortMode === 'date' ? 'var(--ink)' : 'var(--paper)', color: sortMode === 'date' ? 'var(--paper)' : 'var(--ink)', marginLeft: -2 }}
          >BY DATE</button>
        </div>
      </div>

      {adding && (
        <AddBullet onSave={addBullet} onCancel={() => setAdding(false)} />
      )}

      {/* Category filter pills */}
      {presentCats.length > 1 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
          <button
            className="btn btn--sm"
            onClick={() => setFilterCat(null)}
            style={{ background: filterCat === null ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)' }}
          >ALL</button>
          {grouped.map(g => (
            <button
              key={g.slug}
              className="btn btn--sm"
              onClick={() => setFilterCat(filterCat === g.slug ? null : g.slug)}
              style={{
                background: filterCat === g.slug ? 'var(--acid)' : 'var(--paper)',
                color: 'var(--ink)',
                borderColor: 'var(--ink)',
              }}
            >
              {g.label} <span style={{ opacity: 0.6, marginLeft: 4 }}>{g.rows.length}</span>
            </button>
          ))}
        </div>
      )}

      {/* Category picker */}
      <div className="panel panel--inset stack-sm" style={{ marginBottom: 20 }}>
        <div className="label">GENERATE BULLETS — PICK LENSES</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
          {CATEGORIES.map(c => {
            const on = picked.has(c.slug);
            return (
              <button
                type="button"
                key={c.slug}
                onClick={() => togglePick(c.slug)}
                className="btn btn--sm"
                style={{
                  textAlign: 'left',
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                  padding: '10px 12px',
                  background: on ? 'var(--ink)' : 'var(--paper)',
                  color: on ? 'var(--paper)' : 'var(--ink)',
                  borderColor: 'var(--ink)',
                }}
              >
                <span style={{ fontSize: 11, letterSpacing: '0.18em' }}>
                  {on ? '✓' : '○'} {c.label.toUpperCase()}
                </span>
                <span style={{ fontFamily: 'var(--mono)', fontWeight: 400, fontSize: 9.5, letterSpacing: '0.05em', textTransform: 'none', marginTop: 4, opacity: 0.8 }}>
                  {c.blurb}
                </span>
              </button>
            );
          })}
        </div>
        <div className="row row--between row--centered" style={{ marginTop: 4 }}>
          <span className="label muted">
            {picked.size === 0 ? 'PICK AT LEAST ONE LENS' : `${picked.size} LENSES · ~${picked.size * 12}s`}
          </span>
          <button className="btn btn--acid" onClick={generateBank} disabled={generating || picked.size === 0}>
            {generating
              ? <span className="spinner">GENERATING</span>
              : <>↻ GENERATE BANK</>}
          </button>
        </div>
      </div>

      {generating && (
        <EventStream
          submitUrl={`/api/projects/${id}/bullets/generate-bank/submit`}
          submitBody={{ categories: Array.from(picked) }}
          pollUrl={jobId => `/api/projects/jobs/${jobId}/progress`}
          onDone={_id => { setGenerating(false); load(); }}
          onClose={() => setGenerating(false)}
          title="GENERATING BULLETS..."
          doneLabel=""
        />
      )}

      {err && <div className="err" style={{ marginBottom: 16 }}>{err}</div>}

      {bullets.length === 0 && !generating && (
        <div className="editorial muted" style={{ padding: '40px 0' }}>
          Empty bank. Pick lenses above and generate.
        </div>
      )}

      {/* BY CATEGORY view */}
      {sortMode === 'category' && visibleGroups.map((g, gi) => (
        <div key={g.slug} style={{ marginBottom: 32 }}>
          <div style={{ marginBottom: 16, paddingBottom: 8, borderBottom: 'var(--rule-thick)' }}>
            <div className="row row--between row--centered">
              <span className="label">{g.label}</span>
              <span className="label muted">{g.rows.length}</span>
            </div>
            {g.blurb && (
              <div style={{ marginTop: 5, fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.05em' }}>
                {g.blurb}
              </div>
            )}
          </div>
          {g.rows.map((b, i) => editing === b.id ? (
            <EditBullet key={b.id} bullet={b} onCancel={() => setEditing(null)} onSave={(t, tg) => saveBullet(b, t, tg)} />
          ) : (
            <BulletRow key={b.id} bullet={b} index={i} onEdit={() => setEditing(b.id)} onDelete={() => delBullet(b)} />
          ))}
        </div>
      ))}

      {/* BY DATE view */}
      {sortMode === 'date' && (
        <div>
          {flatByDate.map((b, i) => {
            const cat = categoryMap.get(b.category);
            return editing === b.id ? (
              <EditBullet key={b.id} bullet={b} onCancel={() => setEditing(null)} onSave={(t, tg) => saveBullet(b, t, tg)} />
            ) : (
              <div key={b.id} className="bullet">
                <div className="bullet__rank">#{String(i + 1).padStart(2, '0')}</div>
                <div style={{ width: '100%' }}>
                  <div className="bullet__text" dangerouslySetInnerHTML={{ __html: markdownBoldToHtml(b.text) }} />
                  {cat && (
                    <div style={{ marginTop: 6, fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--muted)', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
                      {cat.label} — <span style={{ textTransform: 'none', letterSpacing: '0.04em' }}>{cat.blurb}</span>
                    </div>
                  )}
                  <div className="bullet__tags" style={{ marginTop: 6 }}>
                    {b.tags.map(t => <span key={t} className="tag">{t}</span>)}
                  </div>
                  <div className="row" style={{ marginTop: 8 }}>
                    <button className="btn btn--ghost btn--sm" onClick={() => setEditing(b.id)}>EDIT</button>
                    <button className="btn btn--ghost btn--sm btn--rust" onClick={() => delBullet(b)}>DELETE</button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function AddBullet({ onSave, onCancel }: {
  onSave: (text: string, tags: string[], category: string) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState('');
  const [tagsStr, setTagsStr] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0].slug);
  const [err, setErr] = useState<string | null>(null);

  function submit() {
    if (!text.trim()) { setErr('Text is required.'); return; }
    onSave(text.trim(), tagsStr.split(',').map(s => s.trim()).filter(Boolean), category);
  }

  return (
    <div className="bullet" style={{ marginBottom: 16 }}>
      <div className="bullet__rank">NEW</div>
      <div className="stack-sm" style={{ width: '100%' }}>
        <textarea
          className="field__textarea"
          value={text}
          onChange={e => { setText(e.target.value); setErr(null); }}
          placeholder="Reduced latency by 47ms by rewriting the query planner."
          style={{ minHeight: 80 }}
          autoFocus
        />
        <input
          className="field__input"
          value={tagsStr}
          onChange={e => setTagsStr(e.target.value)}
          placeholder="backend, performance (optional)"
        />
        <select
          className="field__input"
          value={category}
          onChange={e => setCategory(e.target.value)}
        >
          {CATEGORIES.map(c => (
            <option key={c.slug} value={c.slug}>{c.label} — {c.blurb}</option>
          ))}
        </select>
        {err && <div className="err">{err}</div>}
        <div className="row">
          <button className="btn btn--sm" onClick={submit}>SAVE</button>
          <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}

function BulletRow({ bullet, index, onEdit, onDelete }: {
  bullet: Bullet;
  index: number;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="bullet">
      <div className="bullet__rank">#{String(index + 1).padStart(2, '0')}</div>
      <div>
        <div className="bullet__text" dangerouslySetInnerHTML={{ __html: markdownBoldToHtml(bullet.text) }} />
        <div className="bullet__tags">
          {bullet.tags.map(t => <span key={t} className="tag">{t}</span>)}
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <button className="btn btn--ghost btn--sm" onClick={onEdit}>EDIT</button>
          <button className="btn btn--ghost btn--sm btn--rust" onClick={onDelete}>DELETE</button>
        </div>
      </div>
    </div>
  );
}

function EditBullet({ bullet, onSave, onCancel }: {
  bullet: Bullet;
  onSave: (text: string, tags: string[]) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(bullet.text);
  const [tagsStr, setTagsStr] = useState(bullet.tags.join(', '));
  return (
    <div className="bullet">
      <div className="bullet__rank">EDIT</div>
      <div className="stack-sm">
        <textarea className="field__textarea" value={text} onChange={e => setText(e.target.value)} style={{ minHeight: 80 }} />
        <input className="field__input" value={tagsStr} onChange={e => setTagsStr(e.target.value)} placeholder="backend, ai-ml" />
        <div className="row">
          <button className="btn btn--sm" onClick={() => onSave(text, tagsStr.split(',').map(s => s.trim()).filter(Boolean))}>SAVE</button>
          <button className="btn btn--ghost btn--sm" onClick={onCancel}>CANCEL</button>
        </div>
      </div>
    </div>
  );
}

/** Render **bold** as <strong>bold</strong> while escaping everything else. */
function markdownBoldToHtml(s: string): string {
  const escaped = s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  return escaped.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
}
