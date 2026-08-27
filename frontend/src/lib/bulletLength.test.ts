import { describe, it, expect } from 'vitest';
import { charCount, estimatedLines, fitOf, needsRefit } from './bulletLength';
import type { GenerationConfig } from './api';

// The shipped backend defaults. In characters: 1 line 81-97, dead zone 103-167,
// 2 lines 173-200, floor 65.
const CFG: GenerationConfig = {
  wordFilterEnabled: true,
  singleLineLow: 15, singleLineHigh: 18,
  doubleLineLow: 32, doubleLineHigh: 37,
  deadZoneLow: 19, deadZoneHigh: 31,
  minWordFloor: 12,
  temperature: 1.0, boldDensity: 'LIGHT', tone: 'NEUTRAL', actionVerbStyle: 'TECHNICAL',
};

const of = (n: number) => 'x'.repeat(n);

describe('charCount', () => {
  it('ignores bold markers, which take no width on the page', () => {
    expect(charCount('a **bc** d')).toBe(charCount('a bc d'));
  });
  it('trims', () => {
    expect(charCount('  ab  ')).toBe(2);
  });
});

describe('estimatedLines', () => {
  it('charges a full line for a partial one', () => {
    expect(estimatedLines(of(106))).toBe(2);
  });
  it('never returns zero for an empty bullet', () => {
    expect(estimatedLines('')).toBe(1);
  });
});

describe('fitOf', () => {
  it.each([
    [90, 'ONE_LINE'],
    [120, 'DEAD_ZONE'],
    [180, 'TWO_LINE'],
    [250, 'TOO_LONG'],
    [40, 'TOO_SHORT'],
  ])('reads %ic as %s', (n, expected) => {
    expect(fitOf(of(n), CFG)).toBe(expected);
  });

  it('flags nothing when the user has the filter off', () => {
    expect(fitOf(of(250), { ...CFG, wordFilterEnabled: false })).toBe('OFF');
  });

  it('treats the band edges as inside the band', () => {
    expect(fitOf(of(97), CFG)).toBe('ONE_LINE');   // singleLineHigh
    expect(fitOf(of(103), CFG)).toBe('DEAD_ZONE'); // deadZoneLow
    expect(fitOf(of(167), CFG)).toBe('DEAD_ZONE'); // deadZoneHigh
    expect(fitOf(of(200), CFG)).toBe('TWO_LINE');  // doubleLineHigh
  });
});

describe('needsRefit', () => {
  it('acts on the three bad fits only', () => {
    expect(['DEAD_ZONE', 'TOO_LONG', 'TOO_SHORT'].every(f => needsRefit(f as any))).toBe(true);
    expect(['ONE_LINE', 'TWO_LINE', 'OFF'].some(f => needsRefit(f as any))).toBe(false);
  });
});
