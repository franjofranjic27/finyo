import { beforeEach, describe, expect, it, vi } from 'vitest';
import { categoriesApi } from './categories';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('categoriesApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getAll requests /categories with the token', () => {
    categoriesApi.getAll(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/categories', {}, TOKEN);
  });
});
