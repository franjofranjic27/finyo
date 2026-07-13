import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { WealthSummaryCard } from './WealthSummaryCard';
import { wealthApi } from '@/api/wealth';
import { renderWithProviders } from '@/test/test-utils';
import { wealthOverview } from '@/test/fixtures/wealth';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({ accessToken: 'test-token' }),
}));

vi.mock('@/api/wealth', () => ({
  wealthApi: { getOverview: vi.fn() },
}));

describe('WealthSummaryCard', () => {
  it('shows a skeleton while the overview loads', () => {
    vi.mocked(wealthApi.getOverview).mockReturnValue(new Promise(() => {}));
    const { container } = renderWithProviders(<WealthSummaryCard />);

    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders the total, the YTD chip and one legend entry per bucket', async () => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(wealthOverview());
    renderWithProviders(<WealthSummaryCard />);

    expect(await screen.findByText("CHF 99'719.00")).toBeInTheDocument();
    expect(screen.getByText(/\+CHF 12'340\.00/)).toBeInTheDocument();
    expect(screen.getByText(/\+14\.1%/)).toBeInTheDocument();

    expect(screen.getByText('Sparen')).toBeInTheDocument();
    expect(screen.getByText('Wertschriftendepot')).toBeInTheDocument();
    expect(screen.getByText('Krypto')).toBeInTheDocument();
    expect(screen.getAllByTestId('donut-legend-item')).toHaveLength(3);
    expect(screen.getByText("CHF 77'000.00")).toBeInTheDocument();
    expect(screen.getByText('77.2%')).toBeInTheDocument();
    expect(wealthApi.getOverview).toHaveBeenCalledWith('test-token');
  });

  it('hides the YTD chip when there is no comparison data yet', async () => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ ytdChange: null, ytdChangePct: null }),
    );
    renderWithProviders(<WealthSummaryCard />);

    expect(await screen.findByText("CHF 99'719.00")).toBeInTheDocument();
    expect(screen.getAllByTestId('donut-legend-item')).toHaveLength(3);
    expect(screen.queryByText('YTD')).not.toBeInTheDocument();
  });

  it('links to the wealth page when no buckets exist', async () => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ buckets: [], total: 0, ytdChange: null, ytdChangePct: null }),
    );
    renderWithProviders(<WealthSummaryCard />);

    const cta = await screen.findByRole('link', { name: /Set up wealth/ });
    expect(cta).toHaveAttribute('href', '/wealth');
    expect(screen.getByText(/No wealth buckets yet/)).toBeInTheDocument();
  });

  it('shows an inline error when the overview fails', async () => {
    vi.mocked(wealthApi.getOverview).mockRejectedValue(new Error('boom'));
    renderWithProviders(<WealthSummaryCard />);

    expect(await screen.findByText('Could not load wealth overview')).toBeInTheDocument();
  });
});
