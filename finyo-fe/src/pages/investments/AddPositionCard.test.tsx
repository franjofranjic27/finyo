import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddPositionCard } from './AddPositionCard';
import { portfolioApi } from '@/api/portfolio';
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

vi.mock('@/api/portfolio', () => ({
  portfolioApi: {
    createPosition: vi.fn(),
    createPositionsBulk: vi.fn(),
  },
}));

describe('AddPositionCard', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.createPosition).mockResolvedValue({
      id: 'p1',
      instrumentId: 'i1',
      name: 'Nestlé SA',
      isin: 'CH0038863350',
      valor: null,
      quantity: 10,
      purchasePrice: 90,
      createdAt: '2026-07-10T12:00:00Z',
    });
  });

  it('disables submit until an identifier, quantity and purchase price are set', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AddPositionCard />);

    const submit = screen.getByRole('button', { name: /add/i });
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText('Name'), 'Nestlé SA');
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText('Quantity'), '10');
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
    expect(submit).toBeEnabled();
  });

  it('creates the position with parsed numbers and optional fields omitted', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AddPositionCard />);

    await user.type(screen.getByLabelText('ISIN'), 'CH0038863350');
    await user.type(screen.getByLabelText('Quantity'), '10');
    await user.type(screen.getByLabelText('Avg. Purchase Price'), '90.5');
    await user.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(portfolioApi.createPosition).toHaveBeenCalledWith('test-token', {
        name: undefined,
        isin: 'CH0038863350',
        valor: undefined,
        quantity: 10,
        purchasePrice: 90.5,
        currentPrice: undefined,
      }),
    );
  });

  it('sends the manual current price when provided', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AddPositionCard />);

    await user.type(screen.getByLabelText('Name'), 'Private Fund');
    await user.type(screen.getByLabelText('Quantity'), '5');
    await user.type(screen.getByLabelText('Avg. Purchase Price'), '100');
    await user.type(screen.getByLabelText('Current Price (optional)'), '110');
    await user.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(portfolioApi.createPosition).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({ name: 'Private Fund', currentPrice: 110 }),
      ),
    );
  });

  it('resets the form and invalidates the portfolio query on success', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<AddPositionCard />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Name'), 'Nestlé SA');
    await user.type(screen.getByLabelText('Quantity'), '10');
    await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
    await user.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] }),
    );
    expect(screen.getByLabelText('Name')).toHaveValue('');
    expect(screen.getByLabelText('Quantity')).toHaveValue(null);
    expect(screen.getByLabelText('Avg. Purchase Price')).toHaveValue(null);
  });

  it('shows the mutation error message when creating fails', async () => {
    vi.mocked(portfolioApi.createPosition).mockRejectedValue(new Error('quantity must be a positive number'));
    const user = userEvent.setup();
    renderWithProviders(<AddPositionCard />);

    await user.type(screen.getByLabelText('Name'), 'Nestlé SA');
    await user.type(screen.getByLabelText('Quantity'), '10');
    await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
    await user.click(screen.getByRole('button', { name: /add/i }));

    expect(await screen.findByText('quantity must be a positive number')).toBeInTheDocument();
  });
});
