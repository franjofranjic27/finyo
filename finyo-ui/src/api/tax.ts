import { apiRequest } from './client';

export type TaxCivilStatus = 'SINGLE' | 'MARRIED' | 'WIDOWED';

export interface TaxCalculationRequest {
  taxYear: number;
  cantonCode: string;
  bfsNumber?: number | null;
  civilStatus: TaxCivilStatus;
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
};
