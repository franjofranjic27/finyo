import type { Category } from '@/types';
import { apiRequest } from './client';

export const categoriesApi = {
  getAll: (token: string) => apiRequest<Category[]>('/categories', {}, token),
};
