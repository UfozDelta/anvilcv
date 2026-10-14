export const FIELD_OPTIONS = [
  ['software', 'Software'],
  ['mechanical', 'Mechanical (beta)'],
  ['other', 'Other / not sure'],
] as const;

/** Pick the discipline the résumé is written for. "Other" runs the software wording. */
export function FieldPicker({ value, onChange, disabled }: { value: string; onChange: (field: string) => void; disabled?: boolean }) {
  return (
    <div className="na-lenses" role="group" aria-label="Field">
      {FIELD_OPTIONS.map(([k, text]) => (
        <button key={k} type="button" disabled={disabled} aria-pressed={value === k} onClick={() => onChange(k)}>{text}</button>
      ))}
    </div>
  );
}
