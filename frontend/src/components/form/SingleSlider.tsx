import { formStyles as styles } from './styles';

export function SingleSlider({ label, value, min, max, step = 1, onChange }: {
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
