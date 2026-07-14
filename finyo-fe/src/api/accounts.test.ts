import { beforeEach, describe, expect, it, vi } from 'vitest';
import { accountsApi } from './accounts';
import { apiRequest } from './client';
import type { CreateAccountRequest } from '@/types';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

const newAccount: CreateAccountRequest = {
  name: 'UBS Checking',
  type: 'CHECKING',
  currency: 'CHF',
  initialBalance: '100.00',
};

describe('accountsApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getAll requests /accounts with the token', () => {
    accountsApi.getAll(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/accounts', {}, TOKEN);
  });

  it('getById requests the account by id', () => {
    accountsApi.getById(TOKEN, 'a1');
    expect(apiRequestMock).toHaveBeenCalledWith('/accounts/a1', {}, TOKEN);
  });

  it('create POSTs the serialised account', () => {
    accountsApi.create(TOKEN, newAccount);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/accounts',
      { method: 'POST', body: JSON.stringify(newAccount) },
      TOKEN,
    );
  });

  it('importAccounts POSTs the items wrapped in a bulk payload', () => {
    accountsApi.importAccounts(TOKEN, [newAccount]);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/accounts/bulk',
      { method: 'POST', body: JSON.stringify({ items: [newAccount] }) },
      TOKEN,
    );
  });

  it('update PUTs the partial account to the id path', () => {
    accountsApi.update(TOKEN, 'a1', { name: 'Renamed' });
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/accounts/a1',
      { method: 'PUT', body: JSON.stringify({ name: 'Renamed' }) },
      TOKEN,
    );
  });

  it('delete issues DELETE on the id path', () => {
    accountsApi.delete(TOKEN, 'a1');
    expect(apiRequestMock).toHaveBeenCalledWith('/accounts/a1', { method: 'DELETE' }, TOKEN);
  });
});
