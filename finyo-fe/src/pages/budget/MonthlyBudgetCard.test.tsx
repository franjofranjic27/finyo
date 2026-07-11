import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthlyBudgetCard } from './MonthlyBudgetCard';
import { budgetApi } from '@/api/budget';
import { formatCHF } from '@/lib/formatters';
import { renderWithProviders } from '@/test/test-utils';
import { monthlyBudget } from '@/test/fixtures/budget';

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

vi.mock('@/api/budget', () => ({
  budgetApi: {
    getFixedCosts: vi.fn(),
    createFixedCost: vi.fn(),
    updateFixedCost: vi.fn(),
    deleteFixedCost: vi.fn(),
    getMonthlyBudget: vi.fn(),
    updateMonthlyBudget: vi.fn(),
  },
}));

describe('MonthlyBudgetCard', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.updateMonthlyBudget).mockResolvedValue(monthlyBudget());
  });

  it('renders the income and allocation flow with signed amounts', () => {
    renderWithProviders(<MonthlyBudgetCard budget={monthlyBudget()} />);

    const income = screen.getByText("+CHF 5'268.90");
    expect(income).toHaveClass('text-emerald-500');
    // Savings and tax reserve share the amount, as do investing and pillar 3a.
    expect(screen.getAllByText('−CHF 800.00')).toHaveLength(2);
    expect(screen.getAllByText('−CHF 600.00')).toHaveLength(2);
    const fixedCosts = screen.getByText('−CHF 314.55');
    expect(fixedCosts).toHaveClass('text-red-500');
    expect(screen.getByText('−CHF 230.00')).toBeInTheDocument();
  });

  it('shows the available tile with the share of net income', () => {
    renderWithProviders(<MonthlyBudgetCard budget={monthlyBudget()} />);

    const amount = screen.getByText("CHF 1'924.35");
    expect(amount).toHaveClass('text-emerald-500');
    // 1924.35 / 5268.90 → 36.5%
    expect(screen.getByText('per month · 36.5% of net income')).toBeInTheDocument();
  });

  it('styles a negative available amount red', () => {
    renderWithProviders(
      <MonthlyBudgetCard budget={monthlyBudget({ available: -156.4 })} />,
    );

    const amount = screen.getByText(formatCHF(-156.4));
    expect(amount).toHaveClass('text-red-500');
  });

  it('omits the share sub-line when the net income is zero', () => {
    renderWithProviders(
      <MonthlyBudgetCard budget={monthlyBudget({ netIncome: 0, available: -100 })} />,
    );

    expect(screen.queryByText(/of net income/)).not.toBeInTheDocument();
  });

  it('submits all six editable amounts from the edit dialog', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<MonthlyBudgetCard budget={monthlyBudget()} />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button', { name: 'Edit amounts' }));
    const netIncomeInput = screen.getByLabelText('Net income');
    expect(netIncomeInput).toHaveValue(5268.9);
    await user.clear(netIncomeInput);
    await user.type(netIncomeInput, '6000');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(budgetApi.updateMonthlyBudget).toHaveBeenCalledWith('test-token', {
        netIncome: 6000,
        savings: 800,
        investing: 600,
        pillar3a: 600,
        taxReserve: 800,
        workCosts: 230,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] }),
    );
  });
});
