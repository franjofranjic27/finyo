import { apiRequest } from './client';

/** Keyword-based auto-categorisation rule applied during statement imports. */
export interface CategoryRule {
  id: string;
  keyword: string;
  categoryId: string;
  categoryName: string;
  categoryColor: string | null;
}

export interface CategoryRuleInput {
  keyword: string;
  categoryId: string;
}

export const categoryRulesApi = {
  getAll: (token: string) => apiRequest<CategoryRule[]>('/category-rules', {}, token),

  create: (token: string, data: CategoryRuleInput) =>
    apiRequest<CategoryRule>(
      '/category-rules',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  update: (token: string, id: string, data: CategoryRuleInput) =>
    apiRequest<CategoryRule>(
      `/category-rules/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token,
    ),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/category-rules/${id}`, { method: 'DELETE' }, token),
};
