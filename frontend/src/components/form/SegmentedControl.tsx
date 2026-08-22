export function SegmentedControl<T extends string>({ value, options, onChange }: {
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
