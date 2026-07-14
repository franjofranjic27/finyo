import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CalculatorCard } from './CalculatorCard';
import { pillar3Api } from '@/api/pillar3';
import { salaryApi } from '@/api/salary';
import { taxApi } from '@/api/tax';
import { wealthApi } from '@/api/wealth';
import { pillar3Scenario, pillar3ScenarioInputs } from '@/test/fixtures/pillar3';
import { salary } from '@/test/fixtures/salary';
import { taxScenario, taxYearDetail, taxYearInputs } from '@/test/fixtures/tax';
import { wealthBucket, wealthOverview } from '@/test/fixtures/wealth';
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
    updateScenario: vi.fn(),
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

vi.mock('@/api/wealth', () => ({
  wealthApi: {
    getOverview: vi.fn(),
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
  // Zero wealth — the default so most tests see no net-wealth prefill.
  vi.mocked(wealthApi.getOverview).mockResolvedValue(
    wealthOverview({ buckets: [], total: 0 }),
  );
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

  it('shows the default scenario in the 3a picker while it prefills the field', async () => {
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

    const picker = await screen.findByRole('combobox', { name: 'Apply a 3a scenario …' });
    await waitFor(() => expect(picker).toHaveTextContent('Max 3a'));
  });

  it('prefills the net wealth from the wealth overview without 3a buckets', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({
        total: 100_000,
        buckets: [
          wealthBucket({ id: 'b1', balance: 80_000 }),
          wealthBucket({ id: 'p3', source: 'PILLAR3', balance: 20_000 }),
        ],
      }),
    );
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    expect(await screen.findByDisplayValue('80000')).toBeInTheDocument();
    expect(
      screen.getByText('Taken from your wealth (excluding pillar 3a)'),
    ).toBeInTheDocument();
  });

  it('never overwrites a net wealth persisted for the year', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ total: 100_000, buckets: [wealthBucket({ id: 'b1' })] }),
    );
    renderWithProviders(
      <CalculatorCard year={2025} inputs={taxYearInputs({ netWealth: 55_000 })} />,
    );

    await waitFor(() => expect(wealthApi.getOverview).toHaveBeenCalled());

    expect(screen.getByDisplayValue('55000')).toBeInTheDocument();
    expect(
      screen.queryByText('Taken from your wealth (excluding pillar 3a)'),
    ).not.toBeInTheDocument();
  });

  it('drops the wealth prefill once the field is edited', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ total: 100_000, buckets: [wealthBucket({ id: 'b1' })] }),
    );
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    const netWealthInput = await screen.findByDisplayValue('100000');
    await user.clear(netWealthInput);

    expect(netWealthInput).toHaveValue(null);
    expect(
      screen.queryByText('Taken from your wealth (excluding pillar 3a)'),
    ).not.toBeInTheDocument();
  });

  it('sends the untouched wealth prefill in the calculate payload', async () => {
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({
        total: 100_000,
        buckets: [
          wealthBucket({ id: 'b1', balance: 80_000 }),
          wealthBucket({ id: 'p3', source: 'PILLAR3', balance: 20_000 }),
        ],
      }),
    );
    vi.mocked(taxApi.upsertYear).mockResolvedValue(taxYearDetail({ year: 2025 }));
    const user = userEvent.setup();
    renderWithProviders(<CalculatorCard year={2025} inputs={null} />);

    await screen.findByDisplayValue('80000');
    await user.type(screen.getByPlaceholderText('100000'), '90000');
    await user.click(screen.getByRole('button', { name: /Calculate for 2025/ }));

    await waitFor(() => expect(taxApi.upsertYear).toHaveBeenCalledTimes(1));
    const [, , payload] = vi.mocked(taxApi.upsertYear).mock.calls[0];
    expect(payload).toMatchObject({ netWealth: 80_000 });
  });

  describe('with a selected scenario', () => {
    const scenario = taxScenario({
      id: 'sc1',
      name: 'Married variant',
      inputs: taxYearInputs({
        grossEmploymentIncome: 110_000,
        selfEmploymentIncome: 5000,
      }),
    });

    it('renders the rename control and updates the scenario instead of the year', async () => {
      vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
      vi.mocked(taxApi.updateScenario).mockResolvedValue({ ...scenario });
      const user = userEvent.setup();
      renderWithProviders(
        <CalculatorCard year={2025} inputs={scenario.inputs} selectedScenario={scenario} />,
      );

      expect(screen.getByRole('button', { name: 'Rename scenario' })).toBeInTheDocument();

      const grossInput = screen.getByDisplayValue('110000');
      await user.clear(grossInput);
      await user.type(grossInput, '120000');
      await user.click(screen.getByRole('button', { name: 'Update scenario' }));

      await waitFor(() => expect(taxApi.updateScenario).toHaveBeenCalledTimes(1));
      const [token, year, scenarioId, payload] =
        vi.mocked(taxApi.updateScenario).mock.calls[0];
      expect(token).toBe('test-token');
      expect(year).toBe(2025);
      expect(scenarioId).toBe('sc1');
      // Form values override, stored-only fields survive, the name is kept.
      expect(payload).toMatchObject({
        name: 'Married variant',
        grossEmploymentIncome: 120_000,
        selfEmploymentIncome: 5000,
      });
      expect(taxApi.upsertYear).not.toHaveBeenCalled();
    });

    it('renames with the stored inputs, never the live form state', async () => {
      vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
      vi.mocked(taxApi.updateScenario).mockResolvedValue({ ...scenario, name: 'Renamed' });
      const user = userEvent.setup();
      renderWithProviders(
        <CalculatorCard year={2025} inputs={scenario.inputs} selectedScenario={scenario} />,
      );

      // Edit the form first — the rename must not pick this change up.
      const grossInput = screen.getByDisplayValue('110000');
      await user.clear(grossInput);
      await user.type(grossInput, '120000');

      await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
      const nameInput = screen.getByRole('textbox', { name: 'Rename scenario' });
      await user.clear(nameInput);
      await user.type(nameInput, 'Renamed');
      await user.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() =>
        expect(taxApi.updateScenario).toHaveBeenCalledWith('test-token', 2025, 'sc1', {
          ...scenario.inputs,
          name: 'Renamed',
        }),
      );
    });

    it('suppresses all derived prefills', async () => {
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
      vi.mocked(wealthApi.getOverview).mockResolvedValue(
        wealthOverview({ total: 100_000, buckets: [wealthBucket({ id: 'b1' })] }),
      );
      const emptyScenario = taxScenario({
        id: 'sc2',
        name: 'Blank',
        inputs: taxYearInputs({ grossEmploymentIncome: null }),
      });
      renderWithProviders(
        <CalculatorCard
          year={2025}
          inputs={emptyScenario.inputs}
          selectedScenario={emptyScenario}
        />,
      );

      await waitFor(() => expect(salaryApi.get).toHaveBeenCalled());
      await waitFor(() => expect(wealthApi.getOverview).toHaveBeenCalled());

      expect(screen.queryByText(/Prefilled from/)).not.toBeInTheDocument();
      expect(
        screen.queryByText('Taken from your wealth (excluding pillar 3a)'),
      ).not.toBeInTheDocument();
      expect(screen.queryByDisplayValue('69444')).not.toBeInTheDocument();
      expect(screen.queryByDisplayValue('7258')).not.toBeInTheDocument();
      expect(screen.queryByDisplayValue('100000')).not.toBeInTheDocument();
    });
  });
});
