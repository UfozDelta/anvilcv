import { useEffect, useState } from 'react';
import { api, type GenerationConfig } from '../lib/api';

const DEFAULTS: GenerationConfig = {
  wordFilterEnabled: true,
  singleLineLow: 22,
  singleLineHigh: 26,
  doubleLineLow: 42,
  doubleLineHigh: 50,
  deadZoneLow: 27,
  deadZoneHigh: 40,
  minWordFloor: 12,
  temperature: 1.0,
  boldDensity: 'LIGHT',
  tone: 'NEUTRAL',
  actionVerbStyle: 'TECHNICAL',
};

export function useGenerationConfig() {
  const [cfg, setCfg] = useState<GenerationConfig>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api.get<GenerationConfig>('/api/config/generation')
      .then(setCfg)
      .catch(e => setErr(e.message))
      .finally(() => setLoading(false));
  }, []);

  function set<K extends keyof GenerationConfig>(k: K, v: GenerationConfig[K]) {
    setCfg(prev => ({ ...prev, [k]: v }));
  }

  async function save() {
    setSaving(true); setErr(null);
    try {
      const saved = await api.put<GenerationConfig>('/api/config/generation', cfg);
      setCfg(saved);
      setSavedAt(new Date());
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSaving(false);
    }
  }

  return { cfg, set, loading, saving, savedAt, err, save };
}
