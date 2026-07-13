import type { MonthlyDataPoint } from '@/types';

/** The backend's LAST_6_MONTHS range spans seven month groups — keep the last six. */
const MAX_MONTHS = 6;

export interface TrendBar {
  /** Axis label, e.g. `07/2026`. */
  month: string;
  income: number;
  expenses: number;
}

export function toTrendBars(points: readonly MonthlyDataPoint[]): TrendBar[] {
  // The API returns the points in ascending order, so the tail is the most recent.
  return points.slice(-MAX_MONTHS).map((point) => ({
    month: `${String(point.month).padStart(2, '0')}/${point.year}`,
    income: Math.abs(Number(point.income)),
    expenses: Math.abs(Number(point.expenses)),
  }));
}

export function hasMovement(bars: readonly TrendBar[]): boolean {
  return bars.some((bar) => bar.income !== 0 || bar.expenses !== 0);
}
