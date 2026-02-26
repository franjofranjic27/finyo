import type { Transaction, TransactionPage } from '@/types';
import { apiRequest } from './client';

export interface TransactionFilters {
  page?: number;
  size?: number;
  accountId?: string;
  categoryId?: string;
  from?: string;
  to?: string;
  sort?: string;
}

export interface CreateTransactionRequest {
  amount: string;
  currency: string;
  date: string;
  description?: string;
  categoryId?: string;
  accountId: string;
}

export const transactionsApi = {
  getPage: (token: string, filters: TransactionFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.page !== undefined) params.set('page', String(filters.page));
    if (filters.size !== undefined) params.set('size', String(filters.size));
    if (filters.accountId) params.set('accountId', filters.accountId);
    if (filters.categoryId) params.set('categoryId', filters.categoryId);
    if (filters.from) params.set('from', filters.from);
    if (filters.to) params.set('to', filters.to);
    if (filters.sort) params.set('sort', filters.sort);
    const qs = params.toString();
    return apiRequest<TransactionPage>(`/transactions${qs ? `?${qs}` : ''}`, {}, token);
  },

  getRecent: (token: string, limit = 5) =>
    apiRequest<Transaction[]>(`/transactions/recent?limit=${limit}`, {}, token),

  create: (token: string, data: CreateTransactionRequest) =>
    apiRequest<Transaction>('/transactions', { method: 'POST', body: JSON.stringify(data) }, token),

  update: (token: string, id: string, data: Partial<CreateTransactionRequest>) =>
    apiRequest<Transaction>(
      `/transactions/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token
    ),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/transactions/${id}`, { method: 'DELETE' }, token),
};
