import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CalculatorCard } from './CalculatorCard';
import { taxApi } from '@/api/tax';
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
});
