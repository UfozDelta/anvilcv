import { describe, it, expect } from 'vitest';

// The project pages' shared stylesheet, read as text. Vitest empties CSS imports, so this reads the
// file directly. The module name is kept out of a literal so TypeScript needs no Node typings here.
const nodeFs = 'node:fs';
const storyCss: string = (await import(/* @vite-ignore */ nodeFs)).readFileSync(
  new URL('./story.css', import.meta.url),
  'utf8',
);

describe('story.css phone and touch rules', () => {
  it('keeps every :hover rule inside (hover: hover)', () => {
    expect(storyCss.length).toBeGreaterThan(1000); // guards against an empty read passing vacuously
    const bare = storyCss.split('\n').filter(l => l.includes(':hover') && !l.includes('(hover: hover)'));
    expect(bare).toEqual([]);
  });

  it('makes the tab row full width with 44px targets under 560px', () => {
    expect(storyCss).toMatch(/@media \(max-width: 560px\) \{\s*\.sf-tabs \{ display: flex; \}\s*\.sf-tabs button \{ flex: 1 1 0; min-width: 0; min-height: 44px; \}/);
  });

  it('keeps page inputs at 16px on phones so iOS does not zoom', () => {
    expect(storyCss).toMatch(/@media \(max-width: 560px\) \{\s*\.sf-page \.field__input/);
  });

  it('stops transitions on these pages under reduced motion', () => {
    expect(storyCss).toMatch(/prefers-reduced-motion: reduce\) \{\s*\.sf-page \*, \.pl-page \* \{ transition: none !important; \}/);
  });

  it('turns off tap highlight and sets touch-action on page controls', () => {
    expect(storyCss).toMatch(/-webkit-tap-highlight-color: transparent; touch-action: manipulation;/);
  });
});
