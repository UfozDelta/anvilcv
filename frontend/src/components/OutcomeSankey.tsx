import { useMemo } from 'react';
import { sankey, sankeyLeft, sankeyLinkHorizontal, type SankeyGraph } from 'd3-sankey';
import type { OutcomeHistoryEntry } from '../lib/api';

/**
 * Stage order. Doubles as the cycle guard: d3-sankey throws on a circular link, and
 * nothing stops someone marking interview and then applied again, so only moves that
 * advance along this list are drawn. Adding a stage means adding it here and to COLOR.
 */
export const RANK = ['applied', 'oa', 'interview', 'offer', 'rejected', 'ghosted'];

const COLOR: Record<string, string> = {
  applied: 'var(--ink)', oa: 'var(--acid)', interview: 'var(--acid)',
  offer: 'var(--ink)', rejected: 'var(--rust)', ghosted: 'var(--muted)',
};
const stageColor = (s: string) => COLOR[s] ?? 'var(--ink)';

const DEFAULT_W = 560, DEFAULT_H = 240, NODE_W = 14;

export interface Transition { from: string; to: string; count: number }

/**
 * An application's outcomes in the order they were set, with corrections undone: moving it back
 * to an earlier stage (interview, then applied again) means the later stage didn't stick, so it
 * stops counting as reached. Repeats collapse; stages outside RANK are kept as they come.
 */
export function normalizePath(outcomes: string[]): string[] {
  const path: string[] = [];
  for (const o of outcomes) {
    const rank = RANK.indexOf(o);
    if (rank !== -1) while (path.length && RANK.indexOf(path[path.length - 1]) >= rank) path.pop();
    path.push(o);
  }
  return path;
}

/**
 * Tally stage-to-stage moves across every application's history. Rows are grouped
 * by application, consecutive duplicates collapsed (re-marking the same outcome is
 * not a transition), then each adjacent pair counted once.
 */
export function countTransitions(history: OutcomeHistoryEntry[]): Transition[] {
  const byApp = new Map<string, OutcomeHistoryEntry[]>();
  for (const h of history) {
    if (!byApp.has(h.applicationId)) byApp.set(h.applicationId, []);
    byApp.get(h.applicationId)!.push(h);
  }
  const counts = new Map<string, number>();
  for (const rows of byApp.values()) {
    const seq = normalizePath(rows.map(r => r.outcome));
    for (let i = 0; i < seq.length - 1; i++) {
      const key = `${seq[i]}->${seq[i + 1]}`;
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  return Array.from(counts.entries()).map(([key, count]) => {
    const [from, to] = key.split('->');
    return { from, to, count };
  });
}

/** Drop backward and unknown-stage moves, which would make the graph cyclic. */
export function forwardOnly(edges: Transition[]): Transition[] {
  return edges.filter(e => {
    const a = RANK.indexOf(e.from), b = RANK.indexOf(e.to);
    return a !== -1 && b !== -1 && b > a;
  });
}

/** Terminal "no" stages. In `rich` mode each gets one node per stage it happened at. */
const TERMINAL = new Set(['rejected', 'ghosted']);
/** The stages that get a column header in rich mode. */
const MAIN = ['applied', 'oa', 'interview', 'offer'];

/** `id` is unique per node; `stage` is the outcome it stands for (several nodes can share one). */
interface Node { id: string; stage: string; origin?: string }
interface Link { source: string; target: string; value: number }

/**
 * `width`/`height` let a wide layout draw at its real size instead of scaling the 560x240 default up.
 * `rich` is the Simplify-style funnel: stage headers with counts and share of all applications,
 * left-aligned columns, and the ways an application can stop (rejected, ghosted) drawn as their own
 * nodes at the stage each happened, so the chart progresses forward instead of piling up on the far
 * right. Applications still waiting simply stay at Applied. Hover dims everything but the ribbon.
 */
export function OutcomeSankey({ history, width, height, selected, onSelect, rich, compact, labelSize }: {
  history: OutcomeHistoryEntry[]; width?: number; height?: number;
  /** Label px size when drawn at real size on a narrow screen; the default CSS size assumes the 560-wide drawing. */
  labelSize?: number;
  /** Optional: makes each stage node pressable. Omit and the chart is display-only. */
  selected?: string | null; onSelect?: (stage: string) => void;
  rich?: boolean;
  /** Rich + narrow: drop-off nodes show just their count (a legend carries the names), so labels can't collide. */
  compact?: boolean;
}) {
  const W = width ?? DEFAULT_W, H = height ?? DEFAULT_H;
  const TOP = rich ? 38 : 1;
  const graph = useMemo(() => {
    let edges = forwardOnly(countTransitions(history));
    // A drop-off is an end state, so nothing leaves it.
    if (rich) edges = edges.filter(e => !TERMINAL.has(e.from));
    if (edges.length === 0) return null;

    const info = new Map<string, Node>();
    const node = (id: string, stage: string, origin?: string) => { if (!info.has(id)) info.set(id, { id, stage, origin }); return id; };
    const links: Link[] = [];
    for (const e of edges) {
      const from = node(e.from, e.from);
      if (rich && TERMINAL.has(e.to)) {
        // One drop-off node per stage it happened at.
        links.push({ source: from, target: node(`${e.to}@${e.from}`, e.to, e.from), value: e.count });
        continue;
      }
      const i = MAIN.indexOf(e.from), j = MAIN.indexOf(e.to);
      if (rich && i !== -1 && j > i + 1) {
        // A ribbon that skips a stage (applied -> interview) would run straight through that column's nodes.
        // Route it through an invisible node in each skipped column, so the layout reserves a lane for it.
        let prev = from;
        for (let k = i + 1; k < j; k++) {
          const via = node(`via@${e.from}>${e.to}#${MAIN[k]}`, 'via', e.from);
          links.push({ source: prev, target: via, value: e.count });
          prev = via;
        }
        links.push({ source: prev, target: node(e.to, e.to), value: e.count });
      } else {
        links.push({ source: from, target: node(e.to, e.to), value: e.count });
      }
    }
    const nodes = [...info.values()].sort((a, b) => RANK.indexOf(a.stage) - RANK.indexOf(b.stage) || RANK.indexOf(a.origin ?? '') - RANK.indexOf(b.origin ?? ''));

    const layout = sankey<Node, Link>()
      .nodeId(d => d.id)
      .nodeWidth(NODE_W)
      .nodePadding(rich ? 14 : 18)
      .extent([[1, TOP], [W - 1, H - 1]]);
    if (rich) layout.nodeAlign(sankeyLeft);

    // d3-sankey mutates what it is given, so hand it throwaway objects.
    return layout({ nodes, links } as SankeyGraph<Node, Link>);
  }, [history, W, H, rich, TOP]);

  if (!graph) {
    return (
      <div className="muted" style={{ fontSize: 13 }}>
        {history.length === 0
          ? 'No outcome history recorded yet.'
          : `${history.length} history ${history.length === 1 ? 'entry' : 'entries'} loaded, but no application has advanced a stage yet.`}
      </div>
    );
  }

  const path = sankeyLinkHorizontal<Node, Link>();
  /**
   * A ribbon that skips a stage is laid out in two halves around an invisible node (which keeps the skipped
   * column's real nodes out of its lane). Draw it as one direct curve from its source to its target, so it
   * visibly goes straight past that column instead of flattening into it.
   */
  type Ribbon = { source: Node; target: Node; y0: number; y1: number; width: number; value: number };
  const ribbons = ((): Ribbon[] => {
    const chainKey = (n: Node) => n.id.slice(0, n.id.indexOf('#'));
    const out: Ribbon[] = [];
    const starts = new Map<string, (typeof graph.links)[number]>();
    for (const l of graph.links) {
      const src = l.source as Node, tgt = l.target as Node;
      if (tgt.stage === 'via' && src.stage !== 'via') starts.set(chainKey(tgt), l);
      else if (src.stage === 'via' && tgt.stage !== 'via') {
        const first = starts.get(chainKey(src));
        if (first) out.push({ source: first.source as Node, target: tgt, y0: first.y0 ?? 0, y1: l.y1 ?? 0, width: first.width ?? 1, value: l.value });
      } else if (src.stage !== 'via' && tgt.stage !== 'via') {
        out.push({ source: src, target: tgt, y0: l.y0 ?? 0, y1: l.y1 ?? 0, width: l.width ?? 1, value: l.value });
      }
    }
    return out;
  })();
  // Everyone who applied, including those who never moved (they have no ribbon, so the node undercounts).
  const total = new Set(history.map(h => h.applicationId)).size;
  const pct = (v: number) => (total ? `${Math.round((v / total) * 100)}%` : '');

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={width ? undefined : { maxWidth: W }}>
      <g className="sk-links">
        {ribbons.map((l, i) => (
          <path
            key={i}
            className="sk-link"
            d={path(l as never) ?? undefined}
            fill="none"
            // Drop-off ribbons take their end colour so a rejection reads red and silence reads grey.
            stroke={stageColor(rich && TERMINAL.has((l.target as Node).stage) ? (l.target as Node).stage : (l.source as Node).stage)}
            strokeOpacity={0.35}
            strokeWidth={Math.max(1, l.width)}
          >
            <title>{`${(l.source as Node).stage} → ${(l.target as Node).stage}: ${l.value}`}</title>
          </path>
        ))}
      </g>
      {graph.nodes.filter(n => n.stage !== 'via').map(n => {
        const x0 = n.x0 ?? 0, x1 = n.x1 ?? 0;
        const main = rich && MAIN.includes(n.stage);
        const lastCol = x1 >= W - 2;
        // Rich: labels sit to the right of every node except the last column, so neighbouring labels never collide.
        const leftHalf = rich ? !lastCol : x0 < W / 2;
        const on = selected === n.stage;
        const pressable = !!onSelect;
        return (
          <g
            key={n.id}
            {...(pressable ? {
              role: 'button', tabIndex: 0, 'aria-pressed': on, 'aria-label': `${n.stage}${n.origin ? ` after ${n.origin}` : ''}, ${n.value}: show applications`,
              style: { cursor: 'pointer' },
              onClick: () => onSelect(n.stage),
              onKeyDown: (e: React.KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(n.stage); } },
            } : {})}
          >
            <rect x={x0} y={n.y0} width={x1 - x0} height={Math.max(1, (n.y1 ?? 0) - (n.y0 ?? 0))}
                  fill={stageColor(n.stage)} stroke={on ? 'var(--ink)' : rich ? 'var(--ink)' : undefined} strokeWidth={on ? 3 : rich ? 1.5 : undefined} />
            {main ? (
              // Column header: stage, how many reached it, and their share of everything sent.
              <text x={lastCol ? x1 : x0} y={14} textAnchor={lastCol ? 'end' : 'start'} className="sankey__label sk-head" fill="var(--ink)" style={{ textTransform: 'uppercase', fontSize: labelSize }}>
                <tspan fontWeight={700}>{n.stage}</tspan>
                <tspan x={lastCol ? x1 : x0} dy="1.35em" fill="var(--muted)">{n.stage === 'applied' ? total : n.value} · {pct(n.stage === 'applied' ? total : n.value ?? 0)}</tspan>
              </text>
            ) : (
              <text
                x={leftHalf ? x1 + 6 : x0 - 6}
                y={((n.y0 ?? 0) + (n.y1 ?? 0)) / 2}
                textAnchor={leftHalf ? 'start' : 'end'}
                dominantBaseline="middle"
                className="sankey__label" fill="var(--ink)" style={{ textTransform: 'uppercase', fontSize: labelSize }}
              >
                {rich && compact ? n.value : <>{n.stage} ({n.value}){rich && total ? ` · ${pct(n.value ?? 0)}` : ''}</>}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}
