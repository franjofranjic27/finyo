import { useMemo, useState } from 'react';
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
 * Query hooks for the month figures, the budget plan and the wealth overview.
 * Single source of truth for the query keys: the dashboard cards and the budget
 * month tab go through these hooks, so react-query dedupes identical months
 * into one request and both screens share the same cache entries.
 */

const TREND_RANGE = 'LAST_6_MONTHS';

function useToken(): string {
  const { accessToken } = useAuth();
  return accessToken ?? '';
}

/** Range of the given `YYYY-MM` month, defaulting to the running month. */
function useMonthRange(ym?: string): DateRange {
  // The fallback is captured once so the query key stays stable across renders.
  const [runningMonth] = useState(currentMonth);
  const month = ym ?? runningMonth;

  return useMemo(() => monthRange(month), [month]);
}

export function useMonthSummary(ym?: string): UseQueryResult<SpendingSummary> {
  const token = useToken();
  const { from, to } = useMonthRange(ym);

  return useQuery({
    queryKey: ['analytics', 'summary', from, to],
    queryFn: () => analyticsApi.getSummaryRange(token, from, to),
    enabled: !!token,
  });
}

export function useMonthCategoryBreakdown(
  ym?: string,
): UseQueryResult<RangeCategoryBreakdown[]> {
  const token = useToken();
  const { from, to } = useMonthRange(ym);

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
