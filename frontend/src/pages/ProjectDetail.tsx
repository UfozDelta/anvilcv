import { useParams, Link } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { CATEGORIES } from '../lib/api';
import { Section } from '../components/Section';
import { EventStream } from '../components/EventStream';
import { useProjectDetail } from '../hooks/useProjectDetail';
import { AddBullet } from '../components/ProjectDetail/AddBullet';
import { BulletRow } from '../components/ProjectDetail/BulletRow';
import { EditBullet } from '../components/ProjectDetail/EditBullet';
import { EnrichDrawer } from '../components/ProjectDetail/EnrichDrawer';

export function ProjectDetail() {
  const { id } = useParams<{ id: string }>();
  const s = useProjectDetail(id);

  if (s.loading) return <div className="shell"><span className="spinner">LOADING</span></div>;
  if (!s.project) return <div className="shell">Not found.</div>;

  const project = s.project;
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
            onClick={() => s.setInfoOpen(o => !o)}
          >
            <span className="label">EDIT INFO</span>
            <span className="label muted">{s.infoOpen ? '▲ COLLAPSE' : '▼ EXPAND'}</span>
          </button>
          {s.infoOpen && (
            <div className="stack" style={{ marginTop: 8 }}>
              <label className="field">
                <div className="field__label">Title</div>
                <input className="field__input" value={s.editTitle} onChange={e => s.setEditTitle(e.target.value)} placeholder="Software Engineer Intern" />
              </label>
              <label className="field">
                <div className="field__label">Company</div>
                <input className="field__input" value={s.editCompany} onChange={e => s.setEditCompany(e.target.value)} placeholder="Acme Corp" />
              </label>
              <label className="field">
                <div className="field__label">Location</div>
                <input className="field__input" value={s.editLocation} onChange={e => s.setEditLocation(e.target.value)} placeholder="San Francisco, CA" />
              </label>
              <label className="field">
                <div className="field__label">Dates</div>
                <input className="field__input" value={s.editDates} onChange={e => s.setEditDates(e.target.value)} placeholder="Jun 2024 – Aug 2024" />
              </label>
              <label className="field">
                <div className="field__label">Description</div>
                <textarea className="field__textarea" value={s.editDescription} onChange={e => s.setEditDescription(e.target.value)} style={{ minHeight: 80 }} placeholder="What you did here…" />
              </label>
              {s.infoErr && <div className="err">{s.infoErr}</div>}
              <div className="row">
                <button className="btn btn--acid" onClick={s.saveInfo} disabled={s.infoSaving}>
                  {s.infoSaving ? <span className="spinner">SAVING</span> : 'SAVE INFO'}
                </button>
                <button className="btn btn--ghost" onClick={() => s.setInfoOpen(false)}>CANCEL</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Enrich Context — compact trigger, full editor lives in the drawer */}
      {(() => {
        const filledCount = [project.techStack, project.yourRole, project.ownership, project.scaleImpact, project.hardestProblem].filter(Boolean).length;
        return (
          <button
            type="button"
            className="panel panel--inset"
            style={{ all: 'unset', cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 16px', border: '1px solid var(--ink)', marginBottom: 24 }}
            onClick={() => s.setEnrichOpen(true)}
          >
            <span className="label">CONTEXT · {filledCount}/5 FIELDS</span>
            <span className="label muted">{filledCount === 0 ? 'ADD FOR STRONGER BULLETS →' : 'EDIT →'}</span>
          </button>
        );
      })()}

      <AnimatePresence>
        {s.enrichOpen && (
          <EnrichDrawer
            onClose={() => s.setEnrichOpen(false)}
            pasteOpen={s.pasteOpen} setPasteOpen={s.setPasteOpen}
            pasteText={s.pasteText} setPasteText={s.setPasteText}
            pasteMsg={s.pasteMsg} parseAndFill={s.parseAndFill}
            techStack={s.techStack} setTechStack={s.setTechStack}
            yourRole={s.yourRole} setYourRole={s.setYourRole}
            ownership={s.ownership} setOwnership={s.setOwnership}
            scaleImpact={s.scaleImpact} setScaleImpact={s.setScaleImpact}
            hardestProblem={s.hardestProblem} setHardestProblem={s.setHardestProblem}
            editDescription={s.editDescription} setEditDescription={s.setEditDescription}
            enrichErr={s.enrichErr} enrichSaving={s.enrichSaving} saveEnrich={s.saveEnrich}
          />
        )}
      </AnimatePresence>

      <div className="row" style={{ gap: 0, marginBottom: 10 }}>
        <button
          className="btn btn--sm"
          onClick={() => s.setStatusTab('bank')}
          style={{ background: s.statusTab === 'bank' ? 'var(--ink)' : 'var(--paper)', color: s.statusTab === 'bank' ? 'var(--paper)' : 'var(--ink)' }}
        >AI BULLET BANK <span style={{ opacity: 0.6, marginLeft: 4 }}>{s.bullets.filter(b => b.status !== 'APPROVED').length}</span></button>
        <button
          className="btn btn--sm"
          onClick={() => s.setStatusTab('approved')}
          style={{ background: s.statusTab === 'approved' ? 'var(--ink)' : 'var(--paper)', color: s.statusTab === 'approved' ? 'var(--paper)' : 'var(--ink)', marginLeft: -2 }}
        >APPROVED <span style={{ opacity: 0.6, marginLeft: 4 }}>{s.bullets.filter(b => b.status === 'APPROVED').length}</span></button>
      </div>

      <div className="row row--between row--centered" style={{ marginBottom: 10 }}>
        <Section num="01.A" title={s.statusTab === 'approved' ? 'Approved Bullets' : 'AI Bullet Bank'} count={s.grouped.reduce((n, g) => n + g.rows.length, 0)} />
        <div className="row" style={{ gap: 0 }}>
          <button
            className="btn btn--sm"
            onClick={() => s.preview.preview(s.displayed.map(b => b.id))}
            disabled={s.preview.busy || s.displayed.length === 0}
            title="Compile the bullets shown below onto a real resume page"
            style={{ background: 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)', marginRight: 8 }}
          >{s.preview.busy ? 'RENDERING...' : `RENDER PDF (~${s.displayedLines} LINES)`}</button>
          <button
            className="btn btn--sm"
            onClick={() => { s.setAdding(a => !a); s.setEditing(null); }}
            style={{ background: s.adding ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)', marginRight: 8 }}
          >{s.adding ? '✕ CANCEL' : '＋ ADD BULLET'}</button>
          <button
            className="btn btn--sm"
            onClick={s.refitBullets}
            disabled={s.refitting || s.offBandIds.size === 0}
            title="Rewrite bullets whose length misses the page bands"
            style={{ background: 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)', marginRight: 8 }}
          >
            {s.refitting
              ? <span className="spinner">REFITTING</span>
              : s.offBandIds.size === 0 ? 'ALL FIT' : `REFIT ${s.offBandIds.size}`}
          </button>
          <button
            className="btn btn--sm"
            onClick={() => s.setSortMode('category')}
            style={{ background: s.sortMode === 'category' ? 'var(--ink)' : 'var(--paper)', color: s.sortMode === 'category' ? 'var(--paper)' : 'var(--ink)' }}
          >BY CATEGORY</button>
          <button
            className="btn btn--sm"
            onClick={() => s.setSortMode('date')}
            style={{ background: s.sortMode === 'date' ? 'var(--ink)' : 'var(--paper)', color: s.sortMode === 'date' ? 'var(--paper)' : 'var(--ink)', marginLeft: -2 }}
          >BY DATE</button>
        </div>
      </div>

      {s.preview.err && <div className="err" style={{ marginBottom: 12 }}>{s.preview.err}</div>}

      {s.preview.url && (
        <div style={{ marginBottom: 20 }}>
          <div className="row row--between row--centered" style={{ background: 'var(--acid)', color: 'var(--ink)', padding: '6px 10px', border: '2px solid var(--ink)', borderBottom: 'none' }}>
            <span className="label" style={{ fontWeight: 700 }}>
              RENDER · {s.displayed.length} BULLETS · NOT SAVED
            </span>
            <button className="btn btn--ghost btn--sm" style={{ fontSize: 10, padding: '2px 6px' }} onClick={s.preview.close}>✕ CLOSE</button>
          </div>
          <iframe src={s.preview.url} title="bullet render" style={{ width: '100%', height: 900, border: '2px solid var(--ink)', background: '#fff' }} />
        </div>
      )}

      {s.refitMsg && (
        <div className="label muted" style={{ marginBottom: 10 }}>{s.refitMsg}</div>
      )}

      {s.adding && (
        <AddBullet onSave={s.addBullet} onCancel={() => s.setAdding(false)} />
      )}

      {/* Category filter pills */}
      {s.presentCats.length > 1 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
          <button
            className="btn btn--sm"
            onClick={() => s.setFilterCat(null)}
            style={{ background: s.filterCat === null ? 'var(--acid)' : 'var(--paper)', color: 'var(--ink)', borderColor: 'var(--ink)' }}
          >ALL</button>
          {s.grouped.map(g => (
            <button
              key={g.slug}
              className="btn btn--sm"
              onClick={() => s.setFilterCat(s.filterCat === g.slug ? null : g.slug)}
              style={{
                background: s.filterCat === g.slug ? 'var(--acid)' : 'var(--paper)',
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
            const on = s.picked.has(c.slug);
            return (
              <button
                type="button"
                key={c.slug}
                onClick={() => s.togglePick(c.slug)}
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
            {s.picked.size === 0 ? 'PICK AT LEAST ONE LENS' : `${s.picked.size} LENSES · ~${s.picked.size * 12}s`}
          </span>
          <button className="btn btn--acid" onClick={s.generateBank} disabled={s.generating || s.picked.size === 0}>
            {s.generating
              ? <span className="spinner">GENERATING</span>
              : <>↻ GENERATE BANK</>}
          </button>
        </div>
      </div>

      {s.generating && (
        <EventStream
          submitUrl={`/api/projects/${id}/bullets/generate-bank/submit`}
          submitBody={{ categories: Array.from(s.picked) }}
          pollUrl={jobId => `/api/projects/jobs/${jobId}/progress`}
          onDone={_id => { s.setGenerating(false); s.load(); }}
          onClose={() => s.setGenerating(false)}
          title="GENERATING BULLETS..."
          doneLabel=""
        />
      )}

      {s.err && <div className="err" style={{ marginBottom: 16 }}>{s.err}</div>}

      {s.grouped.length === 0 && !s.generating && (
        <div className="editorial muted" style={{ padding: '40px 0' }}>
          {s.statusTab === 'approved' ? 'No approved bullets yet.' : 'Empty bank. Pick lenses above and generate.'}
        </div>
      )}

      {/* BY CATEGORY view */}
      {s.sortMode === 'category' && s.visibleGroups.map((g) => (
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
          {g.rows.map((b, i) => s.editing === b.id ? (
            <EditBullet key={b.id} bullet={b} cfg={s.cfg} onCancel={() => s.setEditing(null)} onSave={(t, tg) => s.saveBullet(b, t, tg)} />
          ) : (
            <BulletRow key={b.id} bullet={b} index={i} cfg={s.cfg} onEdit={() => s.setEditing(b.id)} onDelete={() => s.delBullet(b)}
              onToggleApprove={() => s.setBulletStatus(b, b.status === 'APPROVED' ? 'PENDING' : 'APPROVED')} />
          ))}
        </div>
      ))}

      {/* BY DATE view */}
      {s.sortMode === 'date' && (
        <div>
          {s.flatByDate.map((b, i) => {
            const cat = s.categoryMap.get(b.category);
            return s.editing === b.id ? (
              <EditBullet key={b.id} bullet={b} cfg={s.cfg} onCancel={() => s.setEditing(null)} onSave={(t, tg) => s.saveBullet(b, t, tg)} />
            ) : (
              <BulletRow key={b.id} bullet={b} index={i} cfg={s.cfg} onEdit={() => s.setEditing(b.id)} onDelete={() => s.delBullet(b)}
                onToggleApprove={() => s.setBulletStatus(b, b.status === 'APPROVED' ? 'PENDING' : 'APPROVED')}
                categoryLabel={cat} />
            );
          })}
        </div>
      )}
    </div>
  );
}
