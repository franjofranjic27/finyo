import { beforeEach, describe, expect, it, vi } from 'vitest';
import { portfolioApi } from './portfolio';
import type { CreatePositionRequest } from './portfolio';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

const newPosition: CreatePositionRequest = {
  name: 'Nestlé SA',
  isin: 'CH0038863350',
  quantity: 10,
  purchasePrice: 95.5,
};

describe('portfolioApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getPortfolio requests /portfolio with the token', () => {
    portfolioApi.getPortfolio(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/portfolio', {}, TOKEN);
  });

  it('getHistory requests /portfolio/history with the months parameter', () => {
    portfolioApi.getHistory(TOKEN, 36);
    expect(apiRequestMock).toHaveBeenCalledWith('/portfolio/history?months=36', {}, TOKEN);
  });

  it('createPosition POSTs the serialised position', () => {
    portfolioApi.createPosition(TOKEN, newPosition);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/positions',
      { method: 'POST', body: JSON.stringify(newPosition) },
      TOKEN,
    );
  });

  it('createPositionsBulk POSTs the positions wrapped in an object', () => {
    portfolioApi.createPositionsBulk(TOKEN, [newPosition]);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/positions/bulk',
      { method: 'POST', body: JSON.stringify({ positions: [newPosition] }) },
      TOKEN,
    );
  });

  it('deletePosition issues DELETE on the id path', () => {
    portfolioApi.deletePosition(TOKEN, 'p1');
    expect(apiRequestMock).toHaveBeenCalledWith('/positions/p1', { method: 'DELETE' }, TOKEN);
  });
});
