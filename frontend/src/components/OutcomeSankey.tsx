import { useMemo } from 'react';
import type { OutcomeHistoryEntry } from '../lib/api';

const STAGES = ['applied', 'interview', 'offer', 'rejected'] as const;
const COL: Record<string, number> = { applied: 0, interview: 1, offer: 2, rejected: 2 };
const COLOR: Record<string, string> = {
  applied: 'var(--ink)', interview: 'var(--acid)', offer: 'var(--ink)', rejected: 'var(--rust)',
};

const W = 560, H = 260, NODE_W = 14, ROW_H = 34;

function nodeY(stage: string, rowInCol: number): number {
  return rowInCol * (ROW_H + 10) + 10;
}

// ponytail: hand-rolled flow diagram, not a general sankey layout — fine for our fixed 3-column
// stage set (applied/interview/offer|rejected); revisit with a real layout lib if stages grow.
export function OutcomeSankey({ history }: { history: OutcomeHistoryEntry[] }) {
  const { edges, colNodes } = useMemo(() => {
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
    const edges = Array.from(counts.entries()).map(([key, count]) => {
      const [from, to] = key.split('->');
      return { from, to, count };
    });

    const colNodes: Record<number, string[]> = { 0: [], 1: [], 2: [] };
    for (const s of STAGES) if (!colNodes[COL[s]].includes(s)) colNodes[COL[s]].push(s);

    return { edges, colNodes };
  }, [history]);

  if (edges.length === 0) {
    return <div className="muted" style={{ fontSize: 13 }}>Not enough transition data yet — mark a few outcomes to see the flow.</div>;
  }

  const maxCount = Math.max(...edges.map(e => e.count));
  const colX = [20, W / 2 - NODE_W / 2, W - 20 - NODE_W];

  const nodePos = new Map<string, { x: number; y: number }>();
  for (const [col, stages] of Object.entries(colNodes)) {
    stages.forEach((s, i) => nodePos.set(s, { x: colX[Number(col)], y: nodeY(s, i) }));
  }

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={{ maxWidth: W }}>
      {edges.map((e, i) => {
        const from = nodePos.get(e.from);
        const to = nodePos.get(e.to);
        if (!from || !to) return null;
        const strokeW = 2 + (e.count / maxCount) * 16;
        const x1 = from.x + NODE_W, y1 = from.y + ROW_H / 2;
        const x2 = to.x, y2 = to.y + ROW_H / 2;
        const midX = (x1 + x2) / 2;
        return (
          <path
            key={i}
            d={`M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`}
            fill="none"
            stroke={COLOR[e.from]}
            strokeOpacity={0.35}
            strokeWidth={strokeW}
          />
        );
      })}
      {Array.from(nodePos.entries()).map(([stage, pos]) => (
        <g key={stage}>
          <rect x={pos.x} y={pos.y} width={NODE_W} height={ROW_H} fill={COLOR[stage]} />
          <text x={pos.x + (COL[stage] === 0 ? NODE_W + 6 : -6)} y={pos.y + ROW_H / 2}
                textAnchor={COL[stage] === 0 ? 'start' : 'end'} dominantBaseline="middle"
                fontSize={11} fill="var(--ink)" style={{ textTransform: 'uppercase' }}>
            {stage}
          </text>
        </g>
      ))}
    </svg>
  );
}
