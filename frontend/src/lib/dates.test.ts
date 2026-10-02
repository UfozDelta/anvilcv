import { describe, expect, it } from 'vitest';
import { dropPresent, endKey, ensurePresent, looksCurrent, parseDates, tenure } from './dates';

const NOW = new Date(2026, 9, 15); // Oct 2026

describe('parseDates', () => {
  it('parses month-year to Present', () => {
    const s = parseDates('Jun 2024 – Present', NOW)!;
    expect(s.present).toBe(true);
    expect(tenure(s)).toBe('2y 5m');
  });
  it('handles abbreviations with periods and hyphens', () => {
    expect(tenure(parseDates('Sep. 2022 - May 2024', NOW))).toBe('1y 9m');
  });
  it('handles year-only ranges', () => {
    expect(tenure(parseDates('2019 – 2021', NOW))).toBe('3y');
  });
  it('handles full month names and "to"', () => {
    expect(tenure(parseDates('January 2020 to March 2020', NOW))).toBe('3m');
  });
  it('returns null for unparsable or reversed text', () => {
    expect(parseDates('Summer 2023', NOW)).toBeNull();
    expect(parseDates('whenever', NOW)).toBeNull();
    expect(parseDates('2021 – 2019', NOW)).toBeNull();
    expect(parseDates('', NOW)).toBeNull();
    expect(parseDates(null, NOW)).toBeNull();
    expect(tenure(null)).toBe('');
  });
});

describe('endKey', () => {
  it('puts present on top, then later ends first, unparsable by createdAt', () => {
    const present = endKey('Jun 2024 – Present', null, NOW);
    const late = endKey('Jan 2020 – Dec 2023', null, NOW);
    const early = endKey('2018 – 2019', null, NOW);
    const fallback = endKey('???', '2021-05-01T00:00:00Z', NOW);
    expect(present).toBe(Infinity);
    expect(late).toBeGreaterThan(fallback);
    expect(fallback).toBeGreaterThan(early);
  });
});

describe('currently-work-here helpers', () => {
  it('detects a Present-style ending', () => {
    expect(looksCurrent('Jun 2024 – Present')).toBe(true);
    expect(looksCurrent('Jan 2023 - current')).toBe(true);
    expect(looksCurrent('Jun 2023 – Aug 2023')).toBe(false);
    expect(looksCurrent(null)).toBe(false);
  });
  it('ticking the box turns a start into a Present range', () => {
    expect(ensurePresent('Jun 2024')).toBe('Jun 2024 – Present');
    expect(ensurePresent('Jun 2024 – Aug 2024')).toBe('Jun 2024 – Present');
    expect(ensurePresent('Jun 2024 – Present')).toBe('Jun 2024 – Present');
    expect(ensurePresent('')).toBe('');
  });
  it('un-ticking keeps the start', () => {
    expect(dropPresent('Jun 2024 – Present')).toBe('Jun 2024');
    expect(dropPresent('Jun 2023 – Aug 2023')).toBe('Jun 2023 – Aug 2023');
  });
});
