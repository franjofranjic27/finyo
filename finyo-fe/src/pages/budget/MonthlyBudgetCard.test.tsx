import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthlyBudgetCard } from './MonthlyBudgetCard';
import { budgetApi } from '@/api/budget';
import { formatCHF } from '@/lib/formatters';
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
    createBudgetPosition: vi.fn(),
    updateBudgetPosition: vi.fn(),
    deleteBudgetPosition: vi.fn(),
  },
}));

function renderCard(budget = monthlyBudget(), fixedCosts = fixedCostList()) {
  return renderWithProviders(<MonthlyBudgetCard budget={budget} fixedCosts={fixedCosts} />);
}

describe('MonthlyBudgetCard', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.updateMonthlyBudget).mockResolvedValue(monthlyBudget());
    vi.mocked(budgetApi.createBudgetPosition).mockResolvedValue(monthlyBudget());
    vi.mocked(budgetApi.updateBudgetPosition).mockResolvedValue(monthlyBudget());
    vi.mocked(budgetApi.deleteBudgetPosition).mockResolvedValue(monthlyBudget());
  });

  it('renders the income and position rows with signed amounts', () => {
    renderCard();

    const income = screen.getByText("+CHF 5'268.90");
    expect(income).toHaveClass('text-emerald-500');
    expect(screen.getByText('Sparen')).toBeInTheDocument();
    expect(screen.getByText('Säule 3a')).toBeInTheDocument();
    // Sparen and Steuerrückstellung share the amount, as do Investieren and Säule 3a.
    expect(screen.getAllByText('−CHF 800.00')).toHaveLength(2);
    expect(screen.getAllByText('−CHF 600.00')).toHaveLength(2);
    const workCosts = screen.getByText('−CHF 230.00');
    expect(workCosts).toHaveClass('text-red-500');
  });

  it('renders the fixed-cost groups sorted by monthly total with collapsed items', async () => {
    const user = userEvent.setup();
    renderCard();

    // Groups are rows with a summed monthly amount; items are hidden while collapsed.
    expect(screen.getByText('Versicherung')).toBeInTheDocument();
    expect(screen.getByText('Sport')).toBeInTheDocument();
    expect(screen.getByText('−CHF 205.00')).toBeInTheDocument();
    expect(screen.getAllByText('1 item')).toHaveLength(2);
    expect(screen.queryByText('Krankenkasse')).not.toBeInTheDocument();

    await user.click(screen.getByText('Versicherung'));
    expect(await screen.findByText('Krankenkasse')).toBeInTheDocument();
    // The other group stays collapsed.
    expect(screen.queryByText('ActiveFitness')).not.toBeInTheDocument();
  });

  it('renders the uncategorized group last', () => {
    const list = fixedCostList();
    list.items = [
      { ...list.items[0], id: 'fc3', name: 'Spotify', category: null, amountPerMonth: 12.95 },
      ...list.items,
    ];
    renderCard(monthlyBudget(), list);

    const groups = screen
      .getAllByText(/^(Versicherung|Sport|Uncategorized)$/)
      .map((el) => el.textContent);
    expect(groups).toEqual(['Versicherung', 'Sport', 'Uncategorized']);
  });

  it('shows the available tile with the share of net income', () => {
    renderCard();

    const amount = screen.getByText("CHF 1'924.35");
    expect(amount).toHaveClass('text-emerald-500');
    // 1924.35 / 5268.90 → 36.5%
    expect(screen.getByText('per month · 36.5% of net income')).toBeInTheDocument();
  });

  it('styles a negative available amount red', () => {
    renderCard(monthlyBudget({ available: -156.4 }));

    const amount = screen.getByText(formatCHF(-156.4));
    expect(amount).toHaveClass('text-red-500');
  });

  it('omits the share sub-line when the net income is zero', () => {
    renderCard(monthlyBudget({ netIncome: 0, available: -100 }));

    expect(screen.queryByText(/of net income/)).not.toBeInTheDocument();
  });

  it('shows an empty state when no positions exist', () => {
    renderCard(monthlyBudget({ positions: [] }));

    expect(screen.getByText('No spending pots yet.')).toBeInTheDocument();
  });

  it('submits the net income from the edit dialog', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderCard();
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
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] }),
    );
  });

  it('creates a new position via the add-pot dialog', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderCard();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button', { name: 'Add pot' }));
    await user.type(screen.getByLabelText('Name'), 'Ferien');
    await user.type(screen.getByLabelText('Amount per month'), '300');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(budgetApi.createBudgetPosition).toHaveBeenCalledWith('test-token', {
        name: 'Ferien',
        amount: 300,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] }),
    );
  });

  it('edits a position via the row pencil with prefilled values', async () => {
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getAllByRole('button', { name: 'Edit pot' })[0]);
    const nameInput = screen.getByLabelText('Name');
    expect(nameInput).toHaveValue('Sparen');
    expect(screen.getByLabelText('Amount per month')).toHaveValue(800);
    await user.clear(nameInput);
    await user.type(nameInput, 'Notgroschen');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(budgetApi.updateBudgetPosition).toHaveBeenCalledWith('test-token', 'bp1', {
        name: 'Notgroschen',
        amount: 800,
      }),
    );
  });

  it('shows the server error in the position dialog on a duplicate name', async () => {
    vi.mocked(budgetApi.createBudgetPosition).mockRejectedValue(
      new Error('A budget position with this name already exists'),
    );
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getByRole('button', { name: 'Add pot' }));
    await user.type(screen.getByLabelText('Name'), 'Sparen');
    await user.type(screen.getByLabelText('Amount per month'), '100');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('A budget position with this name already exists'),
    ).toBeInTheDocument();
  });

  it('deletes a position after confirmation and invalidates the budget query', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderCard();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getAllByRole('button', { name: 'Delete pot' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove “Sparen”?');
    await waitFor(() =>
      expect(budgetApi.deleteBudgetPosition).toHaveBeenCalledWith('test-token', 'bp1'),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] }),
    );
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getAllByRole('button', { name: 'Delete pot' })[0]);

    expect(budgetApi.deleteBudgetPosition).not.toHaveBeenCalled();
  });
});
