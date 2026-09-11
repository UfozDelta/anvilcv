/** A labelled number. The label is not optional — that is the whole point. */
export function Stat({ label, value, unit, caption, tone }: {
  label: string;
  value: React.ReactNode;
  unit?: string;
  caption?: string;
  tone?: 'good' | 'alert';
}) {
  const cls = tone === 'alert' ? 'stat stat--alert' : tone === 'good' ? 'stat stat--good' : 'stat';
  return (
    <div className={cls}>
      <div className="stat__label">{label}</div>
      <div className="stat__value">
        {value}{unit && <small>{unit}</small>}
      </div>
      {caption && <div className="stat__caption">{caption}</div>}
    </div>
  );
}
