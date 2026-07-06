import { beforeEach, describe, expect, it, vi } from 'vitest';
import { analyticsApi } from './analytics';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('analyticsApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getSummary defaults to THIS_MONTH', () => {
    analyticsApi.getSummary(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/analytics/summary?range=THIS_MONTH', {}, TOKEN);
  });

  it('getSummary passes an explicit range', () => {
    analyticsApi.getSummary(TOKEN, 'LAST_30_DAYS');
    expect(apiRequestMock).toHaveBeenCalledWith('/analytics/summary?range=LAST_30_DAYS', {}, TOKEN);
  });

  it('getCategoryBreakdown uses the by-category endpoint', () => {
    analyticsApi.getCategoryBreakdown(TOKEN, 'LAST_MONTH');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/analytics/by-category?range=LAST_MONTH',
      {},
      TOKEN,
    );
  });

  it('getMonthlyTrend defaults to LAST_12_MONTHS', () => {
    analyticsApi.getMonthlyTrend(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/analytics/monthly?range=LAST_12_MONTHS',
      {},
      TOKEN,
    );
  });
});
