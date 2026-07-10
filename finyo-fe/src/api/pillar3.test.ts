import { beforeEach, describe, expect, it, vi } from 'vitest';
import { pillar3Api } from './pillar3';
import type {
  Pillar3CompareRequest,
  Pillar3ImportRequest,
  Pillar3ProductInput,
} from './pillar3';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

const productInput: Pillar3ProductInput = {
  provider: 'frankly',
  name: 'Extreme 95',
  isin: 'CH0473243999',
  valor: '47324399',
  equityPct: 95,
  terPct: 0.44,
  active: true,
  sortOrder: 1,
};

describe('pillar3Api', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getProducts requests the catalog without a query by default', () => {
    pillar3Api.getProducts(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/pillar3/products', {}, TOKEN);
  });

  it('getProducts URL-encodes the search term', () => {
    pillar3Api.getProducts(TOKEN, 'frankly 95%');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/pillar3/products?search=frankly%2095%25',
      {},
      TOKEN,
    );
  });

  it('compare POSTs the comparison request', () => {
    const request: Pillar3CompareRequest = {
      currentBalance: 10_000,
      annualContribution: 7258,
      years: 20,
      productIds: ['p1', 'p2'],
    };
    pillar3Api.compare(TOKEN, request);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/pillar3/products/compare',
      { method: 'POST', body: JSON.stringify(request) },
      TOKEN,
    );
  });

  it('adminList requests the admin product list', () => {
    pillar3Api.adminList(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/admin/pillar3/products', {}, TOKEN);
  });

  it('createProduct POSTs the product input', () => {
    pillar3Api.createProduct(TOKEN, productInput);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/admin/pillar3/products',
      { method: 'POST', body: JSON.stringify(productInput) },
      TOKEN,
    );
  });

  it('updateProduct PUTs the product input to its id path', () => {
    pillar3Api.updateProduct(TOKEN, 'p1', productInput);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/admin/pillar3/products/p1',
      { method: 'PUT', body: JSON.stringify(productInput) },
      TOKEN,
    );
  });

  it('deleteProduct issues DELETE on the product path', () => {
    pillar3Api.deleteProduct(TOKEN, 'p1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/admin/pillar3/products/p1',
      { method: 'DELETE' },
      TOKEN,
    );
  });

  it('importProducts POSTs the import payload', () => {
    const request: Pillar3ImportRequest = { products: [productInput] };
    pillar3Api.importProducts(TOKEN, request);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/admin/pillar3/products/import',
      { method: 'POST', body: JSON.stringify(request) },
      TOKEN,
    );
  });
});
