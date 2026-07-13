import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { DashboardPage } from './DashboardPage';
import { analyticsApi } from '@/api/analytics';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import { budgetApi } from '@/api/budget';
import { wealthApi } from '@/api/wealth';
import { renderWithProviders } from '@/test/test-utils';
import { monthlyBudget } from '@/test/fixtures/budget';
import { wealthOverview } from '@/test/fixtures/wealth';
import { currentMonth, monthRange } from '@/lib/dates';
import { useAuth } from '@/auth/useAuth';
import type { MonthlyDataPoint, SpendingSummary } from '@/types';

vi.mock('@/auth/useAuth', () => ({
  useAuth: vi.fn(() => ({ accessToken: 'test-token' })),
}));

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    getSummaryRange: vi.fn(),
    getCategoryBreakdownRange: vi.fn(),
    getMonthlyTrend: vi.fn(),
  },
}));

vi.mock('@/api/budget', () => ({
  budgetApi: { getMonthlyBudget: vi.fn() },
}));

vi.mock('@/api/wealth', () => ({
  wealthApi: { getOverview: vi.fn() },
}));

const summary: SpendingSummary = {
  totalIncome: '5268.90',
  totalExpenses: '3200.00',
  netAmount: '2068.90',
  rangeStart: '2026-07-01',
  rangeEnd: '2026-07-31',
  transactionCount: 12,
};

const breakdown: RangeCategoryBreakdown[] = [
  {
    categoryId: 'c1',
    categoryName: 'Wohnen',
    categoryColor: '#22c55e',
    total: '1800.00',
    percentage: 56.3,
  },
  {
    categoryId: 'c2',
    categoryName: 'Lebensmittel',
    categoryColor: null,
    total: '1400.00',
    percentage: 43.7,
  },
];

const trend: MonthlyDataPoint[] = [
  { year: 2026, month: 6, expenses: '-3100', income: '5268.90', net: '2168.90' },
  { year: 2026, month: 7, expenses: '-3200', income: '5268.90', net: '2068.90' },
];

/** Values from the deleted demo constants — they must never appear again. */
const DEMO_VALUES = ["CHF 7'500.00", "CHF 4'259.50", "CHF -3'240.50", '34 transactions'];

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({ accessToken: 'test-token' } as ReturnType<typeof useAuth>);
    vi.mocked(analyticsApi.getSummaryRange).mockResolvedValue(summary);
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockResolvedValue(breakdown);
    vi.mocked(analyticsApi.getMonthlyTrend).mockResolvedValue(trend);
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    vi.mocked(wealthApi.getOverview).mockResolvedValue(wealthOverview());
  });

  it('renders wealth, budget status, month KPIs and both charts', async () => {
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText("CHF 99'719.00")).toBeInTheDocument();
    expect(screen.getByText('Budget this month')).toBeInTheDocument();
    expect(screen.getByText('Net This Month')).toBeInTheDocument();
    expect(screen.getByText("CHF 2'068.90")).toBeInTheDocument();
    expect(screen.getByText('12 transactions')).toBeInTheDocument();
    expect(screen.getByText("CHF 3'200.00")).toBeInTheDocument();
    expect(screen.getByText('Spending by Category')).toBeInTheDocument();
    expect(screen.getByText('Monthly Overview')).toBeInTheDocument();
  });

  it('shows the biggest category of the month', async () => {
    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText('Biggest Category')).toBeInTheDocument();
    // "Wohnen" appears in the KPI tile and in the donut legend
    expect(screen.getAllByText('Wohnen').length).toBeGreaterThan(0);
    expect(screen.getAllByText("CHF 1'800.00").length).toBeGreaterThan(0);
  });

  it('renders no demo data once the queries resolve', async () => {
    renderWithProviders(<DashboardPage />);
    await screen.findByText("CHF 99'719.00");

    for (const value of DEMO_VALUES) {
      expect(screen.queryByText(value)).not.toBeInTheDocument();
    }
  });

  it('queries every source with the auth token for the running month', async () => {
    renderWithProviders(<DashboardPage />);
    await screen.findByText("CHF 99'719.00");

    const { from, to } = monthRange(currentMonth());
    expect(analyticsApi.getSummaryRange).toHaveBeenCalledWith('test-token', from, to);
    expect(analyticsApi.getCategoryBreakdownRange).toHaveBeenCalledWith('test-token', from, to);
    expect(analyticsApi.getMonthlyTrend).toHaveBeenCalledWith('test-token', 'LAST_6_MONTHS');
    expect(budgetApi.getMonthlyBudget).toHaveBeenCalledWith('test-token');
    expect(wealthApi.getOverview).toHaveBeenCalledWith('test-token');
  });

  it('gates all queries on the access token', () => {
    vi.mocked(useAuth).mockReturnValue({ accessToken: null } as unknown as ReturnType<
      typeof useAuth
    >);
    renderWithProviders(<DashboardPage />);

    expect(analyticsApi.getSummaryRange).not.toHaveBeenCalled();
    expect(analyticsApi.getCategoryBreakdownRange).not.toHaveBeenCalled();
    expect(analyticsApi.getMonthlyTrend).not.toHaveBeenCalled();
    expect(budgetApi.getMonthlyBudget).not.toHaveBeenCalled();
    expect(wealthApi.getOverview).not.toHaveBeenCalled();
  });
});
