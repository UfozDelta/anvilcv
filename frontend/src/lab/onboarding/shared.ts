import { usePrefersReducedMotion } from '../../components/landing/useHeroLoop';

export const EASE = [0.23, 1, 0.32, 1] as const;

/** Entrance props: rise and fade, staggered by index. Reduced motion keeps the fade only. */
export function useRise() {
  const reduce = usePrefersReducedMotion();
  return (i = 0) => ({
    initial: reduce ? { opacity: 0 } : { opacity: 0, y: 8 },
    animate: reduce ? { opacity: 1 } : { opacity: 1, y: 0 },
    transition: { duration: 0.28, delay: i * 0.06, ease: EASE },
  });
}
