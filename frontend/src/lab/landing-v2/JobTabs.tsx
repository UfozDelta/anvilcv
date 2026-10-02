import { useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import type { Job } from '../../components/landing/storyData';

/**
 * A real tablist (roving tabindex, ←/→/Home/End). The active look is a second copy of
 * the list, clipped to the selected tab; moving the clip slides ink and text color
 * together, which separate color transitions never keep in sync.
 */
export function JobTabs({ jobs, idx, onSelect, panelId }: { jobs: Job[]; idx: number; onSelect: (i: number) => void; panelId: string }) {
  const listRef = useRef<HTMLDivElement>(null);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const [clip, setClip] = useState<string | null>(null);

  useLayoutEffect(() => {
    const measure = () => {
      const list = listRef.current;
      const tab = tabRefs.current[idx];
      if (!list || !tab) return;
      const w = list.offsetWidth;
      const left = tab.offsetLeft;
      setClip(`inset(0 ${((w - left - tab.offsetWidth) / w) * 100}% 0 ${(left / w) * 100}%)`);
    };
    measure();
    const ro = new ResizeObserver(measure);
    if (listRef.current) ro.observe(listRef.current);
    return () => ro.disconnect();
  }, [idx]);

  const onKey = (e: KeyboardEvent) => {
    const n = jobs.length;
    const next =
      e.key === 'ArrowRight' ? (idx + 1) % n :
      e.key === 'ArrowLeft' ? (idx - 1 + n) % n :
      e.key === 'Home' ? 0 :
      e.key === 'End' ? n - 1 : -1;
    if (next < 0) return;
    e.preventDefault();
    onSelect(next);
    tabRefs.current[next]?.focus();
  };

  const label = (j: Job) => (
    <>
      <span className="lv-tab__key">{j.tab}</span>
      <span className="lv-tab__co">{j.company}</span>
    </>
  );

  return (
    <div className="lv-tabs" ref={listRef}>
      <div role="tablist" aria-label="Sample job" className="lv-tabs__list" onKeyDown={onKey}>
        {jobs.map((j, i) => (
          <button
            key={j.id}
            ref={(el) => { tabRefs.current[i] = el; }}
            id={`lv-tab-${j.id}`}
            role="tab"
            type="button"
            aria-selected={i === idx}
            aria-controls={panelId}
            tabIndex={i === idx ? 0 : -1}
            className="lv-tab"
            onClick={() => onSelect(i)}
          >
            {label(j)}
          </button>
        ))}
      </div>
      {clip && (
        <div className="lv-tabs__active" style={{ clipPath: clip }} aria-hidden="true">
          {jobs.map((j) => <span key={j.id} className="lv-tab">{label(j)}</span>)}
        </div>
      )}
    </div>
  );
}
