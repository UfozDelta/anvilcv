import { useCallback, useEffect, useRef, useState } from 'react';
import { API_BASE, api } from '../../lib/api';
import '../../styles/landing.css';
import '../../styles/ledger.css';

const UNDO_MS = 5000;

/** Shared row exit fade. */
export const EXIT = { duration: 0.2, ease: [0.23, 1, 0.32, 1] } as const;

/**
 * Optimistic delete with an Undo window. The row leaves the list at once; the DELETE is sent
 * only when the window closes (a new delete restarts it and they commit together). Pending
 * deletes are flushed on unmount and on page hide, so closing the tab never loses one. If the
 * DELETE fails the row comes back and `onError` says why.
 */
export function useUndoDelete<T extends { id: string }>(
  rows: T[],
  setRows: React.Dispatch<React.SetStateAction<T[]>>,
  path: (item: T) => string,
  onError: (item: T, e: unknown) => void,
  /** Called when the page comes back from the back/forward cache; reload the list here. */
  onRestore?: () => void,
) {
  const [removed, setRemoved] = useState<{ item: T; index: number }[]>([]);
  const pending = useRef(removed);
  pending.current = removed;
  const latest = useRef({ path, onError, onRestore });
  latest.current = { path, onError, onRestore };

  const commit = useCallback(() => {
    const batch = pending.current;
    if (batch.length === 0) return;
    pending.current = [];
    setRemoved([]);
    for (const r of batch) {
      api.del(latest.current.path(r.item)).catch((e) => {
        setRows((rs) => [...rs.slice(0, r.index), r.item, ...rs.slice(r.index)]);
        latest.current.onError(r.item, e);
      });
    }
  }, [setRows]);

  // Each new delete restarts the clock.
  useEffect(() => {
    if (removed.length === 0) return;
    const t = window.setTimeout(commit, UNDO_MS);
    return () => window.clearTimeout(t);
  }, [removed, commit]);

  useEffect(() => {
    // keepalive lets the request outlive the page.
    // The Undo bar is cleared too: a page restored from the back/forward cache must not offer
    // Undo for rows that are already deleted server-side.
    const onHide = () => {
      for (const r of pending.current) {
        void fetch(`${API_BASE}${latest.current.path(r.item)}`, { method: 'DELETE', credentials: 'include', keepalive: true });
      }
      pending.current = [];
      setRemoved([]);
    };
    window.addEventListener('pagehide', onHide);
    return () => { window.removeEventListener('pagehide', onHide); commit(); };
  }, [commit]);

  // A bfcache restore shows the pre-hide list, which still has rows that were deleted at pagehide.
  useEffect(() => {
    const onShow = (e: PageTransitionEvent) => { if (e.persisted) latest.current.onRestore?.(); };
    window.addEventListener('pageshow', onShow);
    return () => window.removeEventListener('pageshow', onShow);
  }, []);

  const remove = (id: string) => {
    const index = rows.findIndex((r) => r.id === id);
    if (index < 0) return;
    setRemoved((rs) => [...rs, { item: rows[index], index }]);
    setRows((rs) => rs.filter((r) => r.id !== id));
  };
  const undo = () => {
    const last = removed[removed.length - 1];
    if (!last) return;
    setRows((rs) => [...rs.slice(0, last.index), last.item, ...rs.slice(last.index)]);
    setRemoved((rs) => rs.slice(0, -1));
  };
  /** True while a delete is still inside its Undo window (a reload must not bring the row back). */
  const isPending = (id: string) => pending.current.some((r) => r.item.id === id);
  return { remove, undo, isPending, removed: removed[removed.length - 1]?.item ?? null, more: Math.max(removed.length - 1, 0) };
}

/**
 * Warns before the tab closes or reloads while there are unsaved edits. Only the browser-level
 * prompt: the app uses <BrowserRouter>, and in-app blocking (useBlocker) needs a data router.
 */
export function useUnsavedGuard(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
}

/**
 * Popup-menu behaviour shared by the row ⋯ menu and the account menu: focus moves to the first
 * item on open, arrows move between items, Escape returns focus to the trigger, an outside
 * press just closes.
 */
export function useMenu() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useCallback((refocus = false) => {
    setOpen(false);
    if (refocus) trigger.current?.focus();
  }, []);

  useEffect(() => {
    if (!open) return;
    ref.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    const away = (e: PointerEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') close(true); };
    document.addEventListener('pointerdown', away);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('pointerdown', away); document.removeEventListener('keydown', esc); };
  }, [open, close]);

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const items = [...(ref.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])];
    if (items.length === 0) return;
    e.preventDefault();
    const i = items.indexOf(document.activeElement as HTMLElement);
    const next = e.key === 'ArrowDown' ? (i + 1) % items.length : (i - 1 + items.length) % items.length;
    items[next].focus();
  };

  return { open, setOpen, close, ref, trigger, onKeyDown };
}

/** Page title, optional live count and actions — what every page opens on. */
export function PageTitle({ title, count, actions }: { title: string; count?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <header className="ap-head">
      <div>
        <h1 className="lp-display ap-title">{title}</h1>
        {count && <p className="ap-count">{count}</p>}
      </div>
      {actions && <div className="ap-actions">{actions}</div>}
    </header>
  );
}

/** ⋯ menu: opens from its trigger, closes on outside click, Escape or picking an item. */
export function RowMenu({ onDelete, onDuplicate, label }: { onDelete: () => void; onDuplicate?: () => void; label: string }) {
  const { open, setOpen, close, ref, trigger, onKeyDown } = useMenu();
  const pick = (fn: () => void) => (e: React.MouseEvent) => { e.preventDefault(); e.stopPropagation(); close(true); fn(); };
  return (
    <div className="ap-menu" ref={ref} role="cell" onKeyDown={onKeyDown}>
      <button
        ref={trigger}
        type="button"
        className="ap-menu__btn"
        aria-label={`Actions for ${label}`}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={(e) => { e.preventDefault(); e.stopPropagation(); setOpen((o) => !o); }}
      >⋯</button>
      {open && (
        <div className="ap-menu__pop" role="menu">
          {onDuplicate && <button type="button" role="menuitem" onClick={pick(onDuplicate)}>Duplicate</button>}
          <button type="button" role="menuitem" className="ap-menu__danger" onClick={pick(onDelete)}>Delete</button>
        </div>
      )}
    </div>
  );
}

/** The live region stays mounted so screen readers announce the bar appearing. */
export function UndoBar({ name, more = 0, onUndo }: { name: string | null; more?: number; onUndo: () => void }) {
  return (
    <div role="status">
      {name && (
        <div className="ap-undo">
          <span>Deleted <strong>{name}</strong>{more > 0 && ` +${more} more`}</span>
          <button type="button" onClick={onUndo}>UNDO</button>
        </div>
      )}
    </div>
  );
}

export function BulletBar({ n, max, label }: { n: number; max: number; label?: string }) {
  return (
    <span className="ap-bar" role="img" aria-label={label ?? `${n} bullets`}>
      <span className="ap-bar__fill" style={{ transform: `scaleX(${Math.min(n, max) / max})` }} />
    </span>
  );
}

/** First run / nothing-to-show block: an italic title, a sentence, optional paths or actions. */
export function EmptyState({ title, sub, children }: { title: string; sub: string; children?: React.ReactNode }) {
  return (
    <section className="ap-empty">
      <h2 className="lp-display ap-empty__title">{title}</h2>
      <p className="ap-empty__sub">{sub}</p>
      {children}
    </section>
  );
}

/** Time since an ISO timestamp: "35m ago", "yesterday", "3w ago". */
export function ago(iso: string | null | undefined): string {
  const t = iso ? new Date(iso).getTime() : NaN;
  if (Number.isNaN(t)) return '—';
  const min = Math.max(0, Math.round((Date.now() - t) / 60000));
  if (min < 60) return `${min}m ago`;
  if (min < 60 * 24) return `${Math.round(min / 60)}h ago`;
  const d = Math.round(min / (60 * 24));
  if (d === 1) return 'yesterday';
  if (d < 14) return `${d}d ago`;
  if (d < 60) return `${Math.round(d / 7)}w ago`;
  return `${Math.round(d / 30)}mo ago`;
}
