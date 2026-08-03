import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { PortfolioPage } from './PortfolioPage';
import { portfolioApi } from '@/api/portfolio';
import { renderWithProviders } from '@/test/test-utils';
import { portfolio, portfolioPosition } from '@/test/fixtures/portfolio';

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

vi.mock('@/api/portfolio', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/portfolio')>();
  return {
    ...actual,
    portfolioApi: {
      getPortfolio: vi.fn(),
      getHistory: vi.fn(),
      createPosition: vi.fn(),
      createPositionsBulk: vi.fn(),
      deletePosition: vi.fn(),
    },
  };
});

describe('PortfolioPage', () => {
  it('renders the title, KPI tiles, positions and charts from the portfolio query', async () => {
    vi.mocked(portfolioApi.getHistory).mockResolvedValue({ points: [] });
    vi.mocked(portfolioApi.getPortfolio).mockResolvedValue(
      portfolio({
        positions: [portfolioPosition({ id: 'p1', name: 'Nestlé SA' })],
        totalValue: 1234.5,
        totalCost: 900,
      }),
    );
    renderWithProviders(<PortfolioPage />);

    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeInTheDocument();
    expect(await screen.findByText("CHF 1'234.50")).toBeInTheDocument();
    expect(screen.getByText('Total Value')).toBeInTheDocument();
    // Appears in the grouped positions table and in the allocation legend.
    expect(screen.getAllByText('Nestlé SA').length).toBeGreaterThan(0);
    expect(screen.getByText('Add Security')).toBeInTheDocument();
    expect(screen.getByText('Allocation')).toBeInTheDocument();
    expect(screen.getByText('Performance')).toBeInTheDocument();
    expect(portfolioApi.getPortfolio).toHaveBeenCalledWith('test-token');

    // Section order: KPI tiles, charts, add-position card, positions table.
    const charts = screen.getByText('Allocation');
    const addCard = screen.getByText('Add Security');
    expect(
      charts.compareDocumentPosition(addCard) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('shows an error message when the portfolio cannot be loaded', async () => {
    vi.mocked(portfolioApi.getPortfolio).mockRejectedValue(new Error('boom'));
    renderWithProviders(<PortfolioPage />);

    expect(await screen.findByText('Failed to load the portfolio')).toBeInTheDocument();
  });
});
