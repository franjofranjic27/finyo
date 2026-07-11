import type {
  Pillar3CompareResponse,
  Pillar3Product,
  Pillar3ProductComparison,
} from '@/api/pillar3';

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
