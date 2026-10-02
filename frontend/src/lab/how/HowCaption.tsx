import { StoryStage } from '../landing-v2/StoryStage';
import { STEPS } from '../../components/landing/PinnedSteps';
import { useScrollStep } from './useScrollStep';
import '../landing-v2/landing-v2.css';
import '../../components/landing/steps.css';

/**
 * One place to look: the stage is pinned in the centre and the step's words are its
 * caption, directly under it. The page scrolls past invisible spacers that drive the
 * step; the spacers also carry the full copy for screen readers.
 */
export function HowCaption() {
  const { listRef, step } = useScrollStep(55);
  const s = STEPS[step - 1];

  return (
    <div className="hw-cap lx-compact">
      <div className="hw-cap__sticky">
        <Progress step={step} />
        <StoryStage step={step} />
        <div key={step} className="hw-caption" aria-hidden="true">
          <span className="hw-caption__num">{String(step).padStart(2, '0')}</span>
          <div>
            <h3 className="lp-display hw-caption__title">{s.title}</h3>
            <p className="hw-caption__body">{s.body}</p>
          </div>
        </div>
      </div>
      <ol ref={listRef} className="hw-cap__spacers">
        {STEPS.map((st, i) => (
          <li key={st.title} data-i={i}>
            <span className="hw-sr">{st.title}. {st.body}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

export function Progress({ step }: { step: number }) {
  return (
    <div className="hw-progress" aria-hidden="true">
      {STEPS.map((st, i) => (
        <span key={st.title} className="hw-progress__seg" data-on={step >= i + 1 || undefined} />
      ))}
    </div>
  );
}
