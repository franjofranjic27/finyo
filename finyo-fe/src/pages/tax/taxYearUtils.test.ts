import { describe, expect, it } from 'vitest';
import type { TaxPayment, TaxYearSummary } from '@/api/tax';
import {
  aggregatePaymentsByMonth,
  allowedStatusTransitions,
  buildComparisonData,
  buildYearList,
  deriveDisplayStatus,
  paymentProgress,
  toIsoDate,
} from './taxYearUtils';

function summary(overrides: Partial<TaxYearSummary> & { year: number }): TaxYearSummary {
  return {
    status: 'OPEN',
    expectedTax: null,
    paidTotal: 0,
    openAmount: null,
    grossIncome: null,
    effectiveRatePercent: null,
    ...overrides,
  };
}

function payment(paymentDate: string, amount: number): TaxPayment {
  return { id: paymentDate + amount, paymentDate, amount, label: 'Test' };
}

describe('deriveDisplayStatus', () => {
  it('marks future years as planned', () => {
    expect(deriveDisplayStatus(2026, 'OPEN', 2025)).toBe('planned');
  });

  it('marks the current year as current regardless of the server status', () => {
    expect(deriveDisplayStatus(2025, 'FILED', 2025)).toBe('current');
  });

  it('passes the server status through for past years', () => {
    expect(deriveDisplayStatus(2024, 'ASSESSED', 2025)).toBe('ASSESSED');
  });

  it('falls back to OPEN for past years without a status', () => {
    expect(deriveDisplayStatus(2024, null, 2025)).toBe('OPEN');
  });
});

describe('buildYearList', () => {
  it('puts a non-clickable planned year on top and past years in descending order', () => {
    const years = [summary({ year: 2023, status: 'PAID' }), summary({ year: 2024, status: 'FILED' })];

    const list = buildYearList(years, 2025);

    expect(list.map((e) => e.year)).toEqual([2026, 2025, 2024, 2023]);
    expect(list[0]).toMatchObject({ displayStatus: 'planned', clickable: false });
    expect(list[1]).toMatchObject({ displayStatus: 'current', clickable: true });
    expect(list[2].displayStatus).toBe('FILED');
    expect(list[3].displayStatus).toBe('PAID');
  });

  it('always contains the current year even without a server record', () => {
    const list = buildYearList([], 2025);

    expect(list.map((e) => e.year)).toEqual([2026, 2025]);
    expect(list[1].summary).toBeNull();
  });

  it('attaches the server summary to the current year without duplicating it', () => {
    const current = summary({ year: 2025, status: 'OPEN', expectedTax: 12000 });

    const list = buildYearList([current], 2025);

    expect(list.filter((e) => e.year === 2025)).toHaveLength(1);
    expect(list[1].summary).toEqual(current);
    expect(list[1].displayStatus).toBe('current');
  });
});

describe('aggregatePaymentsByMonth', () => {
  const today = new Date(2025, 5, 15); // 2025-06-15

  it('returns 12 empty buckets for no payments', () => {
    const buckets = aggregatePaymentsByMonth([], today);

    expect(buckets).toHaveLength(12);
    expect(buckets.every((b) => b.amount === 0 && !b.isPaid)).toBe(true);
    expect(buckets.map((b) => b.month)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
  });

  it('sums multiple payments in the same month', () => {
    const buckets = aggregatePaymentsByMonth(
      [payment('2025-02-05', 1000), payment('2025-02-20', 500)],
      today,
    );

    expect(buckets[1]).toEqual({ month: 1, amount: 1500, isPaid: true });
  });

  it('treats a payment dated exactly today as paid (boundary)', () => {
    const buckets = aggregatePaymentsByMonth([payment('2025-06-15', 800)], today);

    expect(buckets[5]).toEqual({ month: 5, amount: 800, isPaid: true });
  });

  it('marks future payments as not paid', () => {
    const buckets = aggregatePaymentsByMonth([payment('2025-06-16', 800)], today);

    expect(buckets[5]).toEqual({ month: 5, amount: 800, isPaid: false });
  });

  it('marks a month with mixed past and future payments as not fully paid', () => {
    const buckets = aggregatePaymentsByMonth(
      [payment('2025-06-01', 400), payment('2025-06-30', 400)],
      today,
    );

    expect(buckets[5]).toEqual({ month: 5, amount: 800, isPaid: false });
  });
});

describe('toIsoDate', () => {
  it('formats local dates with zero padding', () => {
    expect(toIsoDate(new Date(2025, 0, 5))).toBe('2025-01-05');
  });
});

describe('paymentProgress', () => {
  it('returns the paid percentage', () => {
    expect(paymentProgress(5000, 10000)).toBe(50);
  });

  it('clamps to 100 for overpayments', () => {
    expect(paymentProgress(15000, 10000)).toBe(100);
  });

  it('clamps to 0 for negative paid totals', () => {
    expect(paymentProgress(-100, 10000)).toBe(0);
  });

  it('returns 0 when the expected tax is unknown', () => {
    expect(paymentProgress(5000, null)).toBe(0);
  });

  it('returns 0 when the expected tax is zero', () => {
    expect(paymentProgress(5000, 0)).toBe(0);
  });
});

describe('buildComparisonData', () => {
  it('only includes years with a full calculation result, sorted ascending', () => {
    const years = [
      summary({ year: 2025, grossIncome: 110000, expectedTax: 16000, effectiveRatePercent: 14.5 }),
      summary({ year: 2023, grossIncome: 100000, expectedTax: 15000, effectiveRatePercent: 15 }),
      summary({ year: 2024, grossIncome: null, expectedTax: null, effectiveRatePercent: null }),
    ];

    const data = buildComparisonData(years);

    expect(data).toEqual([
      { year: 2023, income: 100000, taxBurden: 15000, effectiveRate: 15 },
      { year: 2025, income: 110000, taxBurden: 16000, effectiveRate: 14.5 },
    ]);
  });

  it('returns an empty array when no year has results', () => {
    expect(buildComparisonData([summary({ year: 2024 })])).toEqual([]);
  });
});

describe('allowedStatusTransitions', () => {
  it('allows any forward step from OPEN but no backward step', () => {
    expect(allowedStatusTransitions('OPEN')).toEqual(['FILED', 'ASSESSED', 'PAID']);
  });

  it('allows exactly one step back plus all forward steps from FILED', () => {
    expect(allowedStatusTransitions('FILED')).toEqual(['OPEN', 'ASSESSED', 'PAID']);
  });

  it('does not allow jumping two steps back from ASSESSED', () => {
    expect(allowedStatusTransitions('ASSESSED')).toEqual(['FILED', 'PAID']);
  });

  it('only allows the single corrective step back from PAID', () => {
    expect(allowedStatusTransitions('PAID')).toEqual(['ASSESSED']);
  });
});
