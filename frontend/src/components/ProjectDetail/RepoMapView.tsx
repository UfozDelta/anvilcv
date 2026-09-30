import { useState } from 'react';
import { CATEGORIES, type RepoMap } from '../../lib/api';

const LENS_LABEL = Object.fromEntries(CATEGORIES.map(c => [c.slug, c.label]));

/**
 * The repo map, top-down: what the project is, the counted facts, the main flows, then
 * subsystems (tick to steer generation) expanding into their modules. Clicking a module
 * opens its central file.
 */
export function RepoMapView({ map, picked, onToggle, onOpenFile }: {
  map: RepoMap;
  picked: Set<string>;
  onToggle: (subsystem: string) => void;
  onOpenFile: (path: string) => void;
}) {
  const [open, setOpen] = useState<string | null>(null);
  const byPath = Object.fromEntries(map.modules.map(m => [m.path, m]));
  const p = map.project;

  return (
    <div className="panel panel--inset stack-sm">
      <div className="row row--between row--centered">
        <span className="label">REPO MAP · {map.sha.slice(0, 7)}</span>
        <span className="label muted">{map.modules.length} MODULES</span>
      </div>

      {p?.overview && <div style={{ fontSize: 14, lineHeight: 1.5 }}>{p.overview}</div>}
      {p?.audience && <div className="label muted">USED BY · {p.audience}</div>}

      {map.facts.length > 0 && (
        <div className="row" style={{ flexWrap: 'wrap', gap: 6 }}>
          {map.facts.map(f => (
            <span key={f.label} className="kw" title={`Counted from the code: ${f.source}`}>{f.value} {f.label}</span>
          ))}
        </div>
      )}

      {p && p.flows.length > 0 && (
        <div className="stack-sm">
          {p.flows.map(f => (
            <div key={f.name} style={{ fontFamily: 'var(--mono)', fontSize: 11 }}>
              <strong>{f.name}:</strong> {f.steps.join(' → ')}
            </div>
          ))}
        </div>
      )}

      {p && p.subsystems.length > 0 && (
        <div className="stack-sm">
          <div className="label muted">
            SUBSYSTEMS — {picked.size === 0 ? 'tick to focus generation (none = each lens picks its own)' : `${picked.size} FOCUSED`}
          </div>
          {p.subsystems.map(s => (
            <div key={s.name} style={{ borderTop: 'var(--rule-thin)', paddingTop: 6 }}>
              <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                <input type="checkbox" checked={picked.has(s.name)} onChange={() => onToggle(s.name)} style={{ marginTop: 3 }} />
                <button type="button" onClick={() => setOpen(open === s.name ? null : s.name)}
                  style={{ all: 'unset', cursor: 'pointer', flex: 1, minWidth: 0 }}>
                  <div><strong>{s.name}</strong> <span className="muted">{open === s.name ? '▲' : '▼'}</span></div>
                  <div className="muted" style={{ fontSize: 13 }}>{s.purpose}</div>
                  <div className="row" style={{ gap: 4, flexWrap: 'wrap', marginTop: 4 }}>
                    {s.lenses.map(l => <span key={l} className="label muted">{LENS_LABEL[l] ?? l}</span>)}
                  </div>
                </button>
              </div>
              {open === s.name && (
                <div className="stack-sm" style={{ margin: '6px 0 6px 24px' }}>
                  {s.modules.map(path => {
                    const m = byPath[path];
                    if (!m) return null;
                    return (
                      <button key={path} type="button" onClick={() => onOpenFile(m.topFile)}
                        style={{ all: 'unset', cursor: 'pointer', display: 'block' }} title={`Open ${m.topFile}`}>
                        <div style={{ fontFamily: 'var(--mono)', fontSize: 12 }}>
                          {m.path} <span className="muted">· {m.files} files · {m.loc.toLocaleString()} lines{m.routes.length ? ` · ${m.routes.length} endpoints` : ''}</span>
                        </div>
                        {m.summary && <div style={{ fontSize: 13 }}>{m.summary}</div>}
                        {m.purpose && <div className="muted" style={{ fontSize: 12 }}>Why: {m.purpose}</div>}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
