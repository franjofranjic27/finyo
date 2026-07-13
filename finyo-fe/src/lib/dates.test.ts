import { describe, expect, it } from 'vitest';
import { currentMonth, monthLabel, monthRange, shiftMonth } from './dates';

describe('monthRange', () => {
  it('returns the first and last day of a 31-day month', () => {
    expect(monthRange('2026-12')).toEqual({ from: '2026-12-01', to: '2026-12-31' });
  });

  it('handles February in a non-leap year', () => {
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
  });

  it('handles February in a leap year', () => {
    expect(monthRange('2024-02')).toEqual({ from: '2024-02-01', to: '2024-02-29' });
  });

  it('handles a 30-day month', () => {
    expect(monthRange('2026-04')).toEqual({ from: '2026-04-01', to: '2026-04-30' });
  });
});

describe('shiftMonth', () => {
  it('moves forward within a year', () => {
    expect(shiftMonth('2026-07', 1)).toBe('2026-08');
  });

  it('wraps December into the next year', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
  });

  it('wraps January into the previous year', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
  });

  it('supports larger deltas', () => {
    expect(shiftMonth('2026-07', -19)).toBe('2024-12');
  });
});

describe('currentMonth', () => {
  it('formats the given date as YYYY-MM', () => {
    expect(currentMonth(new Date(2026, 6, 13))).toBe('2026-07');
    expect(currentMonth(new Date(2026, 0, 1))).toBe('2026-01');
  });
});

describe('monthLabel', () => {
  it('formats a German month label', () => {
    expect(monthLabel('2026-03', 'de-CH')).toBe('März 2026');
  });

  it('formats an English month label', () => {
    expect(monthLabel('2026-03', 'en-GB')).toBe('March 2026');
  });
});
