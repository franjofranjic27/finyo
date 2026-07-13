import type { MonthlyBudget } from '@/api/budget';
import type { SpendingSummary } from '@/types';

/**
 * Plan-vs-actual figures for one month. Single source of truth for the budget
 * month tab and the dashboard budget status card.
 */
export interface PlanVsActual {
  /** Planned net income from the monthly budget. */
  plannedIncome: number;
  /** Booked income of the month. */
  actualIncome: number;
  /** Amount left for variable spending after allocations and fixed costs. */
  plannedVariable: number;
  /** Booked expenses of the month minus the budgeted fixed costs. */
  actualVariable: number;
  /** Fixed costs per month, budgeted separately from the available amount. */
  fixedCosts: number;
}

export function derivePlanVsActual(
  budget: MonthlyBudget,
  summary: SpendingSummary,
): PlanVsActual {
  const actualExpenses = Number(summary.totalExpenses);

  return {
    plannedIncome: budget.netIncome,
    actualIncome: Number(summary.totalIncome),
    // A negative plan would invert the progress bars — clamp at zero.
    plannedVariable: Math.max(0, budget.available),
    // Fixed costs are budgeted separately, so only the variable share counts
    // against the plan's available amount.
    actualVariable: Math.max(0, actualExpenses - budget.fixedCostsPerMonth),
    fixedCosts: budget.fixedCostsPerMonth,
  };
}

/** A budget only counts as planned once a net income has been entered. */
export function hasBudgetPlan(budget: MonthlyBudget): boolean {
  return budget.netIncome > 0;
}
