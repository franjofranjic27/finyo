import type { FixedCost, FixedCostList, MonthlyBudget } from '@/api/budget';

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

export function monthlyBudget(overrides: Partial<MonthlyBudget> = {}): MonthlyBudget {
  return {
    netIncome: 5268.9,
    savings: 800,
    investing: 600,
    pillar3a: 600,
    taxReserve: 800,
    workCosts: 230,
    fixedCostsPerMonth: 314.55,
    available: 1924.35,
    ...overrides,
  };
}
