import { apiRequest } from './client';

/** Deduction line of the salary calculation (mirrors the backend enum). */
export type SalaryDeductionType = 'AHV' | 'ALV' | 'NBU' | 'KTG' | 'PENSION' | 'OTHER';

export interface SalaryDeduction {
  type: SalaryDeductionType;
  /** null for fixed-amount deductions (PENSION, OTHER) */
  pct: number | null;
  perMonth: number;
  perYear: number;
}

export interface SalaryResult {
  grossMonthly: number;
  grossYearly: number;
  deductions: SalaryDeduction[];
  /** Total deductions as share of the gross monthly salary */
  totalDeductionsPct: number;
  totalPerMonth: number;
  totalPerYear: number;
  netMonthly: number;
  netYearly: number;
}

/**
 * Which gross field the user entered. With YEARLY the entered yearly gross is
 * the total annual gross INCLUDING the 13th salary (if enabled); the monthly
 * gross is derived by the backend (yearly / 13 or / 12).
 */
export type SalaryInputMode = 'MONTHLY' | 'YEARLY';

export interface Salary {
  grossMonthly: number;
  grossYearly: number;
  inputMode: SalaryInputMode;
  thirteenthSalary: boolean;
  ahvPct: number;
  alvPct: number;
  nbuPct: number;
  ktgPct: number;
  pensionFixed: number;
  otherFixed: number;
  result: SalaryResult;
}

export interface SalaryInput {
  grossMonthly: number;
  grossYearly: number;
  inputMode: SalaryInputMode;
  thirteenthSalary: boolean;
  ahvPct: number;
  alvPct: number;
  nbuPct: number;
  ktgPct: number;
  pensionFixed: number;
  otherFixed: number;
}

export const salaryApi = {
  get: (token: string) => apiRequest<Salary>('/salary', {}, token),

  update: (token: string, data: SalaryInput) =>
    apiRequest<Salary>('/salary', { method: 'PUT', body: JSON.stringify(data) }, token),
};
