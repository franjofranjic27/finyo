import { apiRequest } from './client';

export interface InsuranceOverview {
  id: string;
  code: string;
  nameEn: string;
  nameDe: string;
  descriptionEn: string;
  descriptionDe: string;
  mandatory: boolean;
  recommended: boolean;
  typicalCostMin?: number;
  typicalCostMax?: number;
  comparisonUrl?: string;
  sortOrder: number;
  hasInsurance: boolean;
  statusUpdatedAt?: string;
}

export const insuranceApi = {
  getOverview: (token: string) =>
    apiRequest<InsuranceOverview[]>('/insurance', {}, token),

  updateStatus: (token: string, insuranceTypeId: string, hasInsurance: boolean) =>
    apiRequest<InsuranceOverview>(
      `/insurance/${insuranceTypeId}/status`,
      { method: 'PATCH', body: JSON.stringify({ hasInsurance }) },
      token,
    ),
};
