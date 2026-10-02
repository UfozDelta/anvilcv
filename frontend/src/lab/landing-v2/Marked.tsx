import { segments, type Keyword } from '../../components/landing/storyData';

/**
 * Text with the job's keywords marked. The acid underlay scales in from the left on
 * mount (CSS), so re-keying by job re-draws the marks when the job changes.
 */
export function Marked({ text, keywords, missing }: { text: string; keywords: Keyword[]; missing?: Set<string> }) {
  return (
    <>
      {segments(text, keywords).map((s, i) =>
        s.kw ? (
          <mark key={i} className="lv-mark" data-miss={missing?.has(s.kw) || undefined} style={{ ['--d' as string]: `${(i % 6) * 40}ms` }}>
            {s.t}
          </mark>
        ) : (
          <span key={i}>{s.t}</span>
        ),
      )}
    </>
  );
}
