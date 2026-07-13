import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CalculatorTab } from './CalculatorTab';
import { taxApi } from '@/api/tax';
import type { Pillar3Result } from '@/api/tax';
import { pillar3Api } from '@/api/pillar3';
import { profileApi } from '@/api/profile';
import type { UserProfile } from '@/api/profile';
import {
  pillar3Product,
  pillar3Result,
  pillar3Scenario,
  pillar3ScenarioInputs,
} from '@/test/fixtures/pillar3';
import { emptyUserProfile, userProfile } from '@/test/fixtures/profile';
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
    calculatePillar3: vi.fn(),
  },
}));

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getProducts: vi.fn(),
    getScenarios: vi.fn(),
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

vi.mock('@/api/profile', () => ({
  PROFILE_QUERY_KEY: ['profile'],
  profileApi: {
    get: vi.fn(),
  },
}));

const result: Pillar3Result = {
  annualContribution: 5000,
  maxContribution: 7258,
  isMaxContribution: false,
  remainingUntilMax: 2258,
  annualTaxSaving: 1200,
  taxSavingIfMaxContribution: 1750,
  projectedBalanceAtRetirement: 350_000,
  totalContributionsOverPeriod: 150_000,
  totalReturnsOverPeriod: 200_000,
  estimatedPayoutTax: 18_000,
  netValueAfterPayoutTax: 332_000,
  equivalentTaxableSavings: 300_000,
  yearlyProjection: [
    { year: 2027, balance: 5250, totalContributed: 5000, totalReturns: 250 },
    { year: 2028, balance: 10_762, totalContributed: 10_000, totalReturns: 762 },
  ],
};

const savedScenario = pillar3Scenario({
  id: 's1',
  name: 'My plan',
  effectiveReturnPercent: 4.2,
  inputs: pillar3ScenarioInputs({
    currentBalance: 20_000,
    annualContribution: 6000,
    yearsToRetirement: 25,
  }),
  calculation: pillar3Result({ projectedBalanceAtRetirement: 111_000 }),
});

// vi.restoreAllMocks in the global afterEach wipes implementations, so the
// query defaults have to be re-established before every test.
beforeEach(() => {
  vi.mocked(pillar3Api.getProducts).mockResolvedValue([]);
  vi.mocked(pillar3Api.getScenarios).mockResolvedValue([]);
  vi.mocked(profileApi.get).mockResolvedValue(emptyUserProfile());
});

describe('CalculatorTab', () => {
  it('renders the calculator form with default values', () => {
    renderWithProviders(<CalculatorTab />);

    expect(screen.getAllByText('Pillar 3a Calculator').length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue('7258')).toBeInTheDocument();
    expect(screen.getByDisplayValue('30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Calculate/ })).toBeEnabled();
  });

  it('calculates and shows the projection metrics', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    expect(await screen.findByText("CHF 350'000.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 1'200.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 18'000.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 332'000.00")).toBeInTheDocument();
    expect(screen.queryByText(/NaN/)).not.toBeInTheDocument();
  });

  it('shows the contribution-cap hint when below the maximum', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    expect(await screen.findByText(/CHF 2'258\.00/)).toBeInTheDocument();
    expect(screen.getByText(/CHF 7'258\.00/)).toBeInTheDocument();
  });

  it('hides the contribution-cap hint at the maximum contribution', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue({
      ...result,
      isMaxContribution: true,
      remainingUntilMax: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    await screen.findByText("CHF 350'000.00");
    expect(screen.queryByText(/CHF 7'258\.00/)).not.toBeInTheDocument();
  });

  it('sends the tax-saving inputs only when an income is entered', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.type(screen.getByPlaceholderText('Optional'), '100000');
    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    expect(taxApi.calculatePillar3).toHaveBeenCalledWith(
      'test-token',
      expect.objectContaining({
        grossEmploymentIncome: 100_000,
        civilStatus: 'SINGLE',
        cantonCode: 'SG',
        annualContribution: 7258,
        yearsToRetirement: 30,
      }),
    );
  });

  it('locks the return rate to the selected product and releases it on removal', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue([
      pillar3Product({ id: 'p1', name: 'Alpha Fund', netReturnPct: 3.2 }),
    ]);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.type(screen.getByRole('textbox', { name: 'Add product' }), 'Alpha');
    await user.click(await screen.findByRole('button', { name: /Alpha Fund/ }));

    const returnInput = screen.getByDisplayValue('3.2');
    expect(returnInput).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Remove product' }));

    expect(returnInput).toBeEnabled();
    expect(returnInput).toHaveValue(3.2);
  });

  it('loads a saved scenario into the form and shows its stored calculation', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([savedScenario]);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(await screen.findByRole('button', { name: /My plan/ }));

    expect(await screen.findByText("CHF 111'000.00")).toBeInTheDocument();
    expect(screen.getByDisplayValue('20000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('6000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('4.2')).toBeInTheDocument();
    expect(screen.getByDisplayValue('25')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scenario actions' })).toBeInTheDocument();
  });

  it('clears the scenario selection when a fresh calculation runs', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([savedScenario]);
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(await screen.findByRole('button', { name: /My plan/ }));
    await screen.findByText("CHF 111'000.00");

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    expect(await screen.findByText("CHF 350'000.00")).toBeInTheDocument();
    expect(screen.queryByText("CHF 111'000.00")).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /My plan/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('prefills the years to retirement from the profile with a hint', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile({ yearsToRetirement: 29 }));
    renderWithProviders(<CalculatorTab />);

    expect(await screen.findByDisplayValue('29')).toBeInTheDocument();
    expect(screen.getByText('Prefilled from your profile')).toBeInTheDocument();
  });

  it('sends the untouched profile prefill in the calculate payload', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile({ yearsToRetirement: 29 }));
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await screen.findByDisplayValue('29');
    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    await waitFor(() => expect(taxApi.calculatePillar3).toHaveBeenCalledTimes(1));
    const [, payload] = vi.mocked(taxApi.calculatePillar3).mock.calls[0];
    expect(payload).toMatchObject({ yearsToRetirement: 29 });
  });

  it('does not replace a user-typed years value with the profile', async () => {
    let resolveProfile!: (profile: UserProfile) => void;
    vi.mocked(profileApi.get).mockReturnValue(
      new Promise((resolve) => {
        resolveProfile = resolve;
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    const yearsInput = screen.getByDisplayValue('30');
    await user.clear(yearsInput);
    await user.type(yearsInput, '25');

    resolveProfile(userProfile({ yearsToRetirement: 29 }));

    await waitFor(() => expect(yearsInput).toHaveValue(25));
    expect(screen.queryByText('Prefilled from your profile')).not.toBeInTheDocument();
  });

  it('saves the current form state as a scenario via the inline panel', async () => {
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(savedScenario);
    const user = userEvent.setup();
    renderWithProviders(<CalculatorTab />);

    await user.click(screen.getByRole('button', { name: 'Save scenario' }));
    await user.type(screen.getByLabelText('Name'), 'My plan');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createScenario).toHaveBeenCalledWith('test-token', {
        currentBalance: 0,
        annualContribution: 7258,
        assumedAnnualReturnPercent: 5,
        yearsToRetirement: 30,
        grossEmploymentIncome: null,
        civilStatus: null,
        cantonCode: null,
        taxYear: new Date().getFullYear(),
        productId: null,
        name: 'My plan',
        isDefault: false,
      }),
    );
  });
});
