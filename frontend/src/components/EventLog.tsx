import { useEffect, useRef } from 'react';
import type { EventLogState } from '../lib/useEventLog';

interface Props {
  state: EventLogState;
}

/**
 * Scrollable log panel shown while an SSE pipeline is running.
 * Auto-scrolls to the latest line. Hides when there are no lines.
 */
export function EventLog({ state }: Props) {
  const { lines, running, error } = state;
  const bottomRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to newest line as events arrive.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  if (lines.length === 0 && !error) return null;

  return (
    <div style={{
      fontFamily: 'var(--mono)',
      fontSize: 11,
      lineHeight: 1.6,
      background: 'var(--paper)',
      border: 'var(--rule)',
      borderRadius: 0,
      padding: '12px 16px',
      maxHeight: 260,
      overflowY: 'auto',
      marginTop: 16,
      marginBottom: 8,
    }}>
      {lines.map((line, i) => (
        <div key={i} style={{ color: lineColor(line) }}>
          {linePrefix(line)}{line}
        </div>
      ))}
      {error && (
        <div style={{ color: 'var(--err, #c00)', marginTop: 4 }}>
          ERROR: {error}
        </div>
      )}
      {running && (
        <div style={{ color: 'var(--muted)', marginTop: 4 }}>▌</div>
      )}
      <div ref={bottomRef} />
    </div>
  );
}

// Color-code lines by their content so cuts/keeps are visually distinct.
function lineColor(line: string): string {
  if (line.startsWith('Cut')) return '#c05000';
  if (line.startsWith('Kept')) return '#2a7a2a';
  if (line.startsWith('Rank #1:') || line.startsWith('Selected')) return 'var(--ink)';
  if (line.startsWith('Skipped')) return '#888';
  if (line.startsWith('Done') || line.startsWith('Saved')) return 'var(--ink)';
  return 'var(--muted, #666)';
}

function linePrefix(line: string): string {
  if (line.startsWith('Cut')) return '✗ ';
  if (line.startsWith('Kept')) return '✓ ';
  if (line.startsWith('Rank #')) return '→ ';
  if (line.startsWith('Done') || line.startsWith('Saved')) return '✓ ';
  if (line.startsWith('Skipped')) return '– ';
  return '  ';
}
