import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';
import { useAuth } from '@/auth/useAuth';
import { analyticsApi } from '@/api/analytics';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import { budgetApi } from '@/api/budget';
import type { MonthlyBudget } from '@/api/budget';
import { wealthApi } from '@/api/wealth';
import type { WealthOverview } from '@/api/wealth';
import { currentMonth, monthRange } from '@/lib/dates';
import type { DateRange } from '@/lib/dates';
import type { MonthlyDataPoint, SpendingSummary } from '@/types';

/**
 * Query hooks shared by the dashboard cards. Each card owns its loading and
 * error state; react-query dedupes the identical keys into one request and the
 * keys match the budget page, so both screens share the same cache entries.
 */

const TREND_RANGE = 'LAST_6_MONTHS';

function useToken(): string {
  const { accessToken } = useAuth();
  return accessToken ?? '';
}

/** The running calendar month, stable for the lifetime of the component. */
export function useCurrentMonthRange(): DateRange {
  return useMemo(() => monthRange(currentMonth()), []);
}

export function useMonthSummary(): UseQueryResult<SpendingSummary> {
  const token = useToken();
  const { from, to } = useCurrentMonthRange();

  return useQuery({
    queryKey: ['analytics', 'summary', from, to],
    queryFn: () => analyticsApi.getSummaryRange(token, from, to),
    enabled: !!token,
  });
}

export function useMonthCategoryBreakdown(): UseQueryResult<RangeCategoryBreakdown[]> {
  const token = useToken();
  const { from, to } = useCurrentMonthRange();

  return useQuery({
    queryKey: ['analytics', 'categories', from, to],
    queryFn: () => analyticsApi.getCategoryBreakdownRange(token, from, to),
    enabled: !!token,
  });
}

export function useMonthlyTrend(): UseQueryResult<MonthlyDataPoint[]> {
  const token = useToken();

  return useQuery({
    queryKey: ['analytics', 'monthly', TREND_RANGE],
    queryFn: () => analyticsApi.getMonthlyTrend(token, TREND_RANGE),
    enabled: !!token,
  });
}

export function useMonthlyBudget(): UseQueryResult<MonthlyBudget> {
  const token = useToken();

  return useQuery({
    queryKey: ['monthly-budget'],
    queryFn: () => budgetApi.getMonthlyBudget(token),
    enabled: !!token,
  });
}

export function useWealthOverview(): UseQueryResult<WealthOverview> {
  const token = useToken();

  return useQuery({
    queryKey: ['wealth'],
    queryFn: () => wealthApi.getOverview(token),
    enabled: !!token,
  });
}
