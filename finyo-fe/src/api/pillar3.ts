import { apiRequest } from './client';
import type { PriceHistory } from './positionDetail';
import type { Pillar3Result, TaxCivilStatus } from './tax';

export interface Pillar3Product {
  id: string;
  provider: string;
  name: string;
  isin: string;
  valor: string | null;
  equityPct: number;
  terPct: number;
  expectedReturnPct: number;
  netReturnPct: number;
  active: boolean;
  sortOrder: number;
}

export interface Pillar3ProductInput {
  provider: string;
  name: string;
  isin: string;
  valor?: string | null;
  equityPct: number;
  terPct: number;
  active?: boolean | null;
  sortOrder?: number | null;
}

export interface Pillar3CompareRequest {
  currentBalance: number;
  annualContribution: number;
  years: number;
  productIds: string[];
}

export interface Pillar3ProductComparison {
  productId: string;
  provider: string;
  name: string;
  isin: string;
  equityPct: number;
  terPct: number;
  avgReturnPct: number;
  netReturnPct: number;
  totalFees: number;
  finalCapital: number;
}

export interface Pillar3CompareResponse {
  totalPaidIn: number;
  maxAnnualContribution: number;
  /** Sorted by finalCapital descending — index 0 is the top product. */
  products: Pillar3ProductComparison[];
}

export interface Pillar3ImportRequest {
  products: Pillar3ProductInput[];
}

export interface Pillar3ImportResult {
  totalRows: number;
  created: number;
  updated: number;
  failed: number;
  errors: string[];
}

export interface Pillar3ScenarioInputs {
  currentBalance: number;
  annualContribution: number;
  assumedAnnualReturnPercent: number;
  yearsToRetirement: number;
  grossEmploymentIncome: number | null;
  civilStatus: TaxCivilStatus | null;
  cantonCode: string | null;
  taxYear: number | null;
  productId: string | null;
}

/** Flat request payload (backend convention: flat request, nested response). */
export type Pillar3ScenarioRequest = Pillar3ScenarioInputs & { name: string; isDefault?: boolean };

export interface Pillar3Scenario {
  id: string;
  name: string;
  isDefault: boolean;
  inputs: Pillar3ScenarioInputs;
  /** Null when the return was entered manually or the product was deleted. */
  product: Pillar3Product | null;
  /** The return rate actually used for the calculation. */
  effectiveReturnPercent: number;
  calculation: Pillar3Result;
  createdAt: string;
}

export const pillar3Api = {
  getProducts: (token: string, search?: string) =>
    apiRequest<Pillar3Product[]>(
      `/pillar3/products${search ? `?search=${encodeURIComponent(search)}` : ''}`,
      {},
      token,
    ),

  /**
   * Stored daily closes for the product's fund (3-year window, oldest first).
   * Empty points are normal: unlisted 3a funds have no market data, and a
   * listed fund fills in the background after the first view.
   */
  getProductPriceHistory: (token: string, productId: string) =>
    apiRequest<PriceHistory>(`/pillar3/products/${productId}/price-history`, {}, token),

  compare: (token: string, data: Pillar3CompareRequest) =>
    apiRequest<Pillar3CompareResponse>(
      '/pillar3/products/compare',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  getScenarios: (token: string) =>
    apiRequest<Pillar3Scenario[]>('/pillar3/scenarios', {}, token),

  createScenario: (token: string, scenario: Pillar3ScenarioRequest) =>
    apiRequest<Pillar3Scenario>(
      '/pillar3/scenarios',
      { method: 'POST', body: JSON.stringify(scenario) },
      token,
    ),

  updateScenario: (token: string, scenarioId: string, scenario: Pillar3ScenarioRequest) =>
    apiRequest<Pillar3Scenario>(
      `/pillar3/scenarios/${scenarioId}`,
      { method: 'PUT', body: JSON.stringify(scenario) },
      token,
    ),

  setDefaultScenario: (token: string, scenarioId: string) =>
    apiRequest<Pillar3Scenario>(
      `/pillar3/scenarios/${scenarioId}/default`,
      { method: 'PATCH' },
      token,
    ),

  deleteScenario: (token: string, scenarioId: string) =>
    apiRequest<void>(`/pillar3/scenarios/${scenarioId}`, { method: 'DELETE' }, token),

  adminList: (token: string) =>
    apiRequest<Pillar3Product[]>('/admin/pillar3/products', {}, token),

  createProduct: (token: string, product: Pillar3ProductInput) =>
    apiRequest<Pillar3Product>(
      '/admin/pillar3/products',
      { method: 'POST', body: JSON.stringify(product) },
      token,
    ),

  updateProduct: (token: string, id: string, product: Pillar3ProductInput) =>
    apiRequest<Pillar3Product>(
      `/admin/pillar3/products/${id}`,
      { method: 'PUT', body: JSON.stringify(product) },
      token,
    ),

  deleteProduct: (token: string, id: string) =>
    apiRequest<void>(`/admin/pillar3/products/${id}`, { method: 'DELETE' }, token),

  importProducts: (token: string, data: Pillar3ImportRequest) =>
    apiRequest<Pillar3ImportResult>(
      '/admin/pillar3/products/import',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),
};
