import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { WordingRow } from './WordingRow';
import type { Bullet, GenerationConfig } from '../../lib/api';

const cfg = {
  wordFilterEnabled: true, singleLineLow: 11, singleLineHigh: 13, doubleLineLow: 23, doubleLineHigh: 27,
  deadZoneLow: 14, deadZoneHigh: 22, minWordFloor: 9,
} as GenerationConfig;

const bullet = (status: Bullet['status'], category: string): Bullet => ({
  id: `b-${status}-${category}`, projectId: 'p', text: 'Built a ledger service for payouts.', tags: [],
  category, status, createdAt: '', updatedAt: '', storyId: 's1',
});

describe('WordingRow', () => {
  // Hard rule: editing must be available for every bullet, in every status and lens.
  for (const status of ['PENDING', 'APPROVED'] as const) {
    for (const lens of ['ai-ml', 'backend', 'data', 'general']) {
      it(`renders an Edit control for a ${status} ${lens} wording`, () => {
        const html = renderToStaticMarkup(
          <WordingRow b={bullet(status, lens)} cfg={cfg} onApprove={() => {}} onEdit={() => {}} onTrash={() => {}} />,
        );
        expect(html).toContain('>Edit</button>');
      });
    }
  }

  it('shows the approve state on an approved wording', () => {
    const html = renderToStaticMarkup(
      <WordingRow b={bullet('APPROVED', 'backend')} cfg={cfg} onApprove={() => {}} onEdit={() => {}} onTrash={() => {}} />,
    );
    expect(html).toContain('✓ Approved');
  });
});
