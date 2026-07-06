import { beforeEach, describe, expect, it, vi } from 'vitest';
import { instrumentsApi } from './instruments';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('instrumentsApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getAll requests the instruments list', () => {
    instrumentsApi.getAll(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/investments/instruments', {}, TOKEN);
  });

  it('getMarketData requests market data for one instrument', () => {
    instrumentsApi.getMarketData(TOKEN, 'i1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/investments/instruments/i1/market-data',
      {},
      TOKEN,
    );
  });

  it('getAllMarketData requests the aggregated market data', () => {
    instrumentsApi.getAllMarketData(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/investments/market-data', {}, TOKEN);
  });

  it('create POSTs the new instrument', () => {
    instrumentsApi.create(TOKEN, { isin: 'CH0012221716', instrumentType: 'STOCK' });
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/investments/instruments',
      { method: 'POST', body: JSON.stringify({ isin: 'CH0012221716', instrumentType: 'STOCK' }) },
      TOKEN,
    );
  });

  it('update PUTs the changes to the id path', () => {
    instrumentsApi.update(TOKEN, 'i1', { name: 'Nestlé SA' });
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/investments/instruments/i1',
      { method: 'PUT', body: JSON.stringify({ name: 'Nestlé SA' }) },
      TOKEN,
    );
  });

  it('delete issues DELETE on the id path', () => {
    instrumentsApi.delete(TOKEN, 'i1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/investments/instruments/i1',
      { method: 'DELETE' },
      TOKEN,
    );
  });
});
