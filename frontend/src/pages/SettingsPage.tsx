import { useEffect, useState } from 'react';
import { api, type GenerationConfig, type BoldDensity, type Tone, type ActionVerbStyle } from '../lib/api';
import { Section } from '../components/Section';

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

export function SettingsPage() {
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

  if (loading) return <div className="shell"><span className="spinner">LOADING</span></div>;

  return (
    <>
    <div className="shell" style={{ maxWidth: 720 }}>
      <h1 style={{ fontFamily: 'var(--mono)', fontSize: '1rem', marginBottom: 32 }}>
        05 — GENERATION SETTINGS
      </h1>

      {err && <div className="err" style={{ marginBottom: 16 }}>{err}</div>}

      {/* ── Word Filter ─────────────────────────────────── */}
      <Section num="01" title="Word Filter" />
      <div style={styles.section}>
        <label style={styles.toggleRow}>
          <input
            type="checkbox"
            checked={cfg.wordFilterEnabled}
            onChange={e => set('wordFilterEnabled', e.target.checked)}
          />
          <span style={{ marginLeft: 8 }}>
            {cfg.wordFilterEnabled ? 'Enabled — bullets outside ranges are dropped' : 'Disabled — all bullets pass through'}
          </span>
        </label>

        <div style={{ opacity: cfg.wordFilterEnabled ? 1 : 0.4, pointerEvents: cfg.wordFilterEnabled ? 'auto' : 'none' }}>
          <SliderRow
            label="1-line range"
            lowKey="singleLineLow" highKey="singleLineHigh"
            low={cfg.singleLineLow} high={cfg.singleLineHigh}
            min={1} max={50}
            onChange={(k, v) => set(k as keyof GenerationConfig, v as any)}
          />
          <SliderRow
            label="2-line range"
            lowKey="doubleLineLow" highKey="doubleLineHigh"
            low={cfg.doubleLineLow} high={cfg.doubleLineHigh}
            min={1} max={100}
            onChange={(k, v) => set(k as keyof GenerationConfig, v as any)}
          />
          <SliderRow
            label="Dead zone (rejected)"
            lowKey="deadZoneLow" highKey="deadZoneHigh"
            low={cfg.deadZoneLow} high={cfg.deadZoneHigh}
            min={1} max={100}
            onChange={(k, v) => set(k as keyof GenerationConfig, v as any)}
          />
          <SingleSlider
            label={`Min word floor — ${cfg.minWordFloor}w`}
            value={cfg.minWordFloor} min={1} max={50}
            onChange={v => set('minWordFloor', v)}
          />
        </div>
      </div>

      {/* ── Generation Tuning ───────────────────────────── */}
      <Section num="02" title="Generation Tuning" />
      <div style={styles.section}>
        <SingleSlider
          label={`Temperature — ${cfg.temperature.toFixed(2)}`}
          value={cfg.temperature} min={0} max={2} step={0.05}
          onChange={v => set('temperature', v)}
        />

        <div style={styles.row}>
          <span style={styles.label}>Bold density</span>
          <SegmentedControl<BoldDensity>
            value={cfg.boldDensity}
            options={[
              { value: 'NONE',  label: 'None' },
              { value: 'LIGHT', label: 'Light' },
              { value: 'HEAVY', label: 'Heavy' },
            ]}
            onChange={v => set('boldDensity', v)}
          />
        </div>

        <div style={styles.row}>
          <span style={styles.label}>Tone</span>
          <SegmentedControl<Tone>
            value={cfg.tone}
            options={[
              { value: 'CONSERVATIVE', label: 'Conservative' },
              { value: 'NEUTRAL',      label: 'Neutral' },
              { value: 'AGGRESSIVE',   label: 'Aggressive' },
            ]}
            onChange={v => set('tone', v)}
          />
        </div>

        <div style={styles.row}>
          <span style={styles.label}>Action verb style</span>
          <SegmentedControl<ActionVerbStyle>
            value={cfg.actionVerbStyle}
            options={[
              { value: 'TECHNICAL',   label: 'Technical' },
              { value: 'LEADERSHIP',  label: 'Leadership' },
              { value: 'IMPACT',      label: 'Impact' },
            ]}
            onChange={v => set('actionVerbStyle', v)}
          />
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginTop: 24 }}>
        <button className="btn" onClick={save} disabled={saving}>
          {saving ? 'SAVING…' : 'SAVE'}
        </button>
        {savedAt && (
          <span style={{ fontFamily: 'var(--mono)', fontSize: '0.72rem', color: 'var(--ink-3)' }}>
            Saved {savedAt.toLocaleTimeString()}
          </span>
        )}
      </div>
    </div>

    <div className="panel" style={{ marginTop: 32 }}>
      <Section num="03" title="TOOLS" />
      <div style={{ marginTop: 20 }}>
        <div style={{ marginBottom: 12 }}>
          <span style={{ fontFamily: 'var(--mono)', fontSize: '0.85rem', fontWeight: 600 }}>
            Project Context Extractor
          </span>
          <p style={{ fontFamily: 'var(--mono)', fontSize: '0.75rem', color: 'var(--ink-3)', marginTop: 6, lineHeight: 1.6 }}>
            A Claude instruction file. Point Claude at any codebase — it explores the repo and
            produces a filled context doc ready to paste into your ResuForge project fields.
          </p>
        </div>
        <a
          className="btn btn--ghost"
          href="/api/tools/content-extract"
          download="content_extract.md"
        >
          ↓ DOWNLOAD content_extract.md
        </a>
      </div>
    </div>
    </>
  );
}

// ── Sub-components ────────────────────────────────────────

function SliderRow({ label, lowKey, highKey, low, high, min, max, onChange }: {
  label: string;
  lowKey: string; highKey: string;
  low: number; high: number;
  min: number; max: number;
  onChange: (key: string, value: number) => void;
}) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ ...styles.label, marginBottom: 6 }}>
        {label} — <span style={{ fontFamily: 'var(--mono)' }}>{low}w – {high}w</span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={styles.sliderRow}>
          <span style={styles.sliderCap}>{min}</span>
          <input
            type="range" min={min} max={max} value={low}
            onChange={e => onChange(lowKey, Number(e.target.value))}
            style={styles.slider}
          />
          <span style={styles.sliderCap}>{max}</span>
          <span style={styles.sliderValue}>{low}w low</span>
        </div>
        <div style={styles.sliderRow}>
          <span style={styles.sliderCap}>{min}</span>
          <input
            type="range" min={min} max={max} value={high}
            onChange={e => onChange(highKey, Number(e.target.value))}
            style={styles.slider}
          />
          <span style={styles.sliderCap}>{max}</span>
          <span style={styles.sliderValue}>{high}w high</span>
        </div>
      </div>
    </div>
  );
}

function SingleSlider({ label, value, min, max, step = 1, onChange }: {
  label: string; value: number; min: number; max: number; step?: number;
  onChange: (v: number) => void;
}) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ ...styles.label, marginBottom: 6 }}>{label}</div>
      <div style={styles.sliderRow}>
        <span style={styles.sliderCap}>{min}</span>
        <input
          type="range" min={min} max={max} step={step} value={value}
          onChange={e => onChange(Number(e.target.value))}
          style={styles.slider}
        />
        <span style={styles.sliderCap}>{max}</span>
      </div>
    </div>
  );
}

function SegmentedControl<T extends string>({ value, options, onChange }: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
}) {
  return (
    <div style={{ display: 'flex', gap: 2 }}>
      {options.map(opt => (
        <button
          key={opt.value}
          onClick={() => onChange(opt.value)}
          style={{
            fontFamily: 'var(--mono)',
            fontSize: '0.72rem',
            padding: '4px 10px',
            border: '1px solid var(--ink-3)',
            background: value === opt.value ? 'var(--ink)' : 'transparent',
            color: value === opt.value ? 'var(--paper)' : 'var(--ink)',
            cursor: 'pointer',
            letterSpacing: '0.04em',
          }}
        >
          {opt.label.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

const styles = {
  section: {
    padding: '20px 0 8px',
    marginBottom: 8,
  },
  row: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    gap: 16,
    marginBottom: 20,
  },
  label: {
    fontFamily: 'var(--mono)',
    fontSize: '0.78rem',
    color: 'var(--ink-2)',
    minWidth: 140,
  },
  toggleRow: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    fontFamily: 'var(--mono)',
    fontSize: '0.78rem',
    marginBottom: 20,
    cursor: 'pointer',
  },
  sliderRow: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    gap: 8,
  },
  slider: {
    flex: 1,
    accentColor: 'var(--ink)',
  },
  sliderCap: {
    fontFamily: 'var(--mono)',
    fontSize: '0.68rem',
    color: 'var(--ink-3)',
    width: 24,
    textAlign: 'center' as const,
  },
  sliderValue: {
    fontFamily: 'var(--mono)',
    fontSize: '0.68rem',
    color: 'var(--ink-2)',
    width: 60,
  },
};
