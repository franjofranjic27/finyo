import type { TaxPayment, TaxYearStatus, TaxYearSummary } from '@/api/tax';

/**
 * UI-level status of a tax year. `planned` and `current` are derived on the
 * client only — the backend knows nothing but OPEN/FILED/ASSESSED/PAID.
 */
export type DisplayStatus = 'planned' | 'current' | TaxYearStatus;

export interface YearListEntry {
  readonly year: number;
  readonly displayStatus: DisplayStatus;
  readonly clickable: boolean;
  readonly summary: TaxYearSummary | null;
}

export interface MonthlyPaymentBucket {
  /** Zero-based month index (0 = January). */
  readonly month: number;
  readonly amount: number;
  readonly isPaid: boolean;
}

export interface ComparisonDatum {
  readonly year: number;
  readonly income: number;
  readonly taxBurden: number;
  readonly effectiveRate: number;
}

/** Badge colour classes per display status (FILED = emerald per design). */
export const STATUS_BADGE: Record<DisplayStatus, string> = {
  planned: 'bg-muted text-muted-foreground',
  current: 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400',
  OPEN: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  FILED: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400',
  ASSESSED: 'bg-violet-500/15 text-violet-600 dark:text-violet-400',
  PAID: 'bg-emerald-500 text-white',
};

export const STATUS_LABEL_KEY: Record<DisplayStatus, string> = {
  planned: 'tax.status.planned',
  current: 'tax.status.current',
  OPEN: 'tax.status.open',
  FILED: 'tax.status.filed',
  ASSESSED: 'tax.status.assessed',
  PAID: 'tax.status.paid',
};

/** Lifecycle order of the backend status state machine. */
const STATUS_ORDER: readonly TaxYearStatus[] = ['OPEN', 'FILED', 'ASSESSED', 'PAID'];

/**
 * Mirrors the backend TaxYearStatus rules: a year may advance any number of
 * steps forward but only be corrected exactly one step backward.
 */
export function allowedStatusTransitions(status: TaxYearStatus): TaxYearStatus[] {
  const index = STATUS_ORDER.indexOf(status);
  return STATUS_ORDER.filter((_, i) => i > index || i === index - 1);
}

export function deriveDisplayStatus(
  year: number,
  status: TaxYearStatus | null,
  currentYear: number,
): DisplayStatus {
  if (year > currentYear) return 'planned';
  if (year === currentYear) return 'current';
  return status ?? 'OPEN';
}

/**
 * Builds the sidebar year list: the planned next year (non-clickable) on top,
 * the current year (always present, even without a server record), then all
 * past years from the server in descending order.
 */
export function buildYearList(
  serverYears: readonly TaxYearSummary[],
  currentYear: number,
): YearListEntry[] {
  const currentSummary = serverYears.find((y) => y.year === currentYear) ?? null;
  const pastEntries = serverYears
    .filter((y) => y.year < currentYear)
    .sort((a, b) => b.year - a.year)
    .map((y) => ({
      year: y.year,
      displayStatus: deriveDisplayStatus(y.year, y.status, currentYear),
      clickable: true,
      summary: y,
    }));

  return [
    { year: currentYear + 1, displayStatus: 'planned', clickable: false, summary: null },
    { year: currentYear, displayStatus: 'current', clickable: true, summary: currentSummary },
    ...pastEntries,
  ];
}

/** Formats a Date as a local ISO date string (yyyy-mm-dd). */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Aggregates payments into 12 monthly buckets (by month of the payment date).
 * A bucket counts as paid when it has payments and none of them lies in the
 * future (paymentDate <= today, inclusive).
 */
export function aggregatePaymentsByMonth(
  payments: readonly TaxPayment[],
  today: Date,
): MonthlyPaymentBucket[] {
  const todayIso = toIsoDate(today);
  const amounts = new Array<number>(12).fill(0);
  const hasPayments = new Array<boolean>(12).fill(false);
  const hasFuturePayment = new Array<boolean>(12).fill(false);

  for (const payment of payments) {
    const month = Number(payment.paymentDate.slice(5, 7)) - 1;
    if (!Number.isInteger(month) || month < 0 || month > 11) continue;
    amounts[month] += payment.amount;
    hasPayments[month] = true;
    if (payment.paymentDate > todayIso) hasFuturePayment[month] = true;
  }

  return Array.from({ length: 12 }, (_, month) => ({
    month,
    amount: amounts[month],
    isPaid: hasPayments[month] && !hasFuturePayment[month],
  }));
}

/** Percentage of the expected tax already paid, clamped to 0–100. */
export function paymentProgress(paidTotal: number, expectedTax: number | null): number {
  if (expectedTax == null || expectedTax <= 0) return 0;
  return Math.min(100, Math.max(0, (paidTotal / expectedTax) * 100));
}

/** Chart data for the year comparison — only years with a calculation result. */
export function buildComparisonData(years: readonly TaxYearSummary[]): ComparisonDatum[] {
  return years
    .flatMap((y) =>
      y.grossIncome != null && y.expectedTax != null && y.effectiveRatePercent != null
        ? [
            {
              year: y.year,
              income: y.grossIncome,
              taxBurden: y.expectedTax,
              effectiveRate: y.effectiveRatePercent,
            },
          ]
        : [],
    )
    .sort((a, b) => a.year - b.year);
}
