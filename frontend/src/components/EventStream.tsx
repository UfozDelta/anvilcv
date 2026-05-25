import { useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { API_BASE } from '../lib/api';

interface Props {
  jdText: string;
  jdUrl: string;
  roleEmphasis: string;
  onDone: (appId: string) => void;
  onClose: () => void;
}

function lineColor(line: string): string {
  const l = line.toLowerCase();
  if (l.startsWith('cut:')) return '#c05000';
  if (l.startsWith('pdf compile failed') || l.startsWith('tectonic:')) return '#c00';
  if (l.startsWith('kept:') || l.startsWith('saved ') || l.startsWith('done -')) return '#2a7a2a';
  if (l.startsWith('selection complete') || l.startsWith('  ')) return '#2a7a2a';
  if (l.startsWith('ats matched')) return '#2a7a2a';
  if (l.startsWith('retry result:') && l.includes('passed')) return '#2a7a2a';
  if (l.startsWith('ats missing')) return '#c05000';
  return '#8a6800';
}

/**
 * Modal popup that streams SSE pipeline events.
 *
 * Uses @microsoft/fetch-event-source instead of a manual fetch+ReadableStream
 * loop. The library fires onmessage synchronously per parsed SSE event rather
 * than inside microtask continuations, so flushSync can force a paint before
 * the next event arrives — fixing the React 18 batching problem.
 */
export function EventStream({ jdText, jdUrl, roleEmphasis, onDone, onClose }: Props) {
  const [lines, setLines] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const onDoneRef = useRef(onDone);
  onDoneRef.current = onDone;

  useEffect(() => {
    const ctrl = new AbortController();

    fetchEventSource(`${API_BASE}/api/applications/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        jdText: jdText.trim() || undefined,
        jdUrl: jdUrl.trim() || undefined,
        roleEmphasis,
      }),
      signal: ctrl.signal,
      // Disable automatic retry — let user close and resubmit on failure
      openWhenHidden: true,
      onmessage(ev) {
        if (ev.event === 'log') {
          // flushSync forces a synchronous DOM commit + paint before returning.
          // fetchEventSource fires this callback outside the microtask chain so
          // flushSync actually works here (unlike inside await reader.read()).
          flushSync(() => setLines(prev => [...prev, ev.data]));
        } else if (ev.event === 'done') {
          setDone(true);
          setTimeout(() => onDoneRef.current(ev.data), 600);
        } else if (ev.event === 'error') {
          setError(ev.data);
        }
      },
      onerror(err) {
        setError(err?.message || 'Stream error');
        // Throw to stop fetchEventSource from retrying
        throw err;
      },
    });

    return () => ctrl.abort();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        background: 'rgba(10,10,10,0.72)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
      onClick={e => { if (e.target === e.currentTarget && (done || !!error)) onClose(); }}
    >
      <div
        style={{
          background: 'var(--paper)',
          border: '2px solid var(--ink)',
          width: '100%',
          maxWidth: 680,
          maxHeight: '80vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 16px',
            borderBottom: '2px solid var(--ink)',
            background: 'var(--ink)',
            color: 'var(--paper)',
          }}
        >
          <span style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '0.18em', textTransform: 'uppercase' }}>
            {done ? 'PIPELINE COMPLETE' : error ? 'PIPELINE ERROR' : 'TAILORING RESUME...'}
          </span>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--paper)',
              cursor: 'pointer',
              fontFamily: 'var(--mono)',
              fontSize: 13,
              padding: '0 4px',
              lineHeight: 1,
            }}
          >
            ✕
          </button>
        </div>

        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '12px 16px',
            fontFamily: 'var(--mono)',
            fontSize: 11,
            lineHeight: 1.8,
          }}
        >
          {lines.length === 0 && !error && (
            <div style={{ color: 'var(--muted)' }}>Connecting...</div>
          )}
          {lines.map((line, i) => (
            <div key={i} style={{ color: lineColor(line) }}>{line}</div>
          ))}
          {error && (
            <div style={{ color: '#c00', marginTop: 8 }}>{error}</div>
          )}
          {!done && !error && lines.length > 0 && (
            <div style={{ color: '#888', marginTop: 4 }}>...</div>
          )}
          <div ref={bottomRef} />
        </div>

        {(done || !!error) && (
          <div
            style={{
              padding: '12px 16px',
              borderTop: '2px solid var(--ink)',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            <button className="btn btn--ghost btn--sm" onClick={onClose}>
              CLOSE
            </button>
            {done && (
              <span style={{ fontFamily: 'var(--mono)', fontSize: 11, color: '#2a7a2a', alignSelf: 'center' }}>
                Redirecting...
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
