import { useEffect, useState } from 'react';
import { Section } from '../Section';
import { formStyles as styles } from '../form/styles';
import { api } from '../../lib/api';

interface EvalSetView {
  id: string;
  label: string;
  source: 'BASELINE' | 'GENERATED';
  note: string | null;
  status: 'RUNNING' | 'DONE' | 'FAILED';
  error: string | null;
  createdAt: string | null;
  count: number;
  projects: { id: string; name: string }[];
}

interface Summary {
  count: number;
  projects: number;
  perProject: number;
  identicalRate: number;
  overlapRate: number;
  vanityRate: number;
  multiSentenceRate: number;
  lengthFitRate: number;
  meanChars: number;
  weakOpenerRate: number;
  openerDiversity: number;
  topOpeners: string[];
  unbackedRate: number;
  categories: Record<string, number>;
}

interface Flags {
  identical: boolean;
  overlap: boolean;
  vanity: boolean;
  multiSentence: boolean;
  length: string | null;
  weakOpener: boolean;
  unbacked: string[];
}

interface Side {
  summary: Summary;
  bullets: { text: string; category: string; status: string; flags: Flags }[];
}

interface Compare {
  a: { id: string; label: string; summary: Summary };
  b: { id: string; label: string; summary: Summary };
  jobMatch: {
    applications: number;
    meanA: number;
    meanB: number;
    rows: { company: string; role: string; keywords: number; a: number; b: number }[];
  };
  projects: { projectId: string; name: string; a: Side; b: Side }[];
}

const mono = { fontFamily: 'var(--mono)' };
const note = { ...mono, fontSize: '0.72rem', color: 'var(--ink-3)', lineHeight: 1.6 };
const cell = { padding: '4px 8px', verticalAlign: 'top' as const };

/** better: +1 = higher is better, -1 = lower is better, 0 = informational. */
const METRICS: { key: keyof Summary; label: string; pct: boolean; better: 1 | -1 | 0 }[] = [
  { key: 'count', label: 'Bullets', pct: false, better: 0 },
  { key: 'perProject', label: 'Per project', pct: false, better: 0 },
  { key: 'identicalRate', label: 'Near-identical repeats', pct: true, better: -1 },
  { key: 'overlapRate', label: 'Overlap (same work, info)', pct: true, better: 0 },
  { key: 'vanityRate', label: 'Vanity numbers', pct: true, better: -1 },
  { key: 'unbackedRate', label: 'Unbacked numbers (approx)', pct: true, better: -1 },
  { key: 'multiSentenceRate', label: '2+ sentences', pct: true, better: -1 },
  { key: 'lengthFitRate', label: 'Length band fit', pct: true, better: 1 },
  { key: 'meanChars', label: 'Mean chars', pct: false, better: 0 },
  { key: 'weakOpenerRate', label: 'Weak openers', pct: true, better: -1 },
  { key: 'openerDiversity', label: 'Opener diversity', pct: true, better: 1 },
];

const fmt = (v: number, pct: boolean) => (pct ? `${(v * 100).toFixed(1)}%` : v.toFixed(1).replace(/\.0$/, ''));

function deltaStyle(delta: number, better: number) {
  if (better === 0 || Math.abs(delta) < 1e-9) return {};
  return delta * better > 0 ? { background: 'var(--acid)' } : { color: 'var(--rust)' };
}

function flagList(f: Flags): string[] {
  const out: string[] = [];
  if (f.identical) out.push('DUP');
  if (f.vanity) out.push('VANITY');
  if (f.unbacked.length) out.push(`UNBACKED ${f.unbacked.join(' ')}`);
  if (f.length) out.push(f.length.replace('_', '-'));
  if (f.multiSentence) out.push('2+SENT');
  if (f.weakOpener) out.push('WEAK');
  return out;
}

function BulletColumn({ side }: { side: Side }) {
  if (side.bullets.length === 0) return <p style={note}>—</p>;
  return (
    <ul style={{ margin: 0, paddingLeft: 16 }}>
      {side.bullets.map((b, i) => {
        const flags = flagList(b.flags);
        return (
          <li key={i} style={{ marginBottom: 8, fontSize: '0.78rem', lineHeight: 1.45 }}>
            <span style={{ ...mono, fontSize: '0.65rem', color: 'var(--ink-3)' }}>
              [{b.category}{b.status === 'APPROVED' ? ' ✓' : ''}]{' '}
            </span>
            {b.text.replace(/\*\*/g, '')}
            {flags.length > 0 && (
              <span style={{ ...mono, fontSize: '0.62rem', color: 'var(--rust)' }}> {flags.join(' · ')}</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export function BulletEvalSection() {
  const [sets, setSets] = useState<EvalSetView[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [a, setA] = useState('');
  const [b, setB] = useState('');
  const [cmp, setCmp] = useState<Compare | null>(null);
  const [busy, setBusy] = useState(false);
  const [picked, setPicked] = useState<string[]>([]);
  const [genNote, setGenNote] = useState('');
  const [openProject, setOpenProject] = useState<string | null>(null);

  const load = () => api.get<EvalSetView[]>('/api/admin/eval/sets')
    .then(s => { setSets(s); setErr(null); })
    .catch(e => setErr(e.message));

  useEffect(() => { load(); }, []);

  // Poll only while a dry-run generation is in flight.
  const running = sets.some(s => s.status === 'RUNNING');
  useEffect(() => {
    if (!running) return;
    const id = setInterval(load, 5000);
    return () => clearInterval(id);
  }, [running]);

  const reference = sets.find(s => s.id === a) ?? sets.find(s => s.source === 'BASELINE');

  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true); setErr(null);
    try { await fn(); await load(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  const compare = () => act(async () => {
    setCmp(await api.get<Compare>(`/api/admin/eval/compare?a=${a}&b=${b}`));
  });

  const generate = () => act(async () => {
    if (!reference) throw new Error('Snapshot the bank first');
    await api.post('/api/admin/eval/generate', { referenceSetId: reference.id, projectIds: picked, note: genNote });
    setPicked([]);
  });

  const done = sets.filter(s => s.status === 'DONE');

  return (
    <>
      <Section num="10" title="Bullet Eval" count={sets.length} />
      <div style={styles.section}>
        <p style={note}>
          Snapshots of your live bullet bank vs dry-run generations of the current generator. A snapshot
          copies the bullet table into an eval set (stored in the DB only). A dry run never writes the
          bullet table; it starts from an empty bank, so compare rates, not counts. Generation is not
          seeded — run the same config twice before trusting a small delta.
        </p>
        {err && <div className="err" style={{ marginTop: 8 }}>{err}</div>}

        <div style={{ overflowX: 'auto', marginTop: 12 }}>
          <table style={{ ...mono, fontSize: '0.7rem', width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--rule)' }}>
                <th style={cell}>Set</th><th style={cell}>Source</th><th style={cell}>Status</th>
                <th style={cell}>Bullets</th><th style={cell}>Note</th><th style={cell} />
              </tr>
            </thead>
            <tbody>
              {sets.map(s => (
                <tr key={s.id} style={{ borderBottom: 'var(--rule-thin)' }}>
                  <td style={cell}>{s.label}</td>
                  <td style={cell}>{s.source}</td>
                  <td style={cell}>{s.status}{s.error ? ` — ${s.error}` : ''}</td>
                  <td style={cell}>{s.count}</td>
                  <td style={cell}>{s.note}</td>
                  <td style={cell}>
                    {s.status !== 'RUNNING' && (
                      <button className="btn btn--ghost" disabled={busy}
                        onClick={() => confirm(`Delete eval set ${s.label}?`) && act(() => api.del(`/api/admin/eval/sets/${s.id}`))}>
                        DELETE
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {sets.length === 0 && <tr><td style={cell} colSpan={6}>No sets yet — snapshot the current bank.</td></tr>}
            </tbody>
          </table>
        </div>

        <div style={{ display: 'flex', gap: 12, marginTop: 16, flexWrap: 'wrap' }}>
          <button className="btn" disabled={busy} onClick={() => act(() => api.post('/api/admin/eval/snapshot'))}>
            SNAPSHOT CURRENT BANK
          </button>
        </div>

        {reference && (
          <div style={{ marginTop: 20 }}>
            <p style={note}>
              Dry-run generate (spends LLM tokens, logged as bullet_generation) over the lenses
              <b> {reference.label}</b> has for each picked project:
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 8 }}>
              {reference.projects.map(p => (
                <label key={p.id} style={{ ...mono, fontSize: '0.72rem', cursor: 'pointer' }}>
                  <input type="checkbox" checked={picked.includes(p.id)}
                    onChange={e => setPicked(x => e.target.checked ? [...x, p.id] : x.filter(i => i !== p.id))} />
                  {' '}{p.name}
                </label>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap' }}>
              <input value={genNote} onChange={e => setGenNote(e.target.value)}
                placeholder="note: git sha / what changed"
                style={{ ...mono, fontSize: '0.78rem', flex: 1, minWidth: 200, padding: '6px 8px' }} />
              <button className="btn" disabled={busy || running || picked.length === 0} onClick={generate}>
                {running ? 'GENERATING…' : `GENERATE (${picked.length})`}
              </button>
            </div>
          </div>
        )}

        {done.length > 0 && (
          <div style={{ display: 'flex', gap: 12, marginTop: 24, flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={styles.label}>Compare</span>
            {[{ v: a, set: setA, l: 'A' }, { v: b, set: setB, l: 'B' }].map(x => (
              <select key={x.l} value={x.v} onChange={e => x.set(e.target.value)}
                style={{ ...mono, fontSize: '0.78rem', padding: '6px 8px' }}>
                <option value="">{x.l}…</option>
                {done.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}
              </select>
            ))}
            <button className="btn btn--ghost" disabled={busy || !a || !b} onClick={compare}>COMPARE</button>
          </div>
        )}

        {cmp && (
          <>
            <div style={{ overflowX: 'auto', marginTop: 16 }}>
              <table style={{ ...mono, fontSize: '0.72rem', width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--rule)' }}>
                    <th style={cell}>Metric</th><th style={cell}>A · {cmp.a.label}</th>
                    <th style={cell}>B · {cmp.b.label}</th><th style={cell}>Δ</th>
                  </tr>
                </thead>
                <tbody>
                  {METRICS.map(m => {
                    const va = cmp.a.summary[m.key] as number;
                    const vb = cmp.b.summary[m.key] as number;
                    const d = vb - va;
                    return (
                      <tr key={m.key} style={{ borderBottom: 'var(--rule-thin)' }}>
                        <td style={cell}>{m.label}</td>
                        <td style={cell}>{fmt(va, m.pct)}</td>
                        <td style={cell}>{fmt(vb, m.pct)}</td>
                        <td style={cell}><span style={deltaStyle(d, m.better)}>{d > 0 ? '+' : ''}{fmt(d, m.pct)}</span></td>
                      </tr>
                    );
                  })}
                  <tr style={{ borderBottom: 'var(--rule-thin)' }}>
                    <td style={cell}>JD keyword coverage ({cmp.jobMatch.applications} apps)</td>
                    <td style={cell}>{fmt(cmp.jobMatch.meanA, true)}</td>
                    <td style={cell}>{fmt(cmp.jobMatch.meanB, true)}</td>
                    <td style={cell}>
                      <span style={deltaStyle(cmp.jobMatch.meanB - cmp.jobMatch.meanA, 1)}>
                        {fmt(cmp.jobMatch.meanB - cmp.jobMatch.meanA, true)}
                      </span>
                    </td>
                  </tr>
                  <tr>
                    <td style={cell}>Top openers</td>
                    <td style={cell}>{cmp.a.summary.topOpeners.join(', ')}</td>
                    <td style={cell}>{cmp.b.summary.topOpeners.join(', ')}</td>
                    <td style={cell} />
                  </tr>
                </tbody>
              </table>
            </div>
            <p style={{ ...note, marginTop: 8 }}>
              Coverage = share of each saved application's JD keywords carried by the ≤25 bullets the
              pre-filter would hand the ranker (no LLM). More bullets and keyword stuffing both raise
              it — read it beside the length and sentence rows.
            </p>

            <p style={{ ...note, marginTop: 20 }}>Per project — click to see bullets side by side:</p>
            {cmp.projects.map(p => (
              <div key={p.projectId} style={{ borderTop: 'var(--rule-thin)', padding: '8px 0' }}>
                <button className="btn btn--ghost" style={{ width: '100%', textAlign: 'left' }}
                  onClick={() => setOpenProject(o => o === p.projectId ? null : p.projectId)}>
                  {p.name} — A {p.a.summary.count} / B {p.b.summary.count} · dup {fmt(p.a.summary.identicalRate, true)} → {fmt(p.b.summary.identicalRate, true)}
                  {' '}· fit {fmt(p.a.summary.lengthFitRate, true)} → {fmt(p.b.summary.lengthFitRate, true)}
                </button>
                {openProject === p.projectId && (
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16, marginTop: 8 }}>
                    <div><div style={note}>A · {cmp.a.label}</div><BulletColumn side={p.a} /></div>
                    <div><div style={note}>B · {cmp.b.label}</div><BulletColumn side={p.b} /></div>
                  </div>
                )}
              </div>
            ))}
          </>
        )}
      </div>
    </>
  );
}
