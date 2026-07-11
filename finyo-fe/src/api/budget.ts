import { apiRequest } from './client';

/** Payment cadence of a fixed cost (mirrors the backend enum). */
export type PaymentInterval = 'MONTHLY' | 'YEARLY';

export interface FixedCost {
  id: string;
  name: string;
  category: string | null;
  paymentInterval: PaymentInterval;
  /** Cost per payment interval, as entered */
  amount: number;
  amountPerYear: number;
  amountPerMonth: number;
}

export interface FixedCostList {
  items: FixedCost[];
  totalPerYear: number;
  totalPerMonth: number;
}

export interface FixedCostInput {
  name: string;
  category?: string;
  paymentInterval: PaymentInterval;
  amount: number;
}

export interface MonthlyBudget {
  netIncome: number;
  savings: number;
  investing: number;
  pillar3a: number;
  taxReserve: number;
  workCosts: number;
  /** Derived from the fixed-cost table — not editable here */
  fixedCostsPerMonth: number;
  /** netIncome minus all allocations and fixed costs; may be negative */
  available: number;
}

export interface MonthlyBudgetInput {
  netIncome: number;
  savings: number;
  investing: number;
  pillar3a: number;
  taxReserve: number;
  workCosts: number;
}

export const budgetApi = {
  getFixedCosts: (token: string) => apiRequest<FixedCostList>('/fixed-costs', {}, token),

  createFixedCost: (token: string, data: FixedCostInput) =>
    apiRequest<FixedCost>('/fixed-costs', { method: 'POST', body: JSON.stringify(data) }, token),

  updateFixedCost: (token: string, id: string, data: FixedCostInput) =>
    apiRequest<FixedCost>(
      `/fixed-costs/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token,
    ),

  deleteFixedCost: (token: string, id: string) =>
    apiRequest<void>(`/fixed-costs/${id}`, { method: 'DELETE' }, token),

  getMonthlyBudget: (token: string) => apiRequest<MonthlyBudget>('/monthly-budget', {}, token),

  updateMonthlyBudget: (token: string, data: MonthlyBudgetInput) =>
    apiRequest<MonthlyBudget>(
      '/monthly-budget',
      { method: 'PUT', body: JSON.stringify(data) },
      token,
    ),
};
