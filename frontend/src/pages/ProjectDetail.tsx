import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, type Project, type Bullet, CATEGORIES } from '../lib/api';
import { Section } from '../components/Section';
import { useEventLog } from '../lib/useEventLog';
import { EventLog } from '../components/EventLog';

export function ProjectDetail() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [bullets, setBullets] = useState<Bullet[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const { stream, state: logState, reset: resetLog } = useEventLog();
  const [editing, setEditing] = useState<string | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set(['ai-ml', 'backend']));
  const [sortMode, setSortMode] = useState<'category' | 'date'>('category');
  const [filterCat, setFilterCat] = useState<string | null>(null);

  async function load() {
    if (!id) return;
    setLoading(true);
    try {
      const [p, bs] = await Promise.all([
        api.get<Project>(`/api/projects/${id}`),
        api.get<Bullet[]>(`/api/projects/${id}/bullets`),
      ]);
      setProject(p); setBullets(bs);
    } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [id]);

  async function generateBank() {
    if (!id || picked.size === 0) return;
    setErr(null);
    resetLog();
    setGenerating(true);
    const cats = Array.from(picked);
    try {
      // SSE endpoint streams real per-bullet events; "done" resolves with total count.
      await stream(
        `/api/projects/${id}/bullets/generate-bank/stream`,
        { categories: cats }
      );
      await load();
    } catch (e: any) {
      setErr(e?.message || 'Generation failed');
    } finally {
      setGenerating(false);
    }
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

      <div className="row row--between row--centered" style={{ marginBottom: 10 }}>
        <Section num="01.A" title="Bullet Bank" count={bullets.length} />
        <div className="row" style={{ gap: 0 }}>
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

      {/* Live log panel — shows real events from bullet generation pipeline */}
      <EventLog state={logState} />

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
