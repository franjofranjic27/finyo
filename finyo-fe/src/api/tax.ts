import { apiRequest } from './client';

export type TaxCivilStatus = 'SINGLE' | 'MARRIED' | 'SINGLE_PARENT';

export type TaxYearStatus = 'OPEN' | 'FILED' | 'ASSESSED' | 'PAID';

export type ChurchAffiliation = 'NONE' | 'PROTESTANT' | 'ROMAN_CATHOLIC';

export interface TaxCalculationRequest {
  taxYear: number;
  cantonCode: string;
  bfsNumber?: number | null;
  civilStatus: TaxCivilStatus;
  churchAffiliation?: ChurchAffiliation | null;
  numberOfChildren: number;
  grossEmploymentIncome: number;
  selfEmploymentIncome?: number | null;
  investmentIncome?: number | null;
  rentalIncome?: number | null;
  deductionProfessionalExpenses?: number | null;
  deductionInsurancePremiums?: number | null;
  deductionCharitableDonations?: number | null;
  deductionDebtInterest?: number | null;
  pillar3aContribution?: number | null;
  netWealth?: number | null;
}

export interface TaxBreakdownItem {
  label: string;
  amount: number;
}

export interface TaxResultResponse {
  taxYear: number;
  cantonCode: string;
  communeName?: string;
  civilStatus: TaxCivilStatus;
  grossIncome: number;
  totalDeductions: number;
  taxableIncome: number;
  federalTax: number;
  cantonalTax: number;
  communalTax: number;
  churchTax?: number;
  totalIncomeTax: number;
  effectiveRatePercent: number;
  marginalRatePercent: number;
  wealthTax?: number;
  grandTotal: number;
  pillar3aTaxSaving?: number;
  breakdown: TaxBreakdownItem[];
}

export interface TaxCommune {
  id: number;
  taxYear: number;
  cantonCode: string;
  communeName: string;
  bfsNumber: number;
  multiplier: number;
  churchMultiplierProtestant: number | null;
  churchMultiplierRomanCatholic: number | null;
  cantonMultiplier: number | null;
}

export interface TaxYearSummary {
  year: number;
  status: TaxYearStatus;
  expectedTax: number | null;
  paidTotal: number;
  openAmount: number | null;
  grossIncome: number | null;
  effectiveRatePercent: number | null;
}

export interface TaxYearInputs {
  cantonCode: string | null;
  bfsNumber: number | null;
  civilStatus: TaxCivilStatus | null;
  churchAffiliation: ChurchAffiliation | null;
  numberOfChildren: number | null;
  grossEmploymentIncome: number | null;
  selfEmploymentIncome: number | null;
  investmentIncome: number | null;
  rentalIncome: number | null;
  deductionProfessionalExpenses: number | null;
  deductionInsurancePremiums: number | null;
  deductionCharitableDonations: number | null;
  deductionDebtInterest: number | null;
  pillar3aContribution: number | null;
  netWealth: number | null;
}

export interface TaxPayment {
  id: string;
  paymentDate: string;
  amount: number;
  label: string;
}

export type TaxPaymentInput = Omit<TaxPayment, 'id'>;

export interface TaxDeadline {
  id: string;
  dueDate: string;
  label: string;
  amount: number | null;
  done: boolean;
}

export type TaxDeadlineInput = Omit<TaxDeadline, 'id'>;

export interface TaxYearDetail {
  year: number;
  status: TaxYearStatus;
  inputs: TaxYearInputs | null;
  calculation: TaxResultResponse | null;
  payments: TaxPayment[];
  deadlines: TaxDeadline[];
  expectedTax: number | null;
  paidTotal: number;
  openAmount: number | null;
  filingDeadline: string | null;
  filedAt: string | null;
  assessedAt: string | null;
  assessedAmount: number | null;
}

/** Flat request payload (backend convention: flat request, nested response). */
export type TaxScenarioRequest = TaxYearInputs & { name: string; isDefault?: boolean };

export interface TaxScenario {
  id: string;
  taxYear: number;
  name: string;
  isDefault: boolean;
  inputs: TaxYearInputs;
  createdAt: string;
  calculation: TaxResultResponse | null;
}

export interface Pillar3Request {
  currentBalance: number;
  annualContribution: number;
  assumedAnnualReturnPercent: number;
  yearsToRetirement: number;
  grossEmploymentIncome?: number | null;
  civilStatus?: TaxCivilStatus | null;
  cantonCode?: string | null;
  taxYear?: number | null;
}

export interface Pillar3YearlyProjection {
  year: number;
  balance: number;
  contribution: number;
  returns: number;
  taxSaving: number;
}

export interface Pillar3Result {
  annualContribution: number;
  maxContribution: number;
  isMaxContribution: boolean;
  remainingUntilMax: number;
  annualTaxSaving: number;
  taxSavingIfMaxContribution: number;
  projectedBalanceAtRetirement: number;
  totalContributionsOverPeriod: number;
  totalReturnsOverPeriod: number;
  estimatedPayoutTax: number;
  netValueAfterPayoutTax: number;
  equivalentTaxableSavings: number;
  yearlyProjection: Pillar3YearlyProjection[];
}

export const taxApi = {
  calculate: (token: string, data: TaxCalculationRequest) =>
    apiRequest<TaxResultResponse>(
      '/tax/calculate',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  getCommunes: (token: string, canton: string, year = 2025) =>
    apiRequest<TaxCommune[]>(`/tax/communes?canton=${canton}&year=${year}`, {}, token),

  calculatePillar3: (token: string, data: Pillar3Request) =>
    apiRequest<Pillar3Result>(
      '/tax/pillar3/calculate',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  getYears: (token: string) =>
    apiRequest<TaxYearSummary[]>('/tax/years', {}, token),

  getYear: (token: string, year: number) =>
    apiRequest<TaxYearDetail>(`/tax/years/${year}`, {}, token),

  upsertYear: (token: string, year: number, inputs: TaxYearInputs) =>
    apiRequest<TaxYearDetail>(
      `/tax/years/${year}`,
      { method: 'PUT', body: JSON.stringify(inputs) },
      token,
    ),

  updateYearStatus: (token: string, year: number, status: TaxYearStatus, effectiveDate?: string) =>
    apiRequest<TaxYearDetail>(
      `/tax/years/${year}/status`,
      { method: 'PATCH', body: JSON.stringify({ status, effectiveDate: effectiveDate ?? null }) },
      token,
    ),

  deleteYear: (token: string, year: number) =>
    apiRequest<void>(`/tax/years/${year}`, { method: 'DELETE' }, token),

  addPayment: (token: string, year: number, payment: TaxPaymentInput) =>
    apiRequest<TaxPayment>(
      `/tax/years/${year}/payments`,
      { method: 'POST', body: JSON.stringify(payment) },
      token,
    ),

  deletePayment: (token: string, year: number, paymentId: string) =>
    apiRequest<void>(`/tax/years/${year}/payments/${paymentId}`, { method: 'DELETE' }, token),

  addDeadline: (token: string, year: number, deadline: TaxDeadlineInput) =>
    apiRequest<TaxDeadline>(
      `/tax/years/${year}/deadlines`,
      { method: 'POST', body: JSON.stringify(deadline) },
      token,
    ),

  updateDeadline: (token: string, year: number, deadlineId: string, deadline: TaxDeadlineInput) =>
    apiRequest<TaxDeadline>(
      `/tax/years/${year}/deadlines/${deadlineId}`,
      { method: 'PUT', body: JSON.stringify(deadline) },
      token,
    ),

  deleteDeadline: (token: string, year: number, deadlineId: string) =>
    apiRequest<void>(`/tax/years/${year}/deadlines/${deadlineId}`, { method: 'DELETE' }, token),

  getScenarios: (token: string, year: number) =>
    apiRequest<TaxScenario[]>(`/tax/years/${year}/scenarios`, {}, token),

  createScenario: (token: string, year: number, scenario: TaxScenarioRequest) =>
    apiRequest<TaxScenario>(
      `/tax/years/${year}/scenarios`,
      { method: 'POST', body: JSON.stringify(scenario) },
      token,
    ),

  setDefaultScenario: (token: string, year: number, scenarioId: string) =>
    apiRequest<TaxScenario>(
      `/tax/years/${year}/scenarios/${scenarioId}/default`,
      { method: 'PATCH' },
      token,
    ),

  deleteScenario: (token: string, year: number, scenarioId: string) =>
    apiRequest<void>(`/tax/years/${year}/scenarios/${scenarioId}`, { method: 'DELETE' }, token),
};
