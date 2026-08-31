import { describe, it, expect } from 'vitest';
import { CHARS_PER_LINE, CHARS_PER_WORD, charCount, estimatedLines, fitOf, needsRefit } from './bulletLength';
import type { GenerationConfig } from './api';

// The shipped backend defaults (V24, calibrated at CHARS_PER_WORD 7.4 measured over
// the real corpus). In characters: 1 line 81-96, dead zone 104-163, 2 lines 170-200,
// floor 67. The 200 ceiling is under 2 x CHARS_PER_LINE, so a kept bullet never
// renders a third line.
const CFG: GenerationConfig = {
  wordFilterEnabled: true,
  singleLineLow: 11, singleLineHigh: 13,
  doubleLineLow: 23, doubleLineHigh: 27,
  deadZoneLow: 14, deadZoneHigh: 22,
  minWordFloor: 9,
  temperature: 1.0, boldDensity: 'LIGHT', tone: 'NEUTRAL', actionVerbStyle: 'TECHNICAL',
};

const of = (n: number) => 'x'.repeat(n);

describe('charCount', () => {
  it('counts an unpaired ** - it renders as literal asterisks', () => {
    expect(charCount('a **bc')).toBe('a **bc'.length);
  });

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

  // Derived from the config rather than hardcoded: the bands and CHARS_PER_WORD are one
  // calibration and have moved twice (V21, V24). Hardcoded edges fail on every
  // recalibration without indicating a real regression.
  it('treats the band edges as inside the band', () => {
    const c = (words: number) => Math.round(words * CHARS_PER_WORD);
    expect(fitOf(of(c(CFG.singleLineHigh)), CFG)).toBe('ONE_LINE');
    expect(fitOf(of(c(CFG.deadZoneLow)), CFG)).toBe('DEAD_ZONE');
    expect(fitOf(of(c(CFG.deadZoneHigh)), CFG)).toBe('DEAD_ZONE');
    expect(fitOf(of(c(CFG.doubleLineHigh)), CFG)).toBe('TWO_LINE');
  });

  it('never admits a bullet that would render a third line', () => {
    const ceiling = Math.round(CFG.doubleLineHigh * CHARS_PER_WORD);
    expect(estimatedLines(of(ceiling))).toBeLessThanOrEqual(2);
    expect(fitOf(of(2 * CHARS_PER_LINE + 1), CFG)).toBe('TOO_LONG');
  });
});

describe('needsRefit', () => {
  it('acts on the three bad fits only', () => {
    expect(['DEAD_ZONE', 'TOO_LONG', 'TOO_SHORT'].every(f => needsRefit(f as any))).toBe(true);
    expect(['ONE_LINE', 'TWO_LINE', 'OFF'].some(f => needsRefit(f as any))).toBe(false);
  });
});
