import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CalculatorCard } from './CalculatorCard';
import { pillar3Api } from '@/api/pillar3';
import { salaryApi } from '@/api/salary';
import { taxApi } from '@/api/tax';
import { pillar3Scenario, pillar3ScenarioInputs } from '@/test/fixtures/pillar3';
import { salary } from '@/test/fixtures/salary';
import { taxYearDetail, taxYearInputs } from '@/test/fixtures/tax';
import { renderWithProviders } from '@/test/test-utils';

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

vi.mock('@/api/tax', () => ({
  taxApi: {
    getCommunes: vi.fn(),
    upsertYear: vi.fn(),
  },
}));

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getScenarios: vi.fn(),
  },
}));

vi.mock('@/api/salary', () => ({
  salaryApi: {
    get: vi.fn(),
  },
}));

/** Salary without a stored gross — the default so most tests see no prefill. */
function salaryWithoutGross() {
  const stored = salary();
  return { ...stored, result: { ...stored.result, grossYearly: 0 } };
}

// vi.restoreAllMocks in the global afterEach wipes implementations, so the
// query defaults have to be re-established before every test.
beforeEach(() => {
  vi.mocked(pillar3Api.getScenarios).mockResolvedValue([]);
  vi.mocked(salaryApi.get).mockResolvedValue(salaryWithoutGross());
});

describe('CalculatorCard', () => {
  it('disables the calculate button without a gross income', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    expect(screen.getByRole('button', { name: /Calculate for 2025/ })).toBeDisabled();
    await waitFor(() =>
      expect(taxApi.getCommunes).toHaveBeenCalledWith('test-token', 'SG', 2025),
    );
  });

  it('prefills the form from the persisted inputs', () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    renderWithProviders(
      <CalculatorCard
        year={2025}
        inputs={taxYearInputs({ grossEmploymentIncome: 120_000, numberOfChildren: 2 })}
      />,
    );

    expect(screen.getByDisplayValue('120000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Calculate for 2025/ })).toBeEnabled();
  });

  it('saves the year inputs on calculate', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.upsertYear).mockResolvedValue(taxYearDetail({ year: 2025 }));
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    await user.type(screen.getByPlaceholderText('100000'), '90000');
    await user.click(screen.getByRole('button', { name: /Calculate for 2025/ }));

    await waitFor(() => expect(taxApi.upsertYear).toHaveBeenCalledTimes(1));
    const [token, year, payload] = vi.mocked(taxApi.upsertYear).mock.calls[0];
    expect(token).toBe('test-token');
    expect(year).toBe(2025);
    expect(payload).toMatchObject({
      cantonCode: 'SG',
      civilStatus: 'SINGLE',
      churchAffiliation: 'NONE',
      numberOfChildren: 0,
      grossEmploymentIncome: 90_000,
      bfsNumber: null,
    });
  });

  it('shows the backend error message when saving fails', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.upsertYear).mockRejectedValue(new Error('No rate data for 2025'));
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={taxYearInputs()} />);

    await user.click(screen.getByRole('button', { name: /Calculate for 2025/ }));

    expect(await screen.findByText('No rate data for 2025')).toBeInTheDocument();
  });

  it('shows the commune multipliers once a commune is selected', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([
      {
        id: 1,
        taxYear: 2025,
        cantonCode: 'SG',
        communeName: 'St. Gallen',
        bfsNumber: 3203,
        multiplier: 1.44,
        churchMultiplierProtestant: 0.25,
        churchMultiplierRomanCatholic: 0.26,
        cantonMultiplier: 1.05,
      },
    ]);
    renderWithProviders(
      <CalculatorCard year={2025} inputs={taxYearInputs({ bfsNumber: 3203 })} />,
    );

    expect(await screen.findByText(/144 %/)).toBeInTheDocument();
    expect(screen.getByText(/105 %/)).toBeInTheDocument();
    expect(screen.getByText('Tax Multiplier:')).toBeInTheDocument();
  });

  it('prefills the pillar 3a field from the default scenario with a hint', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([
      pillar3Scenario({
        id: 's1',
        name: 'Max 3a',
        isDefault: true,
        inputs: pillar3ScenarioInputs({ annualContribution: 7258 }),
      }),
    ]);
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    expect(await screen.findByDisplayValue('7258')).toBeInTheDocument();
    expect(screen.getByText('Prefilled from scenario “Max 3a”')).toBeInTheDocument();
  });

  it('prefills the gross income from the salary with a hint', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    expect(await screen.findByDisplayValue('69444')).toBeInTheDocument();
    expect(screen.getByText('Prefilled from your gross salary')).toBeInTheDocument();
  });

  it('sends untouched prefilled values in the calculate payload', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([
      pillar3Scenario({
        id: 's1',
        name: 'Max 3a',
        isDefault: true,
        inputs: pillar3ScenarioInputs({ annualContribution: 7258 }),
      }),
    ]);
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    vi.mocked(taxApi.upsertYear).mockResolvedValue(taxYearDetail({ year: 2025 }));
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    await screen.findByDisplayValue('7258');
    await user.click(screen.getByRole('button', { name: /Calculate for 2025/ }));

    await waitFor(() => expect(taxApi.upsertYear).toHaveBeenCalledTimes(1));
    const [, , payload] = vi.mocked(taxApi.upsertYear).mock.calls[0];
    expect(payload).toMatchObject({
      pillar3aContribution: 7258,
      grossEmploymentIncome: 69_444,
    });
  });

  it('never overwrites inputs persisted for the year', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([
      pillar3Scenario({
        id: 's1',
        name: 'Max 3a',
        isDefault: true,
        inputs: pillar3ScenarioInputs({ annualContribution: 7258 }),
      }),
    ]);
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    renderWithProviders(
      <CalculatorCard
        year={2025}
        inputs={taxYearInputs({ grossEmploymentIncome: 120_000, pillar3aContribution: 5000 })}
      />,
    );

    await waitFor(() => expect(pillar3Api.getScenarios).toHaveBeenCalled());
    await waitFor(() => expect(salaryApi.get).toHaveBeenCalled());

    expect(screen.getByDisplayValue('120000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('5000')).toBeInTheDocument();
    expect(screen.queryByText(/Prefilled from/)).not.toBeInTheDocument();
  });

  it('sets the pillar 3a field via the scenario picker', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([
      pillar3Scenario({
        id: 's1',
        name: 'Max 3a',
        isDefault: true,
        inputs: pillar3ScenarioInputs({ annualContribution: 7258 }),
      }),
      pillar3Scenario({
        id: 's2',
        name: 'Halber Beitrag',
        inputs: pillar3ScenarioInputs({ annualContribution: 3600 }),
      }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={taxYearInputs()} />);

    await user.click(
      await screen.findByRole('combobox', { name: 'Apply a 3a scenario …' }),
    );
    await user.click(await screen.findByRole('option', { name: /Halber Beitrag/ }));

    expect(screen.getByDisplayValue('3600')).toBeInTheDocument();
  });
});
