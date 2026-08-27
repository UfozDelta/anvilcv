import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';

/**
 * Compiles an arbitrary set of bullets to a PDF and hands back an object URL.
 *
 * Shared by the application detail page (one project group at a time) and the project
 * page (whatever the filters currently show). Nothing is persisted server-side, so a
 * preview never disturbs a saved application.
 */
export function useBulletPreview() {
  const [url, setUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const urlRef = useRef<string | null>(null);

  function put(next: string | null) {
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    urlRef.current = next;
    setUrl(next);
  }

  /** Returns the object URL on success, null on failure — callers key their own UI off it. */
  async function preview(bulletIds: string[]): Promise<string | null> {
    if (bulletIds.length === 0) return null;
    setBusy(true);
    setErr(null);
    try {
      const res = await api.postRaw('/api/bullets/preview', { bulletIds });
      if (!res.ok) {
        setErr((await res.text()) || `${res.status} ${res.statusText}`);
        return null;
      }
      const next = URL.createObjectURL(await res.blob());
      put(next);
      return next;
    } catch (e: any) {
      setErr(e?.message ?? String(e));
      return null;
    } finally { setBusy(false); }
  }

  function close() {
    put(null);
    setErr(null);
  }

  useEffect(() => () => { if (urlRef.current) URL.revokeObjectURL(urlRef.current); }, []);

  return { url, busy, err, preview, close };
}
