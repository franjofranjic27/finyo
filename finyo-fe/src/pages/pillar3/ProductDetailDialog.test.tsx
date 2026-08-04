import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductDetailDialog } from './ProductDetailDialog';
import { pillar3Api } from '@/api/pillar3';
import { pillar3Product } from '@/test/fixtures/pillar3';
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

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getProductPriceHistory: vi.fn(),
  },
}));

const product = pillar3Product({
  id: 'p1',
  name: 'Alpha Fund',
  provider: 'Alpha AG',
  isin: 'CH0011111116',
  valor: '1234567',
  equityPct: 75,
  terPct: 0.45,
  expectedReturnPct: 4.75,
  netReturnPct: 4.3,
});

beforeEach(() => {
  vi.mocked(pillar3Api.getProductPriceHistory).mockResolvedValue({
    isin: 'CH0011111116',
    currency: 'CHF',
    points: [
      { date: '2026-07-30', close: 101.5 },
      { date: '2026-07-31', close: 102.25 },
    ],
  });
});

describe('ProductDetailDialog', () => {
  it('shows the catalog data and requests the price history', async () => {
    renderWithProviders(<ProductDetailDialog product={product} onClose={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Alpha Fund' })).toBeInTheDocument();
    expect(screen.getByText('Alpha AG')).toBeInTheDocument();
    expect(screen.getByText('CH0011111116')).toBeInTheDocument();
    expect(screen.getByText('1234567')).toBeInTheDocument();
    expect(screen.getByText('75.0%')).toBeInTheDocument();
    expect(screen.getByText('0.45%')).toBeInTheDocument();
    expect(screen.getByText('4.8%')).toBeInTheDocument();
    expect(screen.getByText('4.3%')).toBeInTheDocument();
    expect(pillar3Api.getProductPriceHistory).toHaveBeenCalledWith('test-token', 'p1');
    expect(await screen.findByText('Price history')).toBeInTheDocument();
  });

  it('shows the empty state when the fund has no stored history', async () => {
    // Normal for unlisted 3a funds — and for listed ones right after the
    // first view triggered the background backfill.
    vi.mocked(pillar3Api.getProductPriceHistory).mockResolvedValue({
      isin: 'CH0011111116',
      currency: null,
      points: [],
    });
    renderWithProviders(<ProductDetailDialog product={product} onClose={vi.fn()} />);

    expect(await screen.findByText('No price data yet')).toBeInTheDocument();
    expect(document.querySelector('.recharts-responsive-container')).not.toBeInTheDocument();
  });

  it('omits the valor row when the product has none', () => {
    renderWithProviders(
      <ProductDetailDialog product={{ ...product, valor: null }} onClose={vi.fn()} />,
    );

    expect(screen.queryByText('Valor')).not.toBeInTheDocument();
  });

  it('closes via the dialog close control', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<ProductDetailDialog product={product} onClose={onClose} />);

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
