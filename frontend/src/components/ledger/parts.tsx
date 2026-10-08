/* Small row pieces shared by the ledger pages: bullet fit, spinner, delete. */
import { type GenerationConfig } from '../../lib/api';
import { charCount, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';

/** Fit label, shown only when the bullet needs a refit. */
export function Fit({ text, cfg }: { text: string; cfg: GenerationConfig }) {
  const fit = fitOf(text, cfg);
  if (fit === 'OFF' || !needsRefit(fit)) return null;
  return <span className="ps-fit" data-bad title={fitHint(text, cfg)}>⚠ {FIT_LABEL[fit]} · {charCount(text)}c</span>;
}

/** Spinner; the label is for screen readers only. */
export function Spin({ label = 'Working' }: { label?: string }) {
  return <><span className="ps-spin" aria-hidden="true" /><span className="sr-only">{label}</span></>;
}

export function Trash({ onClick, label = 'Delete' }: { onClick: () => void; label?: string }) {
  return (
    <button type="button" className="minibtn ws-trash" title={label} aria-label={label} onClick={onClick}>
      <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
        <path d="M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.8 6.5v5M9.2 6.5v5" fill="none" stroke="currentColor" strokeWidth="1.5" />
      </svg>
    </button>
  );
}
