import { Section } from '../components/Section';
import { SliderRow } from '../components/form/SliderRow';
import { SingleSlider } from '../components/form/SingleSlider';
import { SegmentedControl } from '../components/form/SegmentedControl';
import { formStyles as styles } from '../components/form/styles';
import { useGenerationConfig } from '../hooks/useGenerationConfig';
import type { BoldDensity, Tone, ActionVerbStyle, GenerationConfig } from '../lib/api';

export function SettingsPage() {
  const { cfg, set, loading, saving, savedAt, err, save } = useGenerationConfig();

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
            produces a filled context doc ready to paste into your AnvilCV project fields.
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
