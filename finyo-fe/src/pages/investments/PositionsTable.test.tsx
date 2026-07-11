import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PositionsTable } from './PositionsTable';
import { portfolioApi } from '@/api/portfolio';
import { renderWithProviders } from '@/test/test-utils';
import { portfolioPosition } from '@/test/fixtures/portfolio';

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

vi.mock('@/api/portfolio', () => ({
  portfolioApi: {
    deletePosition: vi.fn(),
  },
}));

const positions = [
  portfolioPosition({
    id: 'p1',
    name: 'Nestlé SA',
    isin: 'CH0038863350',
    quantity: 10,
    purchasePrice: 90,
    currentPrice: 100,
    value: 1000,
    gainLoss: 100,
    returnPct: 11.1,
    allocationPct: 60,
  }),
  portfolioPosition({
    id: 'p2',
    name: 'Roche Holding AG',
    isin: 'CH0012032048',
    value: 666.67,
    gainLoss: -33.33,
    returnPct: -4.8,
    allocationPct: 40,
  }),
];

describe('PositionsTable', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.deletePosition).mockResolvedValue(undefined);
  });

  it('renders one row per position with formatted values', () => {
    renderWithProviders(<PositionsTable positions={positions} />);

    expect(screen.getByText('Positions')).toBeInTheDocument();
    expect(screen.getByText('Nestlé SA')).toBeInTheDocument();
    expect(screen.getByText('CH0038863350')).toBeInTheDocument();
    expect(screen.getByText("CHF 1'000.00")).toBeInTheDocument();
    expect(screen.getByText('60.0%')).toBeInTheDocument();
    expect(screen.getByText('Roche Holding AG')).toBeInTheDocument();
    expect(screen.getByText('CHF -33.33')).toBeInTheDocument();
  });

  it('shows the empty state when there are no positions', () => {
    renderWithProviders(<PositionsTable positions={[]} />);

    expect(
      screen.getByText('No positions yet — add your first security above'),
    ).toBeInTheDocument();
  });

  it('deletes a position after confirmation and invalidates the portfolio', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<PositionsTable positions={positions} />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getAllByRole('button', { name: 'Remove position' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove position "Nestlé SA"?');
    await waitFor(() =>
      expect(portfolioApi.deletePosition).toHaveBeenCalledWith('test-token', 'p1'),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] }),
    );
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<PositionsTable positions={positions} />);

    await user.click(screen.getAllByRole('button', { name: 'Remove position' })[0]);

    expect(portfolioApi.deletePosition).not.toHaveBeenCalled();
  });
});
