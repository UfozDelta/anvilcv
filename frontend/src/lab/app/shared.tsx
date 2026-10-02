import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageTitle } from '../../components/ledger/shared';
import { PROJECTS, type DemoProject } from './appData';

// The real pages share these; the lab keeps importing them from here.
export { EXIT, useMenu, PageTitle, RowMenu, UndoBar, BulletBar } from '../../components/ledger/shared';

const UNDO_MS = 5000;

/** The inert click handler for prototype links. */
export const inert = (e: React.MouseEvent) => e.preventDefault();

/**
 * Rows with optimistic delete: the row leaves at once and an Undo bar holds the item
 * for a few seconds. Deletes stack, so a second delete doesn't orphan the first.
 * No fake waits, no full-list reload. (apple-design: forgiveness)
 */
export function useUndoRows<T extends { id: string }>(initial: T[]) {
  const [rows, setRows] = useState<T[]>(initial);
  const [removed, setRemoved] = useState<{ item: T; index: number }[]>([]);

  // Each new delete restarts the clock.
  useEffect(() => {
    if (removed.length === 0) return;
    const t = window.setTimeout(() => setRemoved([]), UNDO_MS);
    return () => window.clearTimeout(t);
  }, [removed]);

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
  return { rows, setRows, remove, removed: removed[removed.length - 1]?.item ?? null, more: Math.max(removed.length - 1, 0), undo };
}

/** Projects: undoable rows plus duplicate. */
export function useProjectRows(empty: boolean) {
  const { rows, setRows, remove, removed, more, undo } = useUndoRows<DemoProject>(empty ? [] : PROJECTS);
  const duplicate = (id: string) => {
    const src = rows.find((r) => r.id === id);
    if (!src) return;
    const copy = { ...src, id: `${id}-copy-${Date.now()}`, name: `${src.name} (copy)`, editedMinAgo: 0 };
    setRows((rs) => [copy, ...rs]);
  };
  return { rows, remove, duplicate, removed, more, undo };
}

/**
 * Prototype-page harness: R replays the mount animation, E toggles the first-run (empty) state,
 * and `?empty=1` persists it. Skipped while typing or on a modified key.
 */
export function useDemoPage(withEmpty = true) {
  const [params, setParams] = useSearchParams();
  const empty = withEmpty && params.get('empty') === '1';
  const [mountKey, setMountKey] = useState(0);
  const replay = useCallback(() => setMountKey((k) => k + 1), []);
  const toggleEmpty = useCallback(() => {
    setParams(empty ? {} : { empty: '1' }, { replace: true });
    setMountKey((k) => k + 1);
  }, [empty, setParams]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === 'r' || e.key === 'R') replay();
      else if (withEmpty && (e.key === 'e' || e.key === 'E')) toggleEmpty();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [replay, toggleEmpty, withEmpty]);

  return { empty, mountKey, replay };
}

/** Projects page head. Empty: the two big paths below are the actions, so the head doesn't repeat them. */
export function PageHead({ rows, onImport, onNew }: { rows: DemoProject[]; onImport?: () => void; onNew?: () => void }) {
  const bullets = rows.reduce((n, r) => n + r.bullets, 0);
  const has = rows.length > 0;
  return (
    <PageTitle
      title="Projects"
      count={has && <><strong>{rows.length}</strong> project{rows.length === 1 ? '' : 's'} · <strong>{bullets}</strong> bullets in your bank</>}
      actions={has && (
        <>
          <button type="button" className="ap-btn ap-btn--ghost" onClick={onImport}>Import from GitHub</button>
          <button type="button" className="ap-btn ap-btn--acid" onClick={onNew}>+ New project</button>
        </>
      )}
    />
  );
}

/** First run is the one screen allowed delight: two clear ways in, nothing else. */
export function EmptyBank() {
  return (
    <section className="ap-empty">
      <h2 className="lp-display ap-empty__title">Your bank is empty.</h2>
      <p className="ap-empty__sub">Every tailored résumé is built from it. Start with one project.</p>
      <div className="ap-empty__paths">
        <button type="button" className="ap-path ap-path--primary">
          <span className="ap-path__num">01</span>
          <strong>Import a GitHub repo</strong>
          <span>We read the code and draft bullets for you.</span>
        </button>
        <button type="button" className="ap-path">
          <span className="ap-path__num">02</span>
          <strong>Write one by hand</strong>
          <span>Name, a sentence, your role. Bullets come after.</span>
        </button>
      </div>
    </section>
  );
}
