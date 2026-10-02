import { Landing } from '../../pages/Landing';
import { PinnedSteps } from '../../components/landing/PinnedSteps';
import { StoryStage } from '../landing-v2/StoryStage';
import '../landing-v2/landing-v2.css';

/** v1 "How it works": the wordier v2 stage (full JD + bullets) beside the steps. */
function V1How() {
  return <PinnedSteps Stage={StoryStage} />;
}

/** Landing v1 prototype: the live page with the original "How it works" stage. */
export function LabLanding() {
  return <Landing How={V1How} />;
}
