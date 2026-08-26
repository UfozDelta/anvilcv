import { useEffect, useState, useMemo, useRef } from 'react';
import { api, type ApplicationResponse, type Bullet, type Project } from '../lib/api';
import { groupRankedByProject } from '../lib/groupBullets';
import { parseRanking } from '../lib/ranking';

const TOP_N = 15;

export function useApplicationDetail(id: string | undefined) {
  const [app, setApp] = useState<ApplicationResponse | null>(null);
  const [bullets, setBullets] = useState<Record<string, Bullet>>({});
  const [projectById, setProjectById] = useState<Record<string, Project>>({});
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [rerenderStreaming, setRerenderStreaming] = useState(false);
  const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null);
  const blobUrlRef = useRef<string | null>(null);
  const [pdfVersion, setPdfVersion] = useState(0);
  const [expandedWhys, setExpandedWhys] = useState<Set<string>>(new Set());
  const [showTail, setShowTail] = useState(false);

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
      // Key must match groupRankedByProject's bucket key.
      setExpandedGroups(new Set(
        ranking.filter(r => sel.has(r.bulletId)).map(r => map[r.bulletId]?.projectId ?? '__other__'),
      ));
    }
  }
  useEffect(() => { load(); }, [id]);

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
    return parseRanking(app.bulletRanking).sort((a, b) => a.rank - b.rank);
  }, [app]);

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

  function toggleBullet(bid: string) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(bid)) next.delete(bid); else next.add(bid);
      return next;
    });
  }

  function toggleWhy(bid: string) {
    setExpandedWhys(prev => {
      const next = new Set(prev);
      if (next.has(bid)) next.delete(bid); else next.add(bid);
      return next;
    });
  }

  return {
    app, bullets, projectById, busy, rerenderStreaming, setRerenderStreaming,
    pdfBlobUrl, pdfVersion, setPdfVersion, expandedWhys, showTail, setShowTail,
    expandedGroups, selectedIds, ranking, bulletsReady, grouped,
    toggleGroup, setOutcome, toggleBullet, toggleWhy, load,
    TOP_N,
  };
}
