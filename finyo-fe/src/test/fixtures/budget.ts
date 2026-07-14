import type { BudgetPosition, FixedCost, FixedCostList, MonthlyBudget } from '@/api/budget';

export function fixedCost(overrides: Partial<FixedCost> & { id: string }): FixedCost {
  return {
    name: 'Krankenkasse',
    category: 'Versicherung',
    paymentInterval: 'MONTHLY',
    amount: 205,
    amountPerYear: 2460,
    amountPerMonth: 205,
    ...overrides,
  };
}

export function fixedCostList(overrides: Partial<FixedCostList> = {}): FixedCostList {
  return {
    items: [
      fixedCost({ id: 'fc1' }),
      fixedCost({
        id: 'fc2',
        name: 'ActiveFitness',
        category: 'Sport',
        paymentInterval: 'YEARLY',
        amount: 699,
        amountPerYear: 699,
        amountPerMonth: 58.25,
      }),
    ],
    totalPerYear: 3774.6,
    totalPerMonth: 314.55,
    ...overrides,
  };
}

export function budgetPosition(overrides: Partial<BudgetPosition> & { id: string }): BudgetPosition {
  return {
    name: 'Sparen',
    amount: 800,
    sortOrder: 0,
    ...overrides,
  };
}

export function monthlyBudget(overrides: Partial<MonthlyBudget> = {}): MonthlyBudget {
  return {
    netIncome: 5268.9,
    positions: [
      budgetPosition({ id: 'bp1', name: 'Sparen', amount: 800, sortOrder: 0 }),
      budgetPosition({ id: 'bp2', name: 'Investieren', amount: 600, sortOrder: 1 }),
      budgetPosition({ id: 'bp3', name: 'Säule 3a', amount: 600, sortOrder: 2 }),
      budgetPosition({ id: 'bp4', name: 'Steuerrückstellung', amount: 800, sortOrder: 3 }),
      budgetPosition({ id: 'bp5', name: 'Arbeitskosten', amount: 230, sortOrder: 4 }),
    ],
    fixedCostsPerMonth: 314.55,
    available: 1924.35,
    ...overrides,
  };
}
