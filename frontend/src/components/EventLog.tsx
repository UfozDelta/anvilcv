import { useEffect, useRef } from 'react';
import type { EventLogState } from '../lib/useEventLog';

interface Props {
  state: EventLogState;
}

/**
 * Scrollable log panel shown while an SSE pipeline is running.
 * Auto-scrolls to the latest line. Hides when there are no lines.
 * Colors: green = good, red = bad, yellow = info/in-progress.
 */
export function EventLog({ state }: Props) {
  const { lines, running, error } = state;
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  if (lines.length === 0 && !error) return null;

  return (
    <div style={{
      fontFamily: 'var(--mono)',
      fontSize: 11,
      lineHeight: 1.7,
      background: 'var(--paper)',
      border: 'var(--rule)',
      padding: '12px 16px',
      maxHeight: 280,
      overflowY: 'auto',
      marginTop: 16,
      marginBottom: 8,
    }}>
      {lines.map((line, i) => (
        <div key={i} style={{ color: lineColor(line) }}>
          {line}
        </div>
      ))}
      {error && (
        <div style={{ color: '#c00', marginTop: 4 }}>
          {error}
        </div>
      )}
      {running && (
        <div style={{ color: '#888', marginTop: 2 }}>...</div>
      )}
      <div ref={bottomRef} />
    </div>
  );
}

function lineColor(line: string): string {
  const l = line.toLowerCase();

  // Red — cuts, failures, errors, missing ATS
  if (l.startsWith('cut:')) return '#c05000';
  if (l.startsWith('pdf compile failed') || l.startsWith('tectonic:')) return '#c00';

  // Green — kept, saved, done, selected, matched ATS
  if (l.startsWith('kept:')) return '#2a7a2a';
  if (l.startsWith('saved ') || l.startsWith('done -')) return '#2a7a2a';
  if (l.startsWith('selection complete') || l.startsWith('  ')) return '#2a7a2a';
  if (l.startsWith('ats matched')) return '#2a7a2a';
  if (l.startsWith('retry result:') && l.includes('passed')) return '#2a7a2a';

  // Red-ish for ATS missing (not green)
  if (l.startsWith('ats missing')) return '#c05000';

  // Yellow/amber — everything else: info, calling LLM, filtering, retrying, ranking
  return '#8a6800';
}
