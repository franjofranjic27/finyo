import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WealthPage } from './WealthPage';
import { wealthApi } from '@/api/wealth';
import { renderWithProviders } from '@/test/test-utils';
import { wealthHistory, wealthOverview } from '@/test/fixtures/wealth';

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

vi.mock('@/api/wealth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/wealth')>();
  return {
    ...actual,
    wealthApi: {
      getOverview: vi.fn(),
      getHistory: vi.fn(),
      createBucket: vi.fn(),
      updateBucket: vi.fn(),
      deleteBucket: vi.fn(),
    },
  };
});

describe('WealthPage', () => {
  beforeEach(() => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(wealthOverview());
    vi.mocked(wealthApi.getHistory).mockResolvedValue(wealthHistory());
  });

  it('renders the KPI tiles, bucket rows and chart from the overview query', async () => {
    renderWithProviders(<WealthPage />);

    // Total appears in the KPI tile and in the table footer row.
    expect(await screen.findAllByText("CHF 99'719.00")).toHaveLength(2);
    expect(screen.getByText('Total wealth')).toBeInTheDocument();
    expect(screen.getByText("+CHF 12'340.00")).toBeInTheDocument();
    expect(screen.getByText('Monthly savings rate')).toBeInTheDocument();

    expect(screen.getByText('Sparen')).toBeInTheDocument();
    expect(screen.getByText('Wertschriftendepot')).toBeInTheDocument();
    expect(screen.getByText('Krypto')).toBeInTheDocument();
    expect(screen.getAllByText('Portfolio')).toHaveLength(2);
    expect(screen.getByText('manual')).toBeInTheDocument();
    // Krypto has no monthly rate → dash instead of CHF 0.00.
    expect(screen.getByText('—')).toBeInTheDocument();

    expect(screen.getByText('History & forecast')).toBeInTheDocument();
    expect(wealthApi.getOverview).toHaveBeenCalledWith('test-token');
    expect(wealthApi.getHistory).toHaveBeenCalledWith('test-token', 12);
  });

  it('shows the empty states for buckets and chart', async () => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(wealthOverview({ buckets: [] }));
    vi.mocked(wealthApi.getHistory).mockResolvedValue({ points: [] });
    renderWithProviders(<WealthPage />);

    expect(
      await screen.findByText('No buckets yet — create your first wealth bucket'),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(
        'Not enough data yet — the history grows with every visit of this overview',
      ),
    ).toBeInTheDocument();
  });

  it('shows an error message when the overview cannot be loaded', async () => {
    vi.mocked(wealthApi.getOverview).mockRejectedValue(new Error('boom'));
    renderWithProviders(<WealthPage />);

    expect(await screen.findByText('Could not load wealth overview')).toBeInTheDocument();
  });

  it('shows the YTD placeholder when there is no comparison data yet', async () => {
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ ytdChange: null, ytdChangePct: null }),
    );
    renderWithProviders(<WealthPage />);

    expect(await screen.findByText('no comparison data yet')).toBeInTheDocument();
  });

  it('creates a manual bucket through the dialog', async () => {
    vi.mocked(wealthApi.createBucket).mockResolvedValue({
      id: 'new',
      name: 'Notgroschen',
      note: null,
      source: 'MANUAL',
      assetClasses: [],
      manualBalance: 2500,
      monthlyRate: 200,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<WealthPage />);
    await screen.findByText('Sparen');

    await user.click(screen.getByRole('button', { name: 'Add bucket' }));
    await user.type(screen.getByLabelText('Name'), 'Notgroschen');
    await user.type(screen.getByLabelText('Balance'), '2500');
    await user.type(screen.getByLabelText('Monthly deposit'), '200');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    expect(wealthApi.createBucket).toHaveBeenCalledWith('test-token', {
      name: 'Notgroschen',
      source: 'MANUAL',
      manualBalance: 2500,
      monthlyRate: 200,
    });
  });

  it('toggles the source-specific fields and submits asset classes for portfolio buckets', async () => {
    vi.mocked(wealthApi.createBucket).mockResolvedValue({
      id: 'new',
      name: 'Depot',
      note: null,
      source: 'PORTFOLIO',
      assetClasses: ['ETF'],
      manualBalance: null,
      monthlyRate: 0,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<WealthPage />);
    await screen.findByText('Sparen');

    await user.click(screen.getByRole('button', { name: 'Add bucket' }));
    // MANUAL is the default: balance visible, asset classes hidden.
    expect(screen.getByLabelText('Balance')).toBeInTheDocument();
    expect(screen.queryByText('Asset classes')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'From portfolio (asset classes)' }));
    expect(screen.queryByLabelText('Balance')).not.toBeInTheDocument();
    expect(screen.getByText('Asset classes')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Name'), 'Depot');
    await user.click(screen.getByLabelText('ETF'));
    await user.click(screen.getByRole('button', { name: 'Add' }));

    expect(wealthApi.createBucket).toHaveBeenCalledWith('test-token', {
      name: 'Depot',
      source: 'PORTFOLIO',
      assetClasses: ['ETF'],
    });
  });

  it('opens the edit dialog prefilled and updates the bucket', async () => {
    vi.mocked(wealthApi.updateBucket).mockResolvedValue({
      id: 'b1',
      name: 'Sparen',
      note: 'Raiffeisen Sparkonto',
      source: 'MANUAL',
      assetClasses: [],
      manualBalance: 4500,
      monthlyRate: 500,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<WealthPage />);
    await screen.findByText('Sparen');

    await user.click(screen.getAllByRole('button', { name: 'Edit' })[0]);
    expect(screen.getByText('Edit bucket')).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveValue('Sparen');
    expect(screen.getByLabelText('Balance')).toHaveValue(4500);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(wealthApi.updateBucket).toHaveBeenCalledWith('test-token', 'b1', {
      name: 'Sparen',
      note: 'Raiffeisen Sparkonto',
      source: 'MANUAL',
      manualBalance: 4500,
      monthlyRate: 500,
      sortOrder: 0,
    });
  });

  it('deletes a bucket after confirmation', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    vi.mocked(wealthApi.deleteBucket).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithProviders(<WealthPage />);
    await screen.findByText('Sparen');

    await user.click(screen.getAllByRole('button', { name: 'Remove bucket' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove bucket “Sparen”?');
    await waitFor(() =>
      expect(wealthApi.deleteBucket).toHaveBeenCalledWith('test-token', 'b1'),
    );
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<WealthPage />);
    await screen.findByText('Sparen');

    await user.click(screen.getAllByRole('button', { name: 'Remove bucket' })[0]);

    expect(wealthApi.deleteBucket).not.toHaveBeenCalled();
  });
});
