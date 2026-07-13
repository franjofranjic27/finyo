import { beforeEach, describe, expect, it, vi } from 'vitest';
import { categoryRulesApi } from './categoryRules';
import type { CategoryRuleInput } from './categoryRules';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

const input: CategoryRuleInput = { keyword: 'MIGROS', categoryId: 'c1' };

describe('categoryRulesApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getAll requests /category-rules with the token', () => {
    categoryRulesApi.getAll(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/category-rules', {}, TOKEN);
  });

  it('create POSTs the serialised rule', () => {
    categoryRulesApi.create(TOKEN, input);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/category-rules',
      { method: 'POST', body: JSON.stringify(input) },
      TOKEN,
    );
  });

  it('update PUTs the rule to the id path', () => {
    categoryRulesApi.update(TOKEN, 'r1', input);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/category-rules/r1',
      { method: 'PUT', body: JSON.stringify(input) },
      TOKEN,
    );
  });

  it('delete issues DELETE on the id path', () => {
    categoryRulesApi.delete(TOKEN, 'r1');
    expect(apiRequestMock).toHaveBeenCalledWith('/category-rules/r1', { method: 'DELETE' }, TOKEN);
  });
});
