import { useMemo } from 'react';
import { sankey, sankeyLinkHorizontal, type SankeyGraph } from 'd3-sankey';
import type { OutcomeHistoryEntry } from '../lib/api';

/**
 * Stage order. Doubles as the cycle guard: d3-sankey throws on a circular link, and
 * nothing stops someone marking interview and then applied again, so only moves that
 * advance along this list are drawn. Adding a stage means adding it here and to COLOR.
 */
export const RANK = ['applied', 'oa', 'interview', 'offer', 'rejected'];

const COLOR: Record<string, string> = {
  applied: 'var(--ink)', oa: 'var(--acid)', interview: 'var(--acid)',
  offer: 'var(--ink)', rejected: 'var(--rust)',
};
const stageColor = (s: string) => COLOR[s] ?? 'var(--ink)';

const W = 560, H = 240, NODE_W = 14;

export interface Transition { from: string; to: string; count: number }

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
    const seq = rows.map(r => r.outcome).filter((o, i, arr) => i === 0 || arr[i - 1] !== o);
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

interface Node { name: string }
interface Link { source: string; target: string; value: number }

export function OutcomeSankey({ history }: { history: OutcomeHistoryEntry[] }) {
  const graph = useMemo(() => {
    const edges = forwardOnly(countTransitions(history));
    if (edges.length === 0) return null;

    const names = RANK.filter(s => edges.some(e => e.from === s || e.to === s));
    const layout = sankey<Node, Link>()
      .nodeId(d => d.name)
      .nodeWidth(NODE_W)
      .nodePadding(18)
      .extent([[1, 1], [W - 1, H - 1]]);

    // d3-sankey mutates what it is given, so hand it throwaway objects.
    return layout({
      nodes: names.map(name => ({ name })),
      links: edges.map(e => ({ source: e.from, target: e.to, value: e.count })),
    } as SankeyGraph<Node, Link>);
  }, [history]);

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

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={{ maxWidth: W }}>
      {graph.links.map((l, i) => (
        <path
          key={i}
          d={path(l) ?? undefined}
          fill="none"
          stroke={stageColor((l.source as Node).name)}
          strokeOpacity={0.35}
          strokeWidth={Math.max(1, l.width ?? 1)}
        >
          <title>{`${(l.source as Node).name} → ${(l.target as Node).name}: ${l.value}`}</title>
        </path>
      ))}
      {graph.nodes.map(n => {
        const leftHalf = (n.x0 ?? 0) < W / 2;
        return (
          <g key={n.name}>
            <rect x={n.x0} y={n.y0} width={(n.x1 ?? 0) - (n.x0 ?? 0)} height={Math.max(1, (n.y1 ?? 0) - (n.y0 ?? 0))}
                  fill={stageColor(n.name)} />
            <text
              x={leftHalf ? (n.x1 ?? 0) + 6 : (n.x0 ?? 0) - 6}
              y={((n.y0 ?? 0) + (n.y1 ?? 0)) / 2}
              textAnchor={leftHalf ? 'start' : 'end'}
              dominantBaseline="middle"
              fontSize={11} fill="var(--ink)" style={{ textTransform: 'uppercase' }}
            >
              {n.name} ({n.value})
            </text>
          </g>
        );
      })}
    </svg>
  );
}
