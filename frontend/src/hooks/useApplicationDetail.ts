import { useEffect, useState, useMemo, useRef } from 'react';
import { api, type ApplicationResponse, type Bullet, type Project, type SelectionWarning } from '../lib/api';
import { groupRankedByProject, insertSelected, moveWithinProject } from '../lib/groupBullets';
import { awkwardCount, estimatedLines } from '../lib/bulletLength';
import { useBulletPreview } from './useBulletPreview';
import { parseRanking } from '../lib/ranking';
import { useGenerationConfig } from './useGenerationConfig';

const TOP_N = 15;
/**
 * Rendered bullet lines that fit one page. Must equal `BulletSelector.MAX_TOTAL_LINES` on the
 * backend — this drifted to 29 against a backend 31, so the detail page's budget meter called
 * a selection over budget two lines before the selector did. There is no shared source for
 * this: if you change it there, change it here.
 */
const MAX_TOTAL_LINES = 31;

export function useApplicationDetail(id: string | undefined) {
  const [app, setApp] = useState<ApplicationResponse | null>(null);
  const [bullets, setBullets] = useState<Record<string, Bullet>>({});
  const [projectById, setProjectById] = useState<Record<string, Project>>({});
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [lockedIds, setLockedIds] = useState<Set<string>>(new Set());
  const [locksSaving, setLocksSaving] = useState(false);
  const [busy, setBusy] = useState(false);
  const [rerenderStreaming, setRerenderStreaming] = useState(false);
  // Re-running the recruiter pass. Its own flag rather than reusing rerenderStreaming: a
  // re-score compiles no PDF, so the viewer must not be told to reload one.
  const [rescoreStreaming, setRescoreStreaming] = useState(false);
  /** Which refit is running: `{ projectId: null }` for the whole page, a project id for one entry. */
  const [refitTarget, setRefitTarget] = useState<{ projectId: string | null } | null>(null);
  /** Bullet ids newly picked by the last refit, for the "NEW" badge — cleared on next load/refit. */
  const [justAddedIds, setJustAddedIds] = useState<Set<string>>(new Set());
  /** selectedIds snapshot taken when refit starts, diffed against the result in finishRefit. */
  const preRefitSelection = useRef<Set<string>>(new Set());
  const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null);
  const blobUrlRef = useRef<string | null>(null);
  const [pdfVersion, setPdfVersion] = useState(0);
  const [expandedWhys, setExpandedWhys] = useState<Set<string>>(new Set());
  const [showTail, setShowTail] = useState(false);
  // Which project group the open preview belongs to; the PDF itself lives in the shared hook.
  const [previewKey, setPreviewKey] = useState<string | null>(null);
  const preview = useBulletPreview();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingProjectId, setEditingProjectId] = useState<string | null>(null);
  /** Groups whose "more from bank" rows are open. */
  const [bankOpen, setBankOpen] = useState<Set<string>>(new Set());
  /** Bullets that repeat one earlier on the page (server check), keyed by the later bullet's id. */
  const [repeats, setRepeats] = useState<Record<string, SelectionWarning>>({});
  /** An on-page bullet or heading was edited since the last render, so the PDF shows old text.
   *  In-memory only: a reload forgets it. */
  const [textStale, setTextStale] = useState(false);
  const { cfg } = useGenerationConfig();

  async function load() {
    if (!id) return;
    const a = await api.get<ApplicationResponse>(`/api/applications/${id}`);
    setApp(a);
    const ranking = parseRanking(a.bulletRanking).sort((x, y) => x.rank - y.rank);
    // Respect saved selection if user already re-rendered; otherwise pre-select top N.
    const sel = a.selectedBulletIds.length > 0
      ? new Set(a.selectedBulletIds)
      : new Set(ranking.slice(0, TOP_N).map(r => r.bulletId));
    setSelectedIds(sel);
    setLockedIds(new Set(a.lockedBulletIds));

    // Pull all bullets referenced in the ranking so we can display text
    const ids = ranking.map(r => r.bulletId);
    if (ids.length > 0) {
      // No batch endpoint; pull all bullets per project. Easier: pull all projects then their bullets.
      const projects = await api.get<Project[]>(`/api/projects`);
      const projMap: Record<string, Project> = {};
      projects.forEach(p => { projMap[p.id] = p; });
      setProjectById(projMap);
      const all: Bullet[] = [];
      for (const p of projects) {
        const bs = await api.get<Bullet[]>(`/api/projects/${p.id}/bullets`);
        all.push(...bs);
      }
      const map: Record<string, Bullet> = {};
      all.forEach(b => { map[b.id] = b; });
      setBullets(map);
      // Open the groups that already contribute a selected bullet; collapse the rest.
      // Key must match groupRankedByProject's bucket key. Uses `sel` directly (not the
      // ranked-only list) so a selected bullet missing from bulletRanking still opens its group.
      setExpandedGroups(new Set(
        [...sel].map(bid => map[bid]?.projectId ?? '__other__'),
      ));
    }
    return a;
  }
  useEffect(() => { load(); }, [id]);

  // The recruiter pass runs after create returns the PDF. Poll the app (not load(), which
  // would reset the selection) until the score lands.
  useEffect(() => {
    if (!app?.recruiterPending || !id) return;
    const t = setTimeout(() => {
      api.get<ApplicationResponse>(`/api/applications/${id}`).then(setApp).catch(() => {});
    }, 4000);
    return () => clearTimeout(t);
  }, [app, id]);

  useEffect(() => {
    if (!app?.pdfAvailable || !id) return;
    let cancelled = false;
    api.fetchRaw(`/api/applications/${id}/pdf`)
      .then(res => res.blob())
      .then(blob => {
        if (cancelled) return;
        const url = URL.createObjectURL(blob);
        if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current);
        blobUrlRef.current = url;
        setPdfBlobUrl(url);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [app?.pdfAvailable, app?.id, pdfVersion]);

  const ranking = useMemo(() => {
    if (!app) return [];
    const parsed = parseRanking(app.bulletRanking).sort((a, b) => a.rank - b.rank);
    // Bullets can end up in `selectedBulletIds` (and so in the rendered PDF, which renders
    // straight off that list) without ever having been ranked — e.g. a stale selection
    // carried across a regenerate. Those bullets had no row here and so couldn't be seen,
    // counted, or deselected. Give them a synthetic trailing row instead of hiding them.
    const rankedIds = new Set(parsed.map(r => r.bulletId));
    let nextRank = parsed.length > 0 ? Math.max(...parsed.map(r => r.rank)) + 1 : 1;
    const orphans = [...selectedIds]
      .filter(bid => !rankedIds.has(bid) && bullets[bid])
      .map(bid => ({ bulletId: bid, rank: nextRank++, why: '' }));
    return [...parsed, ...orphans];
  }, [app, selectedIds, bullets]);

  /** Ids the LLM actually ranked — everything else on screen is a manual pick from the bank. */
  const rankedIds = useMemo(
    () => new Set(parseRanking(app?.bulletRanking).map(r => r.bulletId)),
    [app?.bulletRanking],
  );

  const bulletsReady = Object.keys(bullets).length > 0 && Object.keys(projectById).length > 0;

  // Group the rank-sorted ranking by owning project, split into Experience/Projects
  // sections to mirror the PDF. Logic lives in groupBullets.ts so it can be unit-tested.
  const grouped = useMemo(
    () => groupRankedByProject(ranking, bullets, projectById),
    [ranking, bullets, projectById],
  );

  function toggleGroup(key: string) {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  async function setOutcome(o: string) {
    if (!app) return;
    setBusy(true);
    try {
      const updated = await api.patch<ApplicationResponse>(`/api/applications/${app.id}`, { outcome: o });
      setApp(updated);
    } finally { setBusy(false); }
  }

  function toggleBank(key: string) {
    setBankOpen(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  const projectOf = (bid: string) => bullets[bid]?.projectId;

  function toggleBullet(bid: string) {
    setSelectedIds(prev => {
      if (!prev.has(bid)) return new Set(insertSelected([...prev], bid, projectOf));
      const next = new Set(prev);
      next.delete(bid);
      return next;
    });
  }

  /** Reorders within the entry; the Set's order is what rerender renders. */
  function moveBullet(bid: string, dir: -1 | 1) {
    setSelectedIds(prev => new Set(moveWithinProject([...prev], bid, dir, projectOf)));
  }

  // Hand-picks skip the selector's repeat checks, so ask the server (same Java rules) and warn.
  // Debounced: a burst of toggles costs one request. Failures just leave the last warnings up.
  useEffect(() => {
    if (!bulletsReady) return;
    let cancelled = false;
    const t = setTimeout(() => {
      api.post<SelectionWarning[]>('/api/applications/selection-check', { selectedBulletIds: [...selectedIds] })
        .then(ws => { if (!cancelled) setRepeats(Object.fromEntries(ws.map(w => [w.bulletId, w]))); })
        .catch(() => {});
    }, 300);
    return () => { cancelled = true; clearTimeout(t); };
  }, [selectedIds, bulletsReady]);

  /** Render only this group's included bullets, leaving the saved resume untouched. */
  async function previewGroup(key: string, bulletIds: string[]) {
    const url = await preview.preview(bulletIds);
    setPreviewKey(url ? key : null);
  }

  function closePreview() {
    preview.close();
    setPreviewKey(null);
  }

  // Rendered lines the current selection costs, against the one-page budget the
  // backend selects with (BulletSelector.MAX_TOTAL_LINES).
  const selectedLines = useMemo(
    () => [...selectedIds].reduce((n, bid) => n + estimatedLines(bullets[bid]?.text ?? ''), 0),
    [selectedIds, bullets],
  );

  // Selected bullets whose length wraps badly (half-filled second line, third line, too short).
  const selectedAwkward = useMemo(
    () => awkwardCount([...selectedIds].map(bid => bullets[bid]?.text ?? '').filter(Boolean), cfg),
    [selectedIds, bullets, cfg],
  );

  /** Saves straight to the bullet bank (same endpoint the Project page uses), so the
   * edit is visible on both /projects/:id and every application referencing this bullet. */
  async function saveBullet(b: Bullet, text: string, tags: string[]) {
    const updated = await api.put<Bullet>(`/api/bullets/${b.id}`, { text, tags });
    setBullets(prev => ({ ...prev, [b.id]: updated }));
    setEditingId(null);
    if (selectedIds.has(b.id) && app) {
      setTextStale(true);
      // The backend just flagged this page's scorecard stale; show it without resetting the selection.
      api.get<ApplicationResponse>(`/api/applications/${app.id}`).then(setApp).catch(() => {});
    }
  }

  /** Same endpoint the Project page uses. Sends the full current project merged with the
   * patch — the backend nulls out any field missing from the body, so a partial PUT would
   * wipe techStack/enrichment data that isn't shown in this header editor. */
  async function saveProject(project: Project, patch: Partial<Project>) {
    const updated = await api.put<Project>(`/api/projects/${project.id}`, { ...project, ...patch });
    setProjectById(prev => ({ ...prev, [project.id]: updated }));
    setEditingProjectId(null);
    if ([...selectedIds].some(bid => bullets[bid]?.projectId === project.id)) setTextStale(true);
  }

  /** Locks are saved immediately (not batched with the PDF selection), since REFIT SELECTION
   * reads them straight off the Application row and a stale save would silently un-pin a bullet. */
  async function toggleLock(bid: string) {
    if (!app) return;
    const next = new Set(lockedIds);
    if (next.has(bid)) next.delete(bid); else next.add(bid);
    setLockedIds(next);
    setLocksSaving(true);
    try {
      const updated = await api.patch<ApplicationResponse>(`/api/applications/${app.id}/locks`, {
        lockedBulletIds: [...next],
      });
      setApp(updated);
      setLockedIds(new Set(updated.lockedBulletIds));
    } finally {
      setLocksSaving(false);
    }
  }

  function toggleWhy(bid: string) {
    setExpandedWhys(prev => {
      const next = new Set(prev);
      if (next.has(bid)) next.delete(bid); else next.add(bid);
      return next;
    });
  }

  /** @param projectId re-pick only that entry; omit to re-pick the whole page. */
  function startRefit(projectId: string | null = null) {
    preRefitSelection.current = new Set(selectedIds);
    setRefitTarget({ projectId });
  }

  /** Reloads after a refit and flags bullets that weren't in the pre-refit selection. */
  async function finishRefit() {
    const a = await load();
    setPdfVersion(v => v + 1);
    setTextStale(false);
    if (a) {
      setJustAddedIds(new Set(a.selectedBulletIds.filter(bid => !preRefitSelection.current.has(bid))));
    }
    setRefitTarget(null);
  }

  return {
    app, bullets, projectById, busy, rerenderStreaming, setRerenderStreaming,
    rescoreStreaming, setRescoreStreaming,
    pdfBlobUrl, pdfVersion, setPdfVersion, expandedWhys, showTail, setShowTail,
    expandedGroups, selectedIds, ranking, bulletsReady, grouped,
    toggleGroup, setOutcome, toggleBullet, moveBullet, repeats, toggleWhy, load,
    editingId, setEditingId, saveBullet, cfg,
    editingProjectId, setEditingProjectId, saveProject,
    lockedIds, toggleLock, locksSaving, refitTarget, setRefitTarget,
    justAddedIds, startRefit, finishRefit,
    previewKey, previewUrl: preview.url, previewBusy: preview.busy, previewErr: preview.err,
    previewGroup, closePreview,
    selectedLines, selectedAwkward, MAX_TOTAL_LINES,
    rankedIds, bankOpen, toggleBank, textStale, setTextStale,
    TOP_N,
  };
}
