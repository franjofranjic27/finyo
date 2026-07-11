import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PerformanceChart } from './PerformanceChart';
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
    getHistory: vi.fn(),
  },
}));

const twoPoints = {
  points: [
    { date: '2026-07-09', totalValue: 1000, totalCost: 900 },
    { date: '2026-07-10', totalValue: 1100, totalCost: 900 },
  ],
};

describe('PerformanceChart', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.getHistory).mockResolvedValue(twoPoints);
  });

  it('renders the title and the range buttons and loads 12 months by default', async () => {
    renderWithProviders(<PerformanceChart />);

    expect(screen.getByText('Performance')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '6M' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1Y' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '3Y' })).toBeInTheDocument();
    await waitFor(() =>
      expect(portfolioApi.getHistory).toHaveBeenCalledWith('test-token', 12),
    );
  });

  it('requests the selected range when a range button is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PerformanceChart />);
    await waitFor(() => expect(portfolioApi.getHistory).toHaveBeenCalledWith('test-token', 12));

    await user.click(screen.getByRole('button', { name: '3Y' }));
    await waitFor(() =>
      expect(portfolioApi.getHistory).toHaveBeenCalledWith('test-token', 36),
    );

    await user.click(screen.getByRole('button', { name: '6M' }));
    await waitFor(() =>
      expect(portfolioApi.getHistory).toHaveBeenCalledWith('test-token', 6),
    );
  });

  it('shows a hint instead of the chart when there are fewer than two points', async () => {
    vi.mocked(portfolioApi.getHistory).mockResolvedValue({
      points: [{ date: '2026-07-10', totalValue: 1000, totalCost: 900 }],
    });
    renderWithProviders(<PerformanceChart />);

    expect(
      await screen.findByText('Not enough data yet — the history grows with every portfolio view'),
    ).toBeInTheDocument();
  });

  it('hides the hint when enough history points are available', async () => {
    renderWithProviders(<PerformanceChart />);
    await waitFor(() => expect(portfolioApi.getHistory).toHaveBeenCalled());

    await waitFor(() =>
      expect(
        screen.queryByText('Not enough data yet — the history grows with every portfolio view'),
      ).not.toBeInTheDocument(),
    );
  });
});
