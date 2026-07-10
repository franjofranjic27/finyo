import { apiRequest } from './client';

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

export const pillar3Api = {
  getProducts: (token: string, search?: string) =>
    apiRequest<Pillar3Product[]>(
      `/pillar3/products${search ? `?search=${encodeURIComponent(search)}` : ''}`,
      {},
      token,
    ),

  compare: (token: string, data: Pillar3CompareRequest) =>
    apiRequest<Pillar3CompareResponse>(
      '/pillar3/products/compare',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

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
