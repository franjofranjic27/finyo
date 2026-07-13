/**
 * Calendar-month helpers on `YYYY-MM` strings. Pure string/number math —
 * no Date-to-ISO round trips, so there are no timezone pitfalls.
 */

export interface DateRange {
  /** First day of the month, ISO date (`YYYY-MM-01`). */
  from: string;
  /** Last day of the month, ISO date. */
  to: string;
}

function parts(ym: string): [number, number] {
  const [year, month] = ym.split('-').map(Number);
  return [year, month];
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** The current month as `YYYY-MM` in local time. */
export function currentMonth(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
}

/** First and last day of the given `YYYY-MM` month as ISO dates. */
export function monthRange(ym: string): DateRange {
  const [year, month] = parts(ym);
  // Day 0 of the next month is the last day of this month (leap-year aware).
  const lastDay = new Date(year, month, 0).getDate();
  return { from: `${ym}-01`, to: `${ym}-${pad(lastDay)}` };
}

/** Shifts a `YYYY-MM` month by `delta` months (negative to go back). */
export function shiftMonth(ym: string, delta: number): string {
  const [year, month] = parts(ym);
  const total = year * 12 + (month - 1) + delta;
  return `${Math.floor(total / 12)}-${pad((total % 12) + 1)}`;
}

/** Locale-formatted month label, e.g. `März 2026` for `2026-03` / `de-CH`. */
export function monthLabel(ym: string, locale: string): string {
  const [year, month] = parts(ym);
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(
    new Date(year, month - 1, 1),
  );
}
