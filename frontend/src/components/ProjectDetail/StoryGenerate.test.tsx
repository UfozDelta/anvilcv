import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StoryGenerate } from './StoryGenerate';
import type { useStoryBank } from '../../hooks/useStoryBank';

type SB = ReturnType<typeof useStoryBank>;

const sb = {
  stories: [{ id: 's1', title: 'Ledger service', evidence: [], lenses: ['backend'], createdAt: '' }],
  bullets: [],
  cap: 12,
  newStoriesJob: () => ({ submitUrl: '', submitBody: {} }),
  wordingsJob: () => ({ submitUrl: '', submitBody: {} }),
  pollUrl: () => '',
  load: async () => {},
} as unknown as SB;

describe('StoryGenerate', () => {
  it('renders the three numbered steps', () => {
    const html = renderToStaticMarkup(<StoryGenerate sb={sb} />);
    expect(html).toContain('<b>1</b> Story');
    expect(html).toContain('<b>2</b> Lens');
    expect(html).toContain('<b>3</b> Generate');
  });

  it('renders the four lens cards for a picked story', () => {
    const html = renderToStaticMarkup(<StoryGenerate sb={sb} />);
    expect(html.split('class="sf-chip"').length - 1).toBe(4);
    for (const name of ['AI/ML', 'Backend', 'Data Eng', 'General']) expect(html).toContain(name);
  });

  it('offers a dashed New stories row and a disabled Generate until a lens is picked', () => {
    const html = renderToStaticMarkup(<StoryGenerate sb={sb} />);
    expect(html).toContain('sf-opt sf-opt--new');
    expect(html).toContain('Pick a lens');
    expect(html).toMatch(/class="btn btn--acid sf-go"[^>]*disabled/);
  });
});
