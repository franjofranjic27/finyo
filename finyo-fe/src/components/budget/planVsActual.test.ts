import { describe, expect, it } from 'vitest';
import { derivePlanVsActual, hasBudgetPlan } from './planVsActual';
import { monthlyBudget } from '@/test/fixtures/budget';
import type { SpendingSummary } from '@/types';

function summary(overrides: Partial<SpendingSummary> = {}): SpendingSummary {
  return {
    totalIncome: '5268.90',
    totalExpenses: '3200.00',
    netAmount: '2068.90',
    rangeStart: '2026-07-01',
    rangeEnd: '2026-07-31',
    transactionCount: 12,
    ...overrides,
  };
}

describe('derivePlanVsActual', () => {
  it('compares booked figures with the plan and nets out the fixed costs', () => {
    const result = derivePlanVsActual(monthlyBudget(), summary());

    expect(result.plannedIncome).toBe(5268.9);
    expect(result.actualIncome).toBe(5268.9);
    expect(result.plannedVariable).toBe(1924.35);
    expect(result.actualVariable).toBeCloseTo(2885.45, 2);
    expect(result.fixedCosts).toBe(314.55);
  });

  it('clamps a negative plan and expenses below the fixed costs at zero', () => {
    const result = derivePlanVsActual(
      monthlyBudget({ available: -500 }),
      summary({ totalExpenses: '100.00' }),
    );

    expect(result.plannedVariable).toBe(0);
    expect(result.actualVariable).toBe(0);
  });
});

describe('hasBudgetPlan', () => {
  it('treats a budget without net income as unplanned', () => {
    expect(hasBudgetPlan(monthlyBudget({ netIncome: 0 }))).toBe(false);
    expect(hasBudgetPlan(monthlyBudget())).toBe(true);
  });
});
