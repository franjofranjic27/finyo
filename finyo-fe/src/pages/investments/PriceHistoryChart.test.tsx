import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { PriceHistoryChart } from './PriceHistoryChart';
import { formatPrice, formatTick } from './priceFormat';
import { positionDetailApi } from '@/api/positionDetail';
import { renderWithProviders } from '@/test/test-utils';
import { priceHistory } from '@/test/fixtures/portfolio';

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

vi.mock('@/api/positionDetail', () => ({
  positionDetailApi: {
    getPriceHistory: vi.fn(),
  },
}));

describe('PriceHistoryChart', () => {
  beforeEach(() => {
    vi.mocked(positionDetailApi.getPriceHistory).mockResolvedValue(priceHistory());
  });

  it('renders the card title and requests the history for the position', async () => {
    renderWithProviders(<PriceHistoryChart positionId="p1" />);

    expect(screen.getByText('Price history')).toBeInTheDocument();
    await waitFor(() =>
      expect(positionDetailApi.getPriceHistory).toHaveBeenCalledWith('p1', 'test-token'),
    );
  });

  it('shows a discreet empty state and no chart when there are no points', async () => {
    vi.mocked(positionDetailApi.getPriceHistory).mockResolvedValue(
      priceHistory({ points: [], isin: null, currency: null }),
    );
    renderWithProviders(<PriceHistoryChart positionId="p1" />);

    expect(await screen.findByText('No price data yet')).toBeInTheDocument();
    expect(document.querySelector('.recharts-responsive-container')).not.toBeInTheDocument();
  });

  it('shows a skeleton while the history is loading', () => {
    vi.mocked(positionDetailApi.getPriceHistory).mockReturnValue(new Promise(() => {}));
    const { container } = renderWithProviders(<PriceHistoryChart positionId="p1" />);

    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByText('No price data yet')).not.toBeInTheDocument();
  });

  it('formats a foreign-currency quote in its own currency, never as CHF', () => {
    const formatted = formatPrice(78.2, 'USD', 'en');

    expect(formatted).not.toMatch(/CHF/);
    expect(formatted).toMatch(/\$|US\$|USD/);
    expect(formatted).toContain('78.20');
  });

  it('formats a bare number without any currency when the currency is unknown', () => {
    const formatted = formatPrice(78.2, null, 'en');

    expect(formatted).not.toMatch(/CHF|\$|USD|€/);
    expect(formatted).toBe('78.20');
  });

  it('falls back to a bare number for an unknown currency code instead of throwing', () => {
    expect(() => formatPrice(78.2, 'XYZ_INVALID', 'en')).not.toThrow();
    expect(formatPrice(78.2, 'XYZ_INVALID', 'en')).toBe('78.20');
  });

  it('builds the axis tick from the date parts to avoid the UTC-midnight shift', () => {
    // '2023-01-15' must read as 15.1., not 14.1. as UTC parsing would yield west of UTC.
    expect(formatTick('2023-01-15')).toBe('15.1.');
  });
});
