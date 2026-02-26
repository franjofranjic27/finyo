import type { Account, CreateAccountRequest } from '@/types';
import { apiRequest } from './client';

export const accountsApi = {
  getAll: (token: string) => apiRequest<Account[]>('/accounts', {}, token),

  getById: (token: string, id: string) => apiRequest<Account>(`/accounts/${id}`, {}, token),

  create: (token: string, data: CreateAccountRequest) =>
    apiRequest<Account>('/accounts', { method: 'POST', body: JSON.stringify(data) }, token),

  update: (token: string, id: string, data: Partial<CreateAccountRequest>) =>
    apiRequest<Account>(`/accounts/${id}`, { method: 'PUT', body: JSON.stringify(data) }, token),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/accounts/${id}`, { method: 'DELETE' }, token),
};
