import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { BudgetStatusCard } from './BudgetStatusCard';
import { analyticsApi } from '@/api/analytics';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { monthlyBudget } from '@/test/fixtures/budget';
import { currentMonth, monthRange } from '@/lib/dates';
import type { SpendingSummary } from '@/types';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({ accessToken: 'test-token' }),
}));

vi.mock('@/api/analytics', () => ({
  analyticsApi: { getSummaryRange: vi.fn() },
}));

vi.mock('@/api/budget', () => ({
  budgetApi: { getMonthlyBudget: vi.fn() },
}));

function spendingSummary(overrides: Partial<SpendingSummary> = {}): SpendingSummary {
  return {
    totalIncome: '5268.90',
    totalExpenses: '3200.00',
    netAmount: '2068.90',
    rangeStart: '2026-07-01',
    rangeEnd: '2026-07-31',
    transactionCount: 12,
    ...overrides,
  };
}

describe('BudgetStatusCard', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    vi.mocked(analyticsApi.getSummaryRange).mockResolvedValue(spendingSummary());
  });

  it('shows a skeleton while plan and summary load', () => {
    vi.mocked(budgetApi.getMonthlyBudget).mockReturnValue(new Promise(() => {}));
    const { container } = renderWithProviders(<BudgetStatusCard />);

    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('compares actual income and variable spending with the plan', async () => {
    renderWithProviders(<BudgetStatusCard />);

    expect(await screen.findByText('Net income')).toBeInTheDocument();
    expect(screen.getByText('Variable expenses')).toBeInTheDocument();

    // income: booked 5268.90 of the planned 5268.90
    expect(screen.getAllByText(/CHF 5'268\.90/).length).toBeGreaterThan(0);
    // variable: 3200.00 expenses minus 314.55 fixed costs = 2885.45 of 1924.35 available
    expect(screen.getByText(/CHF 2'885\.45/)).toBeInTheDocument();
    // fixed costs are shown separately from the available amount
    expect(screen.getByText('CHF 314.55')).toBeInTheDocument();
    expect(screen.getByText('100.0%')).toBeInTheDocument();

    const { from, to } = monthRange(currentMonth());
    expect(analyticsApi.getSummaryRange).toHaveBeenCalledWith('test-token', from, to);
  });

  it('marks overspending against the available amount as destructive', async () => {
    renderWithProviders(<BudgetStatusCard />);
    await screen.findByText('Variable expenses');

    // 2885.45 / 1924.35 = 149.9 %
    expect(screen.getByText('149.9%')).toHaveClass('text-destructive');
  });

  it('links to the budget plan when no budget is planned', async () => {
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(
      monthlyBudget({ netIncome: 0, available: 0 }),
    );
    renderWithProviders(<BudgetStatusCard />);

    const cta = await screen.findByRole('link', { name: /Plan budget/ });
    expect(cta).toHaveAttribute('href', '/budget');
  });

  it('links to the month view when the month has no transactions', async () => {
    vi.mocked(analyticsApi.getSummaryRange).mockResolvedValue(
      spendingSummary({
        totalIncome: '0.00',
        totalExpenses: '0.00',
        netAmount: '0.00',
        transactionCount: 0,
      }),
    );
    renderWithProviders(<BudgetStatusCard />);

    const cta = await screen.findByRole('link', { name: /Import bank statement/ });
    expect(cta).toHaveAttribute('href', '/budget?tab=month');
  });

  it('shows an inline error when a query fails', async () => {
    vi.mocked(budgetApi.getMonthlyBudget).mockRejectedValue(new Error('boom'));
    renderWithProviders(<BudgetStatusCard />);

    expect(await screen.findByText('Could not load the month view')).toBeInTheDocument();
  });
});
