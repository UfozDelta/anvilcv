import { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../lib/api';

interface Props {
  jdText: string;
  jdUrl: string;
  roleEmphasis: string;
  includeCoverLetter: boolean;
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
 * Modal popup that shows pipeline progress via polling.
 *
 * Replaces SSE streaming entirely. On mount, POSTs to /api/applications/submit
 * which returns a jobId immediately and runs the pipeline in a background thread.
 * Then polls GET /api/applications/jobs/{jobId}/progress every 1.5s.
 *
 * Polling sidesteps every React 18 scheduler / SSE batching issue:
 * each poll response is a normal fetch that updates state in a standard
 * React event handler, so setState triggers immediate re-renders.
 */
export function EventStream({ jdText, jdUrl, roleEmphasis, includeCoverLetter, onDone, onClose }: Props) {
  const [lines, setLines] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [connecting, setConnecting] = useState(true);
  const bottomRef = useRef<HTMLDivElement>(null);
  const onDoneRef = useRef(onDone);
  onDoneRef.current = onDone;

  useEffect(() => {
    let intervalId: ReturnType<typeof setInterval> | null = null;
    let cancelled = false;

    async function start() {
      try {
        const res = await fetch(`${API_BASE}/api/applications/submit`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            jdText: jdText.trim() || undefined,
            jdUrl: jdUrl.trim() || undefined,
            roleEmphasis,
            includeCoverLetter,
          }),
        });
        if (!res.ok) {
          const text = await res.text();
          if (!cancelled) setError(text || `HTTP ${res.status}`);
          return;
        }
        const { jobId } = await res.json();
        if (cancelled) return;
        setConnecting(false);

        intervalId = setInterval(async () => {
          try {
            const r = await fetch(`${API_BASE}/api/applications/jobs/${jobId}/progress`, {
              credentials: 'include',
            });
            if (!r.ok) return;
            const data = await r.json();
            if (cancelled) return;

            // Replace lines with full list from server on each poll.
            setLines(data.lines ?? []);

            if (data.status === 'DONE') {
              clearInterval(intervalId!);
              setDone(true);
              setTimeout(() => onDoneRef.current(data.appId), 600);
            } else if (data.status === 'FAILED') {
              clearInterval(intervalId!);
              setError(data.error || 'Pipeline failed');
            }
          } catch {
            // Network hiccup — keep polling
          }
        }, 1500);
      } catch (e: any) {
        if (!cancelled) setError(e?.message || 'Failed to start pipeline');
      }
    }

    start();

    return () => {
      cancelled = true;
      if (intervalId !== null) clearInterval(intervalId);
    };
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
          {connecting && !error && (
            <div style={{ color: 'var(--muted)' }}>Starting pipeline...</div>
          )}
          {lines.map((line, i) => (
            <div key={i} style={{ color: lineColor(line) }}>{line}</div>
          ))}
          {error && (
            <div style={{ color: '#c00', marginTop: 8 }}>{error}</div>
          )}
          {!done && !error && !connecting && lines.length > 0 && (
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
