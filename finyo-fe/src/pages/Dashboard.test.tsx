import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Dashboard } from './Dashboard';
import { analyticsApi } from '@/api/analytics';
import { renderWithProviders } from '@/test/test-utils';
import type { CategoryBreakdown, MonthlyDataPoint, SpendingSummary } from '@/types';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    getSummary: vi.fn(),
    getCategoryBreakdown: vi.fn(),
    getMonthlyTrend: vi.fn(),
  },
}));

const summary: SpendingSummary = {
  totalExpenses: '-2500.00',
  totalIncome: '8000.00',
  netAmount: '5500.00',
  rangeStart: '2026-07-01',
  rangeEnd: '2026-07-06',
  transactionCount: 12,
};

const categories: CategoryBreakdown[] = [
  { categoryId: '1', categoryName: 'Rent', total: '-1500.00', percentage: 60 },
  { categoryId: '2', categoryName: 'Groceries', total: '-1000.00', percentage: 40 },
];

const monthly: MonthlyDataPoint[] = [
  { year: 2026, month: 6, expenses: '-2400', income: '8000', net: '5600' },
  { year: 2026, month: 7, expenses: '-2500', income: '8000', net: '5500' },
];

function mockAnalytics() {
  vi.mocked(analyticsApi.getSummary).mockResolvedValue(summary);
  vi.mocked(analyticsApi.getCategoryBreakdown).mockResolvedValue(categories);
  vi.mocked(analyticsApi.getMonthlyTrend).mockResolvedValue(monthly);
}

describe('Dashboard', () => {
  it('shows the summary cards with formatted amounts', async () => {
    mockAnalytics();
    renderWithProviders(<Dashboard />);

    expect(await screen.findByText("CHF 5'500.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 8'000.00")).toBeInTheDocument();
    expect(screen.getByText("CHF -2'500.00")).toBeInTheDocument();
    expect(screen.getByText('Net This Month')).toBeInTheDocument();
    expect(screen.getByText('12 transactions')).toBeInTheDocument();
  });

  it('shows the biggest category by absolute spend', async () => {
    mockAnalytics();
    renderWithProviders(<Dashboard />);

    expect(await screen.findByText('Rent')).toBeInTheDocument();
    expect(screen.getByText("CHF -1'500.00")).toBeInTheDocument();
  });

  it('requests analytics with the auth token', async () => {
    mockAnalytics();
    renderWithProviders(<Dashboard />);
    await screen.findByText("CHF 5'500.00");

    expect(analyticsApi.getSummary).toHaveBeenCalledWith('test-token', 'THIS_MONTH');
    expect(analyticsApi.getCategoryBreakdown).toHaveBeenCalledWith('test-token', 'THIS_MONTH');
    expect(analyticsApi.getMonthlyTrend).toHaveBeenCalledWith('test-token', 'LAST_6_MONTHS');
  });

  it('renders the chart card titles', async () => {
    mockAnalytics();
    renderWithProviders(<Dashboard />);

    expect(await screen.findByText('Spending by Category')).toBeInTheDocument();
    expect(screen.getByText('Monthly Overview')).toBeInTheDocument();
  });
});
