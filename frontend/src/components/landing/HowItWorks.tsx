import { PinnedSteps } from './PinnedSteps';
import { SpotStage } from './SpotStage';
import './spot.css';

/**
 * Steps scroll beside a pinned, text-light stage; one thing is readable at a time —
 * only the active step's copy is at full strength, and the stage dims everything
 * except the region that step is about ("dim to focus").
 */
export function HowItWorks() {
  return (
    <div className="hw-spot">
      <PinnedSteps Stage={SpotStage} />
    </div>
  );
}
