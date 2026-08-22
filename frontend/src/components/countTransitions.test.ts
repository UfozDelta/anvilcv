import { describe, it, expect } from 'vitest';
import type { OutcomeHistoryEntry } from '../lib/api';
import { countTransitions, forwardOnly } from './OutcomeSankey';

function row(applicationId: string, outcome: string, changedAt: string): OutcomeHistoryEntry {
  return { applicationId, outcome, changedAt };
}

describe('countTransitions', () => {
  it('counts each adjacent pair in an application timeline', () => {
    const edges = countTransitions([
      row('a', 'applied',   '2026-01-01'),
      row('a', 'interview', '2026-01-02'),
      row('a', 'offer',     '2026-01-03'),
    ]);
    expect(edges).toEqual([
      { from: 'applied', to: 'interview', count: 1 },
      { from: 'interview', to: 'offer', count: 1 },
    ]);
  });

  it('sums the same transition across applications', () => {
    const edges = countTransitions([
      row('a', 'applied', '2026-01-01'), row('a', 'rejected', '2026-01-02'),
      row('b', 'applied', '2026-01-01'), row('b', 'rejected', '2026-01-02'),
    ]);
    expect(edges).toEqual([{ from: 'applied', to: 'rejected', count: 2 }]);
  });

  it('ignores a re-mark of the outcome already in effect', () => {
    const edges = countTransitions([
      row('a', 'applied', '2026-01-01'),
      row('a', 'applied', '2026-01-02'),
    ]);
    expect(edges).toEqual([]);
  });

  it('yields no edges for an application with a single entry', () => {
    expect(countTransitions([row('a', 'interview', '2026-01-01')])).toEqual([]);
  });
});

describe('forwardOnly', () => {
  it('keeps moves that advance along RANK', () => {
    expect(forwardOnly([{ from: 'applied', to: 'offer', count: 2 }]))
      .toEqual([{ from: 'applied', to: 'offer', count: 2 }]);
  });

  it('drops a backward move, which would make the graph cyclic', () => {
    expect(forwardOnly([
      { from: 'applied', to: 'interview', count: 3 },
      { from: 'interview', to: 'applied', count: 1 },
    ])).toEqual([{ from: 'applied', to: 'interview', count: 3 }]);
  });

  it('drops stages missing from RANK', () => {
    expect(forwardOnly([{ from: 'applied', to: 'ghosted', count: 1 }])).toEqual([]);
  });

  it('places oa between applied and interview', () => {
    expect(forwardOnly([
      { from: 'applied', to: 'oa', count: 1 },
      { from: 'oa', to: 'interview', count: 1 },
      { from: 'interview', to: 'oa', count: 1 },
    ])).toEqual([
      { from: 'applied', to: 'oa', count: 1 },
      { from: 'oa', to: 'interview', count: 1 },
    ]);
  });
});
