import { formStyles as styles } from './styles';

export function SliderRow({ label, lowKey, highKey, low, high, min, max, onChange }: {
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
