import { useEffect, useRef, useState } from 'react';

/**
 * 1-based index of the child of `listRef` crossing the reading line (`line` from the
 * top of the viewport, as a %). Starts on 1 so the stage is never blank.
 */
export function useScrollStep(line = 55) {
  const listRef = useRef<HTMLOListElement>(null);
  const [step, setStep] = useState(1);

  useEffect(() => {
    const items = Array.from(listRef.current?.children ?? []) as HTMLElement[];
    const io = new IntersectionObserver(
      (entries) => entries.forEach((e) => {
        if (e.isIntersecting) setStep(Number((e.target as HTMLElement).dataset.i) + 1);
      }),
      { rootMargin: `-${line}% 0px -${99 - line}% 0px` },
    );
    items.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [line]);

  return { listRef, step };
}
