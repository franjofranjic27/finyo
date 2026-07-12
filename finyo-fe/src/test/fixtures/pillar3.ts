import type {
  Pillar3CompareResponse,
  Pillar3Product,
  Pillar3ProductComparison,
  Pillar3Scenario,
  Pillar3ScenarioInputs,
} from '@/api/pillar3';
import type { Pillar3Result } from '@/api/tax';

export function pillar3Product(
  overrides: Partial<Pillar3Product> & { id: string },
): Pillar3Product {
  return {
    provider: `Provider ${overrides.id}`,
    name: `Product ${overrides.id}`,
    isin: 'CH0000000001',
    valor: null,
    equityPct: 50,
    terPct: 0.5,
    expectedReturnPct: 3.5,
    netReturnPct: 3.0,
    active: true,
    sortOrder: 0,
    ...overrides,
  };
}

export function pillar3Comparison(
  overrides: Partial<Pillar3ProductComparison> & { productId: string },
): Pillar3ProductComparison {
  return {
    provider: `Provider ${overrides.productId}`,
    name: `Product ${overrides.productId}`,
    isin: 'CH0000000001',
    equityPct: 50,
    terPct: 0.5,
    avgReturnPct: 3.5,
    netReturnPct: 3.0,
    totalFees: 2_000,
    finalCapital: 200_000,
    ...overrides,
  };
}

export function pillar3ScenarioInputs(
  overrides: Partial<Pillar3ScenarioInputs> = {},
): Pillar3ScenarioInputs {
  return {
    currentBalance: 10_000,
    annualContribution: 7258,
    assumedAnnualReturnPercent: 3.0,
    yearsToRetirement: 30,
    grossEmploymentIncome: null,
    civilStatus: null,
    cantonCode: null,
    taxYear: 2026,
    productId: null,
    ...overrides,
  };
}

export function pillar3Result(overrides: Partial<Pillar3Result> = {}): Pillar3Result {
  return {
    annualContribution: 7258,
    maxContribution: 7258,
    isMaxContribution: true,
    remainingUntilMax: 0,
    annualTaxSaving: 0,
    taxSavingIfMaxContribution: 0,
    projectedBalanceAtRetirement: 350_000,
    totalContributionsOverPeriod: 217_740,
    totalReturnsOverPeriod: 132_260,
    estimatedPayoutTax: 18_000,
    netValueAfterPayoutTax: 332_000,
    equivalentTaxableSavings: 0,
    yearlyProjection: [
      { year: 2027, balance: 17_776, totalContributed: 7258, totalReturns: 518 },
      { year: 2028, balance: 25_785, totalContributed: 14_516, totalReturns: 1269 },
    ],
    ...overrides,
  };
}

export function pillar3Scenario(
  overrides: Partial<Pillar3Scenario> & { id: string },
): Pillar3Scenario {
  return {
    name: `Scenario ${overrides.id}`,
    isDefault: false,
    inputs: pillar3ScenarioInputs(),
    product: null,
    effectiveReturnPercent: 3.0,
    calculation: pillar3Result(),
    createdAt: '2026-07-01T10:00:00Z',
    ...overrides,
  };
}

export function pillar3CompareResponse(
  products: Pillar3ProductComparison[],
  overrides: Partial<Omit<Pillar3CompareResponse, 'products'>> = {},
): Pillar3CompareResponse {
  return {
    totalPaidIn: 145_160,
    maxAnnualContribution: 7258,
    products,
    ...overrides,
  };
}
