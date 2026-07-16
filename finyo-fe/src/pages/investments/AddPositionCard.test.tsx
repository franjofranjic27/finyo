import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddPositionCard } from './AddPositionCard';
import { portfolioApi, type InstrumentLookup } from '@/api/portfolio';
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
    lookupInstrument: vi.fn(),
  },
}));

/** A valid, complete ISIN (2 letters + 9 alphanumerics + 1 check digit). */
const LISTED_ISIN = 'IE00B4L5Y983';

function lookup(overrides: Partial<InstrumentLookup> = {}): InstrumentLookup {
  return {
    status: 'NOT_FOUND',
    name: null,
    ticker: null,
    currency: null,
    assetClass: null,
    ...overrides,
  };
}

const FOUND = lookup({
  status: 'FOUND',
  name: 'iShares Core MSCI World',
  ticker: 'SWDA',
  currency: 'USD',
  assetClass: 'ETF',
});

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
    // Default: the lookup resolves to "not found" so an ISIN typed in the existing flows
    // leaves the form untouched (name stays empty, current-price field stays visible).
    vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(lookup());
  });

  describe('submit gating and payload', () => {
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
      vi.mocked(portfolioApi.createPosition).mockRejectedValue(
        new Error('quantity must be a positive number'),
      );
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('Name'), 'Nestlé SA');
      await user.type(screen.getByLabelText('Quantity'), '10');
      await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
      await user.click(screen.getByRole('button', { name: /add/i }));

      expect(
        await screen.findByText('quantity must be a positive number'),
      ).toBeInTheDocument();
    });
  });

  describe('live lookup', () => {
    it('looks up a complete ISIN by isin after the debounce, not a partial one', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      const isinField = screen.getByLabelText('ISIN');
      await user.type(isinField, 'IE00B4L5Y9'); // still incomplete
      expect(portfolioApi.lookupInstrument).not.toHaveBeenCalled();

      await user.type(isinField, '83'); // now a complete ISIN
      await waitFor(() =>
        expect(portfolioApi.lookupInstrument).toHaveBeenCalledWith('test-token', {
          isin: LISTED_ISIN,
        }),
      );
    });

    it('looks up a valor by valor', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('Valor'), '3886335');

      await waitFor(() =>
        expect(portfolioApi.lookupInstrument).toHaveBeenCalledWith('test-token', {
          valor: '3886335',
        }),
      );
    });

    it('does not look up when only a name is entered', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('Name'), 'Private Fund');
      // Give the debounce window room to elapse before asserting no request went out.
      await waitFor(() =>
        expect(screen.getByLabelText('Current Price (optional)')).toBeInTheDocument(),
      );
      expect(portfolioApi.lookupInstrument).not.toHaveBeenCalled();
    });

    it('previews a found security, auto-fills the name and hides the current-price field', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(FOUND);
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);

      expect(await screen.findByText('iShares Core MSCI World')).toBeInTheDocument();
      expect(screen.getByText(/ETF · USD/)).toBeInTheDocument();
      expect(screen.getByText(/price fetched automatically/i)).toBeInTheDocument();
      expect(
        screen.queryByLabelText('Current Price (optional)'),
      ).not.toBeInTheDocument();
      expect(screen.getByLabelText('Name')).toHaveValue('iShares Core MSCI World');
    });

    it('keeps a name the user typed even after a security is found', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(FOUND);
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);
      await screen.findByDisplayValue('iShares Core MSCI World');

      const nameField = screen.getByLabelText('Name');
      await user.clear(nameField);
      await user.type(nameField, 'My custom label');

      expect(nameField).toHaveValue('My custom label');
    });

    it('previews not-found and keeps the current-price field visible', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(
        lookup({ status: 'NOT_FOUND' }),
      );
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);

      expect(await screen.findByText(/no listed security/i)).toBeInTheDocument();
      expect(
        screen.getByLabelText('Current Price (optional)'),
      ).toBeInTheDocument();
    });

    it('previews an unavailable lookup and keeps the current-price field visible', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(
        lookup({ status: 'UNAVAILABLE' }),
      );
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);

      expect(
        await screen.findByText(/lookup is currently unavailable/i),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText('Current Price (optional)'),
      ).toBeInTheDocument();
    });
  });

  describe('submit interaction with the lookup', () => {
    it('omits the current price and keeps the isin when the security was found', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(FOUND);
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);
      await screen.findByText('iShares Core MSCI World');

      await user.type(screen.getByLabelText('Quantity'), '10');
      await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
      await user.click(screen.getByRole('button', { name: /add/i }));

      await waitFor(() =>
        expect(portfolioApi.createPosition).toHaveBeenCalledWith('test-token', {
          name: 'iShares Core MSCI World',
          isin: LISTED_ISIN,
          valor: undefined,
          quantity: 10,
          purchasePrice: 90,
          currentPrice: undefined,
        }),
      );
    });

    it('sends the manual current price when the security is not listed', async () => {
      vi.mocked(portfolioApi.lookupInstrument).mockResolvedValue(
        lookup({ status: 'NOT_FOUND' }),
      );
      const user = userEvent.setup();
      renderWithProviders(<AddPositionCard />);

      await user.type(screen.getByLabelText('ISIN'), LISTED_ISIN);
      await screen.findByText(/no listed security/i);

      await user.type(screen.getByLabelText('Quantity'), '10');
      await user.type(screen.getByLabelText('Avg. Purchase Price'), '90');
      await user.type(screen.getByLabelText('Current Price (optional)'), '110');
      await user.click(screen.getByRole('button', { name: /add/i }));

      await waitFor(() =>
        expect(portfolioApi.createPosition).toHaveBeenCalledWith('test-token', {
          name: undefined,
          isin: LISTED_ISIN,
          valor: undefined,
          quantity: 10,
          purchasePrice: 90,
          currentPrice: 110,
        }),
      );
    });
  });
});
