import type { Budget, BudgetStatus } from '@/types';
import { apiRequest } from './client';

export interface CreateBudgetRequest {
  categoryId: string;
  amount: string;
  period: 'MONTHLY' | 'WEEKLY';
  validFrom: string;
  validUntil?: string;
}

export const budgetsApi = {
  getAll: (token: string) => apiRequest<Budget[]>('/budgets', {}, token),

  getStatus: (token: string) => apiRequest<BudgetStatus[]>('/budgets/status', {}, token),

  create: (token: string, data: CreateBudgetRequest) =>
    apiRequest<Budget>('/budgets', { method: 'POST', body: JSON.stringify(data) }, token),

  update: (token: string, id: string, data: Partial<CreateBudgetRequest>) =>
    apiRequest<Budget>(`/budgets/${id}`, { method: 'PUT', body: JSON.stringify(data) }, token),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/budgets/${id}`, { method: 'DELETE' }, token),
};
