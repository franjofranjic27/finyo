import type { Salary, SalaryResult } from '@/api/salary';

function salaryResult(): SalaryResult {
  return {
    grossMonthly: 5787,
    grossYearly: 69444,
    // Exact backend output for these inputs (percentage lines are rounded to
    // 5 centimes on the YEARLY gross, not 12x the monthly line).
    deductions: [
      { type: 'AHV', pct: 5.3, perMonth: 306.7, perYear: 3680.55 },
      { type: 'ALV', pct: 1.1, perMonth: 63.65, perYear: 763.9 },
      { type: 'NBU', pct: 0.455, perMonth: 26.35, perYear: 315.95 },
      { type: 'KTG', pct: 0.17, perMonth: 9.85, perYear: 118.05 },
      { type: 'PENSION', pct: null, perMonth: 85.35, perYear: 1024.2 },
      { type: 'OTHER', pct: null, perMonth: 11, perYear: 132 },
    ],
    totalDeductionsPct: 8.7,
    totalPerMonth: 502.9,
    totalPerYear: 6034.65,
    netMonthly: 5284.1,
    netYearly: 63409.35,
  };
}

export function salary(overrides: Partial<Salary> = {}): Salary {
  return {
    grossMonthly: 5787,
    thirteenthSalary: false,
    ahvPct: 5.3,
    alvPct: 1.1,
    nbuPct: 0.455,
    ktgPct: 0.17,
    pensionFixed: 85.35,
    otherFixed: 11,
    result: salaryResult(),
    ...overrides,
  };
}
