import type { SavingsGoal } from '@/types';
import { apiRequest } from './client';

export interface CreateSavingsGoalRequest {
  name: string;
  targetAmount: string;
  currentAmount?: string;
  targetDate?: string;
  accountId?: string;
  icon?: string;
  color?: string;
}

export const savingsApi = {
  getAll: (token: string) => apiRequest<SavingsGoal[]>('/savings-goals', {}, token),

  getById: (token: string, id: string) =>
    apiRequest<SavingsGoal>(`/savings-goals/${id}`, {}, token),

  create: (token: string, data: CreateSavingsGoalRequest) =>
    apiRequest<SavingsGoal>('/savings-goals', { method: 'POST', body: JSON.stringify(data) }, token),

  update: (token: string, id: string, data: Partial<CreateSavingsGoalRequest>) =>
    apiRequest<SavingsGoal>(
      `/savings-goals/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token
    ),

  archive: (token: string, id: string) =>
    apiRequest<SavingsGoal>(
      `/savings-goals/${id}/archive`,
      { method: 'POST' },
      token
    ),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/savings-goals/${id}`, { method: 'DELETE' }, token),
};
