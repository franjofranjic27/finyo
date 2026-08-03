import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FixedCostsCard } from './FixedCostsCard';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { fixedCost, fixedCostList } from '@/test/fixtures/budget';

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
    importFixedCosts: vi.fn(),
    getMonthlyBudget: vi.fn(),
    updateMonthlyBudget: vi.fn(),
  },
}));

describe('FixedCostsCard', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.createFixedCost).mockResolvedValue(fixedCost({ id: 'fc-new' }));
    vi.mocked(budgetApi.updateFixedCost).mockResolvedValue(fixedCost({ id: 'fc1' }));
    vi.mocked(budgetApi.deleteFixedCost).mockResolvedValue(undefined);
    vi.mocked(budgetApi.importFixedCosts).mockResolvedValue({
      created: 1,
      updated: 1,
      failed: 0,
      errors: [],
    });
  });

  it('renders the rows with category and interval badges and the totals', () => {
    renderWithProviders(<FixedCostsCard list={fixedCostList()} />);

    const rows = screen.getAllByRole('row');
    const krankenkasse = rows.find((row) => row.textContent?.includes('Krankenkasse'));
    expect(krankenkasse).toBeDefined();
    expect(within(krankenkasse as HTMLElement).getByText('Versicherung')).toBeInTheDocument();
    expect(within(krankenkasse as HTMLElement).getByText('monthly')).toBeInTheDocument();
    expect(within(krankenkasse as HTMLElement).getByText("CHF 2'460.00")).toBeInTheDocument();
    expect(within(krankenkasse as HTMLElement).getByText('CHF 205.00')).toBeInTheDocument();

    const activeFitness = rows.find((row) => row.textContent?.includes('ActiveFitness'));
    expect(activeFitness).toBeDefined();
    const yearlyBadge = within(activeFitness as HTMLElement).getByText('yearly');
    expect(yearlyBadge).toHaveClass('text-violet-500');
    expect(within(activeFitness as HTMLElement).getByText('CHF 58.25')).toBeInTheDocument();

    // "Total" renders twice (mobile list + table) — the table row is the one with a <tr>.
    const totalRow = screen
      .getAllByText('Total')
      .map((element) => element.closest('tr'))
      .find((row) => row !== null);
    expect(totalRow).toBeDefined();
    expect(within(totalRow as HTMLElement).getByText("CHF 3'774.60")).toBeInTheDocument();
    expect(within(totalRow as HTMLElement).getByText('CHF 314.55')).toBeInTheDocument();
  });

  it('omits the category badge when the category is null', () => {
    renderWithProviders(
      <FixedCostsCard
        list={fixedCostList({ items: [fixedCost({ id: 'fc1', category: null })] })}
      />,
    );

    expect(screen.queryByText('Versicherung')).not.toBeInTheDocument();
  });

  it('shows the empty state when there are no fixed costs', () => {
    renderWithProviders(
      <FixedCostsCard list={fixedCostList({ items: [], totalPerYear: 0, totalPerMonth: 0 })} />,
    );

    expect(screen.getByText('No fixed costs yet')).toBeInTheDocument();
    expect(screen.queryByText('Total')).not.toBeInTheDocument();
  });

  it('creates a fixed cost with the entered values and invalidates both queries', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<FixedCostsCard list={fixedCostList()} />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button', { name: 'Add fixed cost' }));
    await user.type(screen.getByLabelText('Name'), 'Netflix');
    await user.type(screen.getByLabelText('Category'), 'Entertainment');
    await user.type(screen.getByLabelText('Amount (CHF)'), '18.90');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(budgetApi.createFixedCost).toHaveBeenCalledWith('test-token', {
        name: 'Netflix',
        category: 'Entertainment',
        paymentInterval: 'MONTHLY',
        amount: 18.9,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['fixed-costs'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] });
  });

  it('updates a fixed cost through the prefilled edit dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<FixedCostsCard list={fixedCostList()} />);

    await user.click(screen.getAllByRole('button', { name: 'Edit fixed cost' })[0]);
    const amountInput = screen.getByLabelText('Amount (CHF)');
    expect(amountInput).toHaveValue(205);
    await user.clear(amountInput);
    await user.type(amountInput, '210');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(budgetApi.updateFixedCost).toHaveBeenCalledWith('test-token', 'fc1', {
        name: 'Krankenkasse',
        category: 'Versicherung',
        paymentInterval: 'MONTHLY',
        amount: 210,
      }),
    );
  });

  it('deletes a fixed cost after confirmation and invalidates both queries', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<FixedCostsCard list={fixedCostList()} />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getAllByRole('button', { name: 'Remove fixed cost' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove “Krankenkasse”?');
    await waitFor(() =>
      expect(budgetApi.deleteFixedCost).toHaveBeenCalledWith('test-token', 'fc1'),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['fixed-costs'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] });
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<FixedCostsCard list={fixedCostList()} />);

    await user.click(screen.getAllByRole('button', { name: 'Remove fixed cost' })[0]);

    expect(budgetApi.deleteFixedCost).not.toHaveBeenCalled();
  });

  describe('CSV import', () => {
    function csvFile(content: string): File {
      return new File([content], 'fixed-costs.csv', { type: 'text/csv' });
    }

    it('imports the parsed rows, shows the result and invalidates both queries', async () => {
      const user = userEvent.setup();
      const { queryClient } = renderWithProviders(<FixedCostsCard list={fixedCostList()} />);
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const file = csvFile(
        "Bezeichnung;Kategorie;Zahlart;Betrag\nKrankenkasse;Versicherung;monatl.;2'460.00\nActiveFitness;;jährl.;699",
      );
      await user.upload(screen.getByLabelText('Import CSV'), file);

      await waitFor(() =>
        expect(budgetApi.importFixedCosts).toHaveBeenCalledWith('test-token', [
          { name: 'Krankenkasse', category: 'Versicherung', paymentInterval: 'MONTHLY', amount: 2460 },
          { name: 'ActiveFitness', category: undefined, paymentInterval: 'YEARLY', amount: 699 },
        ]),
      );
      expect(await screen.findByText('1 created, 1 updated, 0 failed')).toBeInTheDocument();
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['fixed-costs'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['monthly-budget'] });
    });

    it('lists backend row errors from the bulk result', async () => {
      vi.mocked(budgetApi.importFixedCosts).mockResolvedValue({
        created: 1,
        updated: 0,
        failed: 1,
        errors: ['Row 2: duplicate name'],
      });
      const user = userEvent.setup();
      renderWithProviders(<FixedCostsCard list={fixedCostList()} />);

      await user.upload(
        screen.getByLabelText('Import CSV'),
        csvFile('Krankenkasse;Versicherung;monatl.;205\nKrankenkasse;;m;205'),
      );

      expect(await screen.findByText('1 created, 0 updated, 1 failed')).toBeInTheDocument();
      expect(screen.getByText('Row 2: duplicate name')).toBeInTheDocument();
    });

    it('shows the no-rows message and skips the import when nothing parses', async () => {
      const user = userEvent.setup();
      renderWithProviders(<FixedCostsCard list={fixedCostList()} />);

      await user.upload(
        screen.getByLabelText('Import CSV'),
        csvFile(';Versicherung;weekly;-5'),
      );

      expect(await screen.findByText('No valid rows found in the file')).toBeInTheDocument();
      expect(budgetApi.importFixedCosts).not.toHaveBeenCalled();
    });
  });
});
