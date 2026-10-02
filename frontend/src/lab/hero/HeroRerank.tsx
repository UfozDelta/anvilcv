import { HeroCopy, HeroEyebrow } from '../../components/landing/HeroCopy';
import { RerankPanel } from '../../components/landing/RerankPanel';

export function HeroRerank() {
  return (
    <section className="lp-hero shell hx">
      <HeroEyebrow />
      <div className="lp-hero__grid hx-grid">
        <HeroCopy />
        <RerankPanel />
      </div>
    </section>
  );
}

