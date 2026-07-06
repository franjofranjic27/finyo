import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pillar3 } from './Pillar3';
import { taxApi } from '@/api/tax';
import type { Pillar3Result } from '@/api/tax';
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
    { year: 1, balance: 5250, contribution: 5000, returns: 250, taxSaving: 1200 },
    { year: 2, balance: 10_762, contribution: 5000, returns: 512, taxSaving: 1200 },
  ],
};

describe('Pillar3', () => {
  it('renders the calculator form with default values', () => {
    renderWithProviders(<Pillar3 />);

    expect(screen.getAllByText('Pillar 3a Calculator').length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue('7258')).toBeInTheDocument();
    expect(screen.getByDisplayValue('30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Calculate/ })).toBeEnabled();
  });

  it('calculates and shows the projection metrics', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3 />);

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    expect(await screen.findByText("CHF 350'000.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 1'200.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 18'000.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 332'000.00")).toBeInTheDocument();
  });

  it('shows the contribution-cap hint when below the maximum', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3 />);

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
    renderWithProviders(<Pillar3 />);

    await user.click(screen.getByRole('button', { name: /Calculate/ }));

    await screen.findByText("CHF 350'000.00");
    expect(screen.queryByText(/CHF 7'258\.00/)).not.toBeInTheDocument();
  });

  it('sends the tax-saving inputs only when an income is entered', async () => {
    vi.mocked(taxApi.calculatePillar3).mockResolvedValue(result);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3 />);

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
});
