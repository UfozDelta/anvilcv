import { useState, useRef, useCallback } from 'react';
import { flushSync } from 'react-dom';

export interface EventLogState {
  lines: string[];
  running: boolean;
  error: string | null;
}

/**
 * Hook that opens an SSE connection to a POST endpoint and collects log events.
 *
 * Why fetch+ReadableStream instead of EventSource: EventSource only supports GET.
 * Our SSE endpoints are POST (they need a request body). fetch lets us send a body
 * and still read the chunked SSE response line-by-line.
 *
 * Returns:
 *   stream(url, body) — starts streaming; returns a Promise that resolves with the
 *                       "done" event data when the pipeline finishes, or rejects on error.
 *   state             — { lines, running, error }
 *   reset             — clears log for reuse
 */
export function useEventLog() {
  const [lines, setLines] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const reset = useCallback(() => {
    setLines([]);
    setError(null);
    setRunning(false);
  }, []);

  const stream = useCallback(async (url: string, body: unknown): Promise<string> => {
    // Cancel any in-flight stream before starting a new one.
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    reset();
    setRunning(true);

    console.log('[SSE] fetch start', url);
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // credentials: 'include' sends session cookies, matching the main api client.
      credentials: 'include',
      body: JSON.stringify(body),
      signal: ctrl.signal,
    });
    console.log('[SSE] response', res.status, 'content-type:', res.headers.get('content-type'), 'body?', !!res.body);

    if (!res.ok || !res.body) {
      const text = await res.text();
      const msg = text || `HTTP ${res.status}`;
      setError(msg);
      setRunning(false);
      throw new Error(msg);
    }

    // Read the SSE stream line-by-line. Each SSE event is two lines:
    //   event: <name>\n
    //   data: <payload>\n
    // followed by a blank line.
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let currentEvent = 'log';

    return new Promise<string>((resolve, reject) => {
      (async () => {
        try {
          while (true) {
            const { done, value } = await reader.read();
            console.log('[SSE] chunk', { done, bytes: value?.byteLength });
            if (done) break;
            buf += decoder.decode(value, { stream: true });
            console.log('[SSE] buf after decode', JSON.stringify(buf.slice(0, 200)));

            // Process complete lines from the buffer.
            const rawLines = buf.split('\n');
            // Keep the last (possibly incomplete) line in the buffer.
            buf = rawLines.pop() ?? '';

            for (const raw of rawLines) {
              const line = raw.trimEnd();
              console.log('[SSE] line', JSON.stringify(line), 'currentEvent=', currentEvent);
              if (line.startsWith('event:')) {
                currentEvent = line.slice(6).trim();
              } else if (line.startsWith('data:')) {
                const data = line.slice(5).trim();
                if (currentEvent === 'log') {
                  console.log('[SSE] push log line', data);
                  flushSync(() => setLines(prev => [...prev, data]));
                } else if (currentEvent === 'done') {
                  setRunning(false);
                  resolve(data);
                  return;
                } else if (currentEvent === 'error') {
                  setError(data);
                  setRunning(false);
                  reject(new Error(data));
                  return;
                }
                // Reset to default event type after consuming data.
                currentEvent = 'log';
              }
            }
          }
          setRunning(false);
          reject(new Error('Stream ended without done event'));
        } catch (e: any) {
          if (e?.name !== 'AbortError') {
            setError(e?.message || 'Stream error');
            setRunning(false);
            reject(e);
          }
        }
      })();
    });
  }, [reset]);

  return { stream, state: { lines, running, error }, reset };
}
