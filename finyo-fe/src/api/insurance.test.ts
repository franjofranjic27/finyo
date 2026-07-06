import { beforeEach, describe, expect, it, vi } from 'vitest';
import { insuranceApi } from './insurance';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('insuranceApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getOverview requests the insurance overview', () => {
    insuranceApi.getOverview(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/insurance', {}, TOKEN);
  });

  it('updateStatus PATCHes the hasInsurance flag to the type path', () => {
    insuranceApi.updateStatus(TOKEN, 'type-1', true);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/insurance/type-1/status',
      { method: 'PATCH', body: JSON.stringify({ hasInsurance: true }) },
      TOKEN,
    );
  });
});
