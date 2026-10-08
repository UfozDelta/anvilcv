/* Small row pieces shared by the ledger pages: bullet fit, spinner, delete. */
import { type GenerationConfig } from '../../lib/api';
import { charCount, estimatedLines, FIT_LABEL, fitHint, fitOf, needsRefit } from '../../lib/bulletLength';

export function Fit({ text, cfg }: { text: string; cfg: GenerationConfig }) {
  const fit = fitOf(text, cfg);
  if (fit === 'OFF') return null;
  const bad = needsRefit(fit);
  const lines = estimatedLines(text);
  return (
    <span className="ps-fit" data-bad={bad || undefined} title={bad ? fitHint(text, cfg) : `${lines} line${lines === 1 ? '' : 's'}`}>
      {bad && '⚠ '}{FIT_LABEL[fit]} · {charCount(text)}c
    </span>
  );
}

export function Spin() {
  return <span className="ps-spin" aria-label="Generating" />;
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
