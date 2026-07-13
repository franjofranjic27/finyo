import type { SpendingSummary, CategoryBreakdown, MonthlyDataPoint, TimeRange } from '@/types';
import { apiRequest } from './client';

/**
 * Category breakdown item for explicit from/to ranges. Unlike the classic
 * CategoryBreakdown, uncategorised spending is included with a null category.
 */
export interface RangeCategoryBreakdown {
  categoryId: string | null;
  categoryName: string | null;
  categoryColor: string | null;
  total: string;
  percentage: number;
}

export const analyticsApi = {
  getSummary: (token: string, range: TimeRange = 'THIS_MONTH') =>
    apiRequest<SpendingSummary>(`/analytics/summary?range=${range}`, {}, token),

  getSummaryRange: (token: string, from: string, to: string) =>
    apiRequest<SpendingSummary>(`/analytics/summary?from=${from}&to=${to}`, {}, token),

  getCategoryBreakdown: (token: string, range: TimeRange = 'THIS_MONTH') =>
    apiRequest<CategoryBreakdown[]>(`/analytics/by-category?range=${range}`, {}, token),

  getCategoryBreakdownRange: (token: string, from: string, to: string) =>
    apiRequest<RangeCategoryBreakdown[]>(`/analytics/by-category?from=${from}&to=${to}`, {}, token),

  getMonthlyTrend: (token: string, range: TimeRange = 'LAST_12_MONTHS') =>
    apiRequest<MonthlyDataPoint[]>(`/analytics/monthly?range=${range}`, {}, token),
};
