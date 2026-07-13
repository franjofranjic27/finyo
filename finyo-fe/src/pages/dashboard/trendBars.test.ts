import { describe, expect, it } from 'vitest';
import { hasMovement, toTrendBars } from './trendBars';
import type { MonthlyDataPoint } from '@/types';

/** The backend's LAST_6_MONTHS range spans seven month groups (Jan…Jul). */
function sevenPoints(): MonthlyDataPoint[] {
  return [1, 2, 3, 4, 5, 6, 7].map((month) => ({
    year: 2026,
    month,
    expenses: `-${month}00`,
    income: '8000',
    net: '0',
  }));
}

describe('toTrendBars', () => {
  it('keeps only the six most recent months of an ascending series', () => {
    const bars = toTrendBars(sevenPoints());

    expect(bars).toHaveLength(6);
    expect(bars.map((bar) => bar.month)).toEqual([
      '02/2026',
      '03/2026',
      '04/2026',
      '05/2026',
      '06/2026',
      '07/2026',
    ]);
  });

  it('keeps every month of a shorter series', () => {
    expect(toTrendBars(sevenPoints().slice(0, 3))).toHaveLength(3);
  });

  it('renders expenses as positive magnitudes', () => {
    const points: MonthlyDataPoint[] = [
      { year: 2026, month: 6, expenses: '-2400', income: '8000', net: '5600' },
    ];

    expect(toTrendBars(points)[0]).toEqual({ month: '06/2026', income: 8000, expenses: 2400 });
  });
});

describe('hasMovement', () => {
  it('is false when every month is zero', () => {
    expect(hasMovement(toTrendBars([{ year: 2026, month: 7, expenses: '0', income: '0', net: '0' }]))).toBe(
      false,
    );
  });

  it('is true as soon as one month has income or expenses', () => {
    expect(hasMovement(toTrendBars(sevenPoints()))).toBe(true);
  });
});
