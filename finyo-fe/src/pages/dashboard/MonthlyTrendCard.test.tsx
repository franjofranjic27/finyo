import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { MonthlyTrendCard } from './MonthlyTrendCard';
import { analyticsApi } from '@/api/analytics';
import { renderWithProviders } from '@/test/test-utils';
import type { MonthlyDataPoint } from '@/types';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({ accessToken: 'test-token' }),
}));

vi.mock('@/api/analytics', () => ({
  analyticsApi: { getMonthlyTrend: vi.fn() },
}));

const trend: MonthlyDataPoint[] = [
  { year: 2026, month: 6, expenses: '-2400', income: '8000', net: '5600' },
  { year: 2026, month: 7, expenses: '-2500', income: '8000', net: '5500' },
];

describe('MonthlyTrendCard', () => {
  beforeEach(() => {
    vi.mocked(analyticsApi.getMonthlyTrend).mockResolvedValue(trend);
  });

  it('shows a skeleton while the trend loads', () => {
    vi.mocked(analyticsApi.getMonthlyTrend).mockReturnValue(new Promise(() => {}));
    const { container } = renderWithProviders(<MonthlyTrendCard />);

    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders the bar chart for the last six months', async () => {
    const { container } = renderWithProviders(<MonthlyTrendCard />);

    expect(await screen.findByText('Monthly Overview')).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(container.querySelector('.recharts-responsive-container')).toBeInTheDocument(),
    );
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(analyticsApi.getMonthlyTrend).toHaveBeenCalledWith('test-token', 'LAST_6_MONTHS');
  });

  it('links to the month view when every month is empty', async () => {
    vi.mocked(analyticsApi.getMonthlyTrend).mockResolvedValue([
      { year: 2026, month: 7, expenses: '0', income: '0', net: '0' },
    ]);
    renderWithProviders(<MonthlyTrendCard />);

    const cta = await screen.findByRole('link', { name: /Import bank statement/ });
    expect(cta).toHaveAttribute('href', '/budget?tab=month');
  });

  it('shows an inline error when the trend fails', async () => {
    vi.mocked(analyticsApi.getMonthlyTrend).mockRejectedValue(new Error('boom'));
    renderWithProviders(<MonthlyTrendCard />);

    expect(await screen.findByText('Could not load the month view')).toBeInTheDocument();
  });
});
