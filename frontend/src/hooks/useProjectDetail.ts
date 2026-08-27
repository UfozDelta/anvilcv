import { useEffect, useState, useMemo } from 'react';
import { api, type Project, type Bullet, type RefitResponse, CATEGORIES } from '../lib/api';
import { parseExtract } from '../lib/parseExtract';
import { fitOf, needsRefit } from '../lib/bulletLength';
import { useGenerationConfig } from './useGenerationConfig';

export function useProjectDetail(id: string | undefined) {
  const [project, setProject] = useState<Project | null>(null);
  const [bullets, setBullets] = useState<Bullet[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [picked, setPicked] = useState<Set<string>>(new Set(['ai-ml', 'backend']));
  const [sortMode, setSortMode] = useState<'category' | 'date'>('category');
  const [filterCat, setFilterCat] = useState<string | null>(null);
  const [statusTab, setStatusTab] = useState<'bank' | 'approved'>('bank');
  const [enrichOpen, setEnrichOpen] = useState(false);
  const [enrichSaving, setEnrichSaving] = useState(false);
  const [enrichErr, setEnrichErr] = useState<string | null>(null);
  const [techStack, setTechStack] = useState('');
  const [yourRole, setYourRole] = useState('');
  const [ownership, setOwnership] = useState('');
  const [scaleImpact, setScaleImpact] = useState('');
  const [hardestProblem, setHardestProblem] = useState('');
  const [pasteOpen, setPasteOpen] = useState(false);
  const [pasteText, setPasteText] = useState('');
  const [pasteMsg, setPasteMsg] = useState<string | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [infoSaving, setInfoSaving] = useState(false);
  const [infoErr, setInfoErr] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editCompany, setEditCompany] = useState('');
  const [editLocation, setEditLocation] = useState('');
  const [editDates, setEditDates] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [refitting, setRefitting] = useState(false);
  const [refitMsg, setRefitMsg] = useState<string | null>(null);
  // The user's length bands, for the per-row fit badge and the REFIT button's count.
  const { cfg } = useGenerationConfig();

  async function load() {
    if (!id) return;
    setLoading(true);
    try {
      const [p, bs] = await Promise.all([
        api.get<Project>(`/api/projects/${id}`),
        api.get<Bullet[]>(`/api/projects/${id}/bullets`),
      ]);
      setProject(p); setBullets(bs);
      setTechStack(p.techStack || '');
      setYourRole(p.yourRole || '');
      setOwnership(p.ownership || '');
      setScaleImpact(p.scaleImpact || '');
      setHardestProblem(p.hardestProblem || '');
      setEditTitle(p.title || '');
      setEditCompany(p.company || '');
      setEditLocation(p.location || '');
      setEditDates(p.dates || '');
      setEditDescription(p.description || '');
    } finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [id]);

  async function saveInfo() {
    if (!id) return;
    setInfoErr(null); setInfoSaving(true);
    try {
      await api.put(`/api/projects/${id}`, { title: editTitle, company: editCompany, location: editLocation, dates: editDates, description: editDescription });
      await load();
      setInfoOpen(false);
    } catch (e: any) {
      setInfoErr(e?.message || 'Save failed');
    } finally {
      setInfoSaving(false);
    }
  }

  async function saveEnrich() {
    if (!id) return;
    setEnrichErr(null); setEnrichSaving(true);
    try {
      await api.put(`/api/projects/${id}`, { techStack, yourRole, ownership, scaleImpact, hardestProblem, description: editDescription });
      await load();
      setEnrichOpen(false);
    } catch (e: any) {
      setEnrichErr(e?.message || 'Save failed');
    } finally {
      setEnrichSaving(false);
    }
  }

  function parseAndFill() {
    setPasteMsg(null);
    const fields = parseExtract(pasteText);
    const keys = Object.keys(fields) as (keyof typeof fields)[];
    if (keys.length === 0) {
      setPasteMsg('No recognized sections found. Paste the full extractor output.');
      return;
    }
    if (fields.techStack !== undefined) setTechStack(fields.techStack);
    if (fields.yourRole !== undefined) setYourRole(fields.yourRole);
    if (fields.ownership !== undefined) setOwnership(fields.ownership);
    if (fields.scaleImpact !== undefined) setScaleImpact(fields.scaleImpact);
    if (fields.hardestProblem !== undefined) setHardestProblem(fields.hardestProblem);
    if (fields.description !== undefined) setEditDescription(fields.description);
    setPasteMsg(`Filled ${keys.length}/6 fields — review below, then SAVE CONTEXT.`);
    setPasteOpen(false);
    setPasteText('');
  }

  function generateBank() {
    if (!id || picked.size === 0) return;
    setErr(null);
    setGenerating(true);
  }

  async function addBullet(text: string, tags: string[], category: string) {
    await api.post<Bullet>(`/api/projects/${id}/bullets`, { text, tags, category });
    setAdding(false);
    await load();
  }

  async function saveBullet(b: Bullet, text: string, tags: string[]) {
    await api.put<Bullet>(`/api/bullets/${b.id}`, { text, tags });
    setEditing(null);
    await load();
  }
  /**
   * Bullets whose rendered length misses the configured bands. Counted over the whole bank,
   * not the visible tab, because the refit endpoint acts on the whole project.
   */
  const offBandIds = useMemo(
    () => new Set(bullets.filter(b => needsRefit(fitOf(b.text, cfg))).map(b => b.id)),
    [bullets, cfg]);

  async function refitBullets() {
    if (!id || offBandIds.size === 0) return;
    setRefitting(true); setRefitMsg(null); setErr(null);
    try {
      const r = await api.post<RefitResponse>(`/api/projects/${id}/bullets/refit`);
      setBullets(r.bullets);
      setRefitMsg(`${r.rewritten} REWRITTEN · ${r.unchanged} KEPT`);
    } catch (e: any) {
      setErr(e?.message || 'Refit failed');
    } finally {
      setRefitting(false);
    }
  }

  async function delBullet(b: Bullet) {
    if (!confirm(`Delete this bullet?`)) return;
    await api.del(`/api/bullets/${b.id}`);
    await load();
  }
  async function setBulletStatus(b: Bullet, status: Bullet['status']) {
    await api.patch<Bullet>(`/api/bullets/${b.id}/status`, { status });
    await load();
  }

  const categoryMap = useMemo(() => {
    const m = new Map<string, { label: string; blurb: string }>();
    for (const c of CATEGORIES) m.set(c.slug, { label: c.label, blurb: c.blurb });
    return m;
  }, []);

  const tabBullets = useMemo(() =>
    statusTab === 'approved'
      ? bullets.filter(b => b.status === 'APPROVED')
      : bullets.filter(b => b.status !== 'APPROVED'),
  [bullets, statusTab]);

  const grouped = useMemo(() => {
    const map = new Map<string, Bullet[]>();
    for (const b of tabBullets) {
      const k = b.category || 'general';
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(b);
    }
    const ordered: { slug: string; label: string; blurb: string; rows: Bullet[] }[] = [];
    for (const c of CATEGORIES) {
      if (map.has(c.slug)) {
        ordered.push({ slug: c.slug, label: c.label, blurb: c.blurb, rows: map.get(c.slug)! });
        map.delete(c.slug);
      }
    }
    for (const [slug, rows] of map.entries()) {
      ordered.push({ slug, label: slug.toUpperCase(), blurb: '', rows });
    }
    return ordered;
  }, [tabBullets]);

  const visibleGroups = useMemo(() =>
    filterCat ? grouped.filter(g => g.slug === filterCat) : grouped,
  [grouped, filterCat]);

  const flatByDate = useMemo(() => {
    const src = filterCat ? tabBullets.filter(b => (b.category || 'general') === filterCat) : tabBullets;
    return [...src].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [tabBullets, filterCat]);

  const presentCats = useMemo(() => grouped.map(g => g.slug), [grouped]);

  function togglePick(slug: string) {
    setPicked(prev => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug); else next.add(slug);
      return next;
    });
  }

  return {
    project, bullets, loading, generating, setGenerating, err,
    editing, setEditing, adding, setAdding, picked, togglePick,
    sortMode, setSortMode, filterCat, setFilterCat,
    statusTab, setStatusTab,
    enrichOpen, setEnrichOpen, enrichSaving, enrichErr, saveEnrich,
    techStack, setTechStack, yourRole, setYourRole, ownership, setOwnership,
    scaleImpact, setScaleImpact, hardestProblem, setHardestProblem,
    pasteOpen, setPasteOpen, pasteText, setPasteText, pasteMsg, parseAndFill,
    infoOpen, setInfoOpen, infoSaving, infoErr, saveInfo,
    editTitle, setEditTitle, editCompany, setEditCompany,
    editLocation, setEditLocation, editDates, setEditDates,
    editDescription, setEditDescription,
    load, generateBank, addBullet, saveBullet, delBullet, setBulletStatus,
    cfg, offBandIds, refitting, refitMsg, refitBullets,
    categoryMap, grouped, visibleGroups, flatByDate, presentCats,
  };
}
