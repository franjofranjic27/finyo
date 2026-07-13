import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SalaryTab } from './SalaryTab';
import { salaryApi } from '@/api/salary';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { salary } from '@/test/fixtures/salary';
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

vi.mock('@/api/salary', () => ({
  salaryApi: {
    get: vi.fn(),
    update: vi.fn(),
  },
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

function zeroGrossSalary() {
  const base = salary();
  return salary({
    grossMonthly: 0,
    result: { ...base.result, grossMonthly: 0, grossYearly: 0, netMonthly: 0, netYearly: 0 },
  });
}

describe('SalaryTab', () => {
  beforeEach(() => {
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    vi.mocked(salaryApi.update).mockResolvedValue(salary());
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
    vi.mocked(budgetApi.updateMonthlyBudget).mockResolvedValue(monthlyBudget());
  });

  it('renders the inputs from the stored salary', async () => {
    renderWithProviders(<SalaryTab />);

    expect(await screen.findByLabelText('Gross salary per month')).toHaveValue(5787);
    expect(screen.getByText("= CHF 69'444.00 per year")).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '13th month salary' })).not.toBeChecked();
    expect(screen.getByLabelText('AHV / IV / EO')).toHaveValue(5.3);
    expect(screen.getByLabelText('ALV')).toHaveValue(1.1);
    expect(screen.getByLabelText('NBU')).toHaveValue(0.455);
    expect(screen.getByLabelText('KTG')).toHaveValue(0.17);
    expect(screen.getByLabelText('Pension fund (fixed)')).toHaveValue(85.35);
    expect(screen.getByLabelText('Other deductions (fixed)')).toHaveValue(11);
    expect(salaryApi.get).toHaveBeenCalledWith('test-token');
  });

  it('renders the result table with gross, deductions, total and net', async () => {
    renderWithProviders(<SalaryTab />);

    expect(await screen.findByText("CHF 5'787.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 69'444.00")).toBeInTheDocument();
    expect(screen.getByText('−CHF 306.70')).toBeInTheDocument();
    expect(screen.getByText('−CHF 63.65')).toBeInTheDocument();
    expect(screen.getByText('−CHF 26.35')).toBeInTheDocument();
    expect(screen.getByText('−CHF 9.85')).toBeInTheDocument();
    expect(screen.getByText('−CHF 85.35')).toBeInTheDocument();
    expect(screen.getByText('−CHF 502.90')).toBeInTheDocument();
    expect(screen.getByText("CHF 5'284.10")).toBeInTheDocument();
    // Rate column: percentages for social deductions, "fixed" for amounts, total pct.
    expect(screen.getByText('5.3%')).toBeInTheDocument();
    expect(screen.getAllByText('fixed')).toHaveLength(2);
    expect(screen.getByText('8.7%')).toBeInTheDocument();
    // Distribution legend with the shares of the gross salary.
    expect(screen.getByText('Net 91.3%')).toBeInTheDocument();
    expect(screen.getByText('Social insurance 7.0%')).toBeInTheDocument();
    expect(screen.getByText('Pension & fixed 1.7%')).toBeInTheDocument();
  });

  it('recomputes the yearly gross hint from the form state when the 13th salary is toggled', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);

    await user.click(await screen.findByRole('checkbox', { name: '13th month salary' }));

    expect(screen.getByText("= CHF 75'231.00 per year")).toBeInTheDocument();
  });

  it('submits the full salary input from the form state', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);

    const grossInput = await screen.findByLabelText('Gross salary per month');
    await user.clear(grossInput);
    await user.type(grossInput, '6000');
    await user.click(screen.getByRole('button', { name: 'Calculate & save' }));

    await waitFor(() =>
      expect(salaryApi.update).toHaveBeenCalledWith('test-token', {
        grossMonthly: 6000,
        grossYearly: 72000,
        inputMode: 'MONTHLY',
        thirteenthSalary: false,
        ahvPct: 5.3,
        alvPct: 1.1,
        nbuPct: 0.455,
        ktgPct: 0.17,
        pensionFixed: 85.35,
        otherFixed: 11,
      }),
    );
  });

  it('switches to yearly mode and shows the derived monthly gross', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);
    await screen.findByLabelText('Gross salary per month');

    await user.click(screen.getByRole('button', { name: 'CHF per year' }));

    const yearlyInput = screen.getByLabelText('Gross salary per year');
    expect(yearlyInput).toHaveValue(69444);
    expect(screen.queryByLabelText('Gross salary per month')).not.toBeInTheDocument();
    expect(screen.getByText("≈ CHF 5'787.00 per month")).toBeInTheDocument();
  });

  it('recomputes the yearly gross from an edited monthly value when switching to yearly mode', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);

    const grossInput = await screen.findByLabelText('Gross salary per month');
    await user.clear(grossInput);
    await user.type(grossInput, '6000');
    await user.click(screen.getByRole('button', { name: 'CHF per year' }));

    expect(screen.getByLabelText('Gross salary per year')).toHaveValue(72000);
  });

  it('recomputes the monthly gross from an edited yearly value when switching back to monthly mode', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);
    await screen.findByLabelText('Gross salary per month');

    await user.click(screen.getByRole('button', { name: 'CHF per year' }));
    const yearlyInput = screen.getByLabelText('Gross salary per year');
    await user.clear(yearlyInput);
    await user.type(yearlyInput, '78000');
    await user.click(screen.getByRole('button', { name: 'CHF per month' }));

    expect(screen.getByLabelText('Gross salary per month')).toHaveValue(6500);
  });

  it('leaves the counterpart untouched when switching modes with an empty gross field', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);

    const grossInput = await screen.findByLabelText('Gross salary per month');
    await user.clear(grossInput);
    await user.click(screen.getByRole('button', { name: 'CHF per year' }));

    expect(screen.getByLabelText('Gross salary per year')).toHaveValue(69444);
  });

  it('submits the yearly gross with YEARLY mode and a derived monthly value', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SalaryTab />);
    await screen.findByLabelText('Gross salary per month');

    await user.click(screen.getByRole('button', { name: 'CHF per year' }));
    const yearlyInput = screen.getByLabelText('Gross salary per year');
    await user.clear(yearlyInput);
    await user.type(yearlyInput, '78000');
    await user.click(screen.getByRole('checkbox', { name: '13th month salary' }));
    await user.click(screen.getByRole('button', { name: 'Calculate & save' }));

    await waitFor(() =>
      expect(salaryApi.update).toHaveBeenCalledWith('test-token', {
        grossMonthly: 6000,
        grossYearly: 78000,
        inputMode: 'YEARLY',
        thirteenthSalary: true,
        ahvPct: 5.3,
        alvPct: 1.1,
        nbuPct: 0.455,
        ktgPct: 0.17,
        pensionFixed: 85.35,
        otherFixed: 11,
      }),
    );
    // The yearly amount includes the 13th salary — the hint makes that explicit.
    expect(
      screen.getByText('The yearly amount includes the 13th month salary.'),
    ).toBeInTheDocument();
  });

  it('applies the net salary to the monthly budget preserving the other fields', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<SalaryTab />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const applyButton = await screen.findByRole('button', {
      name: 'Apply net as monthly budget income',
    });
    await waitFor(() => expect(applyButton).toBeEnabled());
    await user.click(applyButton);

    await waitFor(() =>
      expect(budgetApi.updateMonthlyBudget).toHaveBeenCalledWith('test-token', {
        netIncome: 5284.1,
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
    expect(await screen.findByText('Applied')).toBeInTheDocument();
  });

  it('disables apply and hides the distribution bar for a zero gross salary', async () => {
    vi.mocked(salaryApi.get).mockResolvedValue(zeroGrossSalary());
    renderWithProviders(<SalaryTab />);

    const applyButton = await screen.findByRole('button', {
      name: 'Apply net as monthly budget income',
    });
    await waitFor(() => expect(budgetApi.getMonthlyBudget).toHaveBeenCalled());
    expect(applyButton).toBeDisabled();
    expect(screen.queryByText(/Social insurance/)).not.toBeInTheDocument();
  });

  it('shows an error message when the salary cannot be loaded', async () => {
    vi.mocked(salaryApi.get).mockRejectedValue(new Error('boom'));
    renderWithProviders(<SalaryTab />);

    expect(
      await screen.findByText('Could not load the salary calculator'),
    ).toBeInTheDocument();
  });
});
