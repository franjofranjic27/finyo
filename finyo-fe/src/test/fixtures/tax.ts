import type {
  TaxResultResponse,
  TaxYearDetail,
  TaxYearInputs,
  TaxYearSummary,
} from '@/api/tax';

export function taxYearSummary(
  overrides: Partial<TaxYearSummary> & { year: number },
): TaxYearSummary {
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

export function taxYearInputs(overrides: Partial<TaxYearInputs> = {}): TaxYearInputs {
  return {
    cantonCode: 'SG',
    bfsNumber: null,
    civilStatus: 'SINGLE',
    churchAffiliation: 'NONE',
    numberOfChildren: 0,
    grossEmploymentIncome: 100_000,
    selfEmploymentIncome: null,
    investmentIncome: null,
    rentalIncome: null,
    deductionProfessionalExpenses: null,
    deductionInsurancePremiums: null,
    deductionCharitableDonations: null,
    deductionDebtInterest: null,
    pillar3aContribution: null,
    netWealth: null,
    ...overrides,
  };
}

export function taxResult(overrides: Partial<TaxResultResponse> = {}): TaxResultResponse {
  return {
    taxYear: 2025,
    cantonCode: 'SG',
    civilStatus: 'SINGLE',
    grossIncome: 100_000,
    totalDeductions: 10_000,
    taxableIncome: 90_000,
    federalTax: 2000,
    cantonalTax: 6000,
    communalTax: 4000,
    totalIncomeTax: 12_000,
    effectiveRatePercent: 12,
    marginalRatePercent: 22,
    grandTotal: 12_000,
    breakdown: [],
    ...overrides,
  };
}

export function taxYearDetail(
  overrides: Partial<TaxYearDetail> & { year: number },
): TaxYearDetail {
  return {
    status: 'OPEN',
    inputs: taxYearInputs(),
    calculation: null,
    payments: [],
    deadlines: [],
    expectedTax: null,
    paidTotal: 0,
    openAmount: null,
    filingDeadline: null,
    filedAt: null,
    assessedAt: null,
    assessedAmount: null,
    ...overrides,
  };
}
