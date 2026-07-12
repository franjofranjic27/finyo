import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { BudgetPage } from './BudgetPage';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { fixedCostList, monthlyBudget } from '@/test/fixtures/budget';

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

describe('BudgetPage', () => {
  it('renders fixed costs and monthly budget from both queries', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />);

    expect(await screen.findByText('Krankenkasse')).toBeInTheDocument();
    // Card title and the derived flow row share the same label.
    expect(screen.getAllByText('Fixed costs & subscriptions').length).toBeGreaterThan(0);
    expect(screen.getByText('Monthly budget')).toBeInTheDocument();
    expect(screen.getByText("CHF 1'924.35")).toBeInTheDocument();
    expect(budgetApi.getFixedCosts).toHaveBeenCalledWith('test-token');
    expect(budgetApi.getMonthlyBudget).toHaveBeenCalledWith('test-token');
  });

  it('shows an error message when the budget cannot be loaded', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockRejectedValue(new Error('boom'));
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />);

    expect(await screen.findByText('Could not load budget')).toBeInTheDocument();
  });
});
