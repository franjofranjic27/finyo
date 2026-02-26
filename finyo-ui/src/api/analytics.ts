import type { SpendingSummary, CategoryBreakdown, MonthlyDataPoint, TimeRange } from '@/types';
import { apiRequest } from './client';

export const analyticsApi = {
  getSummary: (token: string, range: TimeRange = 'THIS_MONTH') =>
    apiRequest<SpendingSummary>(`/analytics/summary?range=${range}`, {}, token),

  getCategoryBreakdown: (token: string, range: TimeRange = 'THIS_MONTH') =>
    apiRequest<CategoryBreakdown[]>(`/analytics/by-category?range=${range}`, {}, token),

  getMonthlyTrend: (token: string, range: TimeRange = 'LAST_12_MONTHS') =>
    apiRequest<MonthlyDataPoint[]>(`/analytics/monthly?range=${range}`, {}, token),
};
