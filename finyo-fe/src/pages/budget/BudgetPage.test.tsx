import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BudgetPage } from './BudgetPage';
import { budgetApi } from '@/api/budget';
import { salaryApi } from '@/api/salary';
import { renderWithProviders } from '@/test/test-utils';
import { fixedCostList, monthlyBudget } from '@/test/fixtures/budget';
import { salary } from '@/test/fixtures/salary';

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

vi.mock('@/api/salary', () => ({
  salaryApi: {
    get: vi.fn(),
    update: vi.fn(),
  },
}));

// The month view has its own test suite — keep this one focused on the tabs.
vi.mock('./MonthTab', () => ({
  MonthTab: () => <div>month-tab-content</div>,
}));

describe('BudgetPage', () => {
  it('renders fixed costs and monthly budget from both queries in the plan tab', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />);

    expect(await screen.findByText('Krankenkasse')).toBeInTheDocument();
    // Card title and the derived flow row share the same label.
    expect(screen.getAllByText('Fixed costs & subscriptions').length).toBeGreaterThan(0);
    // Tab trigger and card title share the same label.
    expect(screen.getAllByText('Monthly budget').length).toBeGreaterThan(1);
    expect(screen.getByText("CHF 1'924.35")).toBeInTheDocument();
    expect(budgetApi.getFixedCosts).toHaveBeenCalledWith('test-token');
    expect(budgetApi.getMonthlyBudget).toHaveBeenCalledWith('test-token');
  });

  it('renders all tab triggers with the plan tab as the default', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />);

    expect(screen.getByRole('tab', { name: 'Monthly budget' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Month view' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Salary calculator' })).toBeInTheDocument();
    expect(await screen.findByText('Krankenkasse')).toBeInTheDocument();
    expect(screen.queryByText('Inputs')).not.toBeInTheDocument();
    expect(screen.queryByText('month-tab-content')).not.toBeInTheDocument();
  });

  it('deep-links to the month tab via ?tab=month', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />, { route: '/budget?tab=month' });

    expect(await screen.findByText('month-tab-content')).toBeInTheDocument();
    expect(screen.queryByText('Krankenkasse')).not.toBeInTheDocument();
  });

  it('switches to the month tab and back via the trigger', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    const user = userEvent.setup();
    renderWithProviders(<BudgetPage />);

    await user.click(screen.getByRole('tab', { name: 'Month view' }));
    expect(await screen.findByText('month-tab-content')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Monthly budget' }));
    expect(await screen.findByText('Krankenkasse')).toBeInTheDocument();
  });

  it('switches to the salary calculator tab', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockResolvedValue(fixedCostList());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    const user = userEvent.setup();
    renderWithProviders(<BudgetPage />);

    await user.click(screen.getByRole('tab', { name: 'Salary calculator' }));

    expect(await screen.findByLabelText('Gross salary per month')).toHaveValue(5787);
    expect(screen.queryByText("CHF 1'924.35")).not.toBeInTheDocument();
  });

  it('shows an error message when the budget cannot be loaded', async () => {
    vi.mocked(budgetApi.getFixedCosts).mockRejectedValue(new Error('boom'));
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    renderWithProviders(<BudgetPage />);

    expect(await screen.findByText('Could not load budget')).toBeInTheDocument();
  });
});
