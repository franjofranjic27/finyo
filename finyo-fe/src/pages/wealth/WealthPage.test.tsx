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

    // Total appears in the KPI tile, the mobile total line and the table footer row.
    expect(await screen.findAllByText("CHF 99'719.00")).toHaveLength(3);
    expect(screen.getByText('Total wealth')).toBeInTheDocument();
    expect(screen.getByText("+CHF 12'340.00")).toBeInTheDocument();
    expect(screen.getByText('Monthly savings rate')).toBeInTheDocument();

    // Rows render twice: once as mobile list rows, once as the desktop table.
    expect(screen.getAllByText('Sparen')).toHaveLength(2);
    // Auto rows use the localized name (2×) next to the source badge (2×).
    expect(screen.getAllByText('Portfolio')).toHaveLength(4);
    expect(screen.getAllByText('Pillar 3a')).toHaveLength(4);
    expect(screen.getAllByText('Automatic')).toHaveLength(4);
    expect(screen.getAllByText('manual')).toHaveLength(2);
    // The portfolio row has no derivable deposit → dash (list row + table row).
    expect(screen.getAllByText('—')).toHaveLength(2);

    expect(screen.getByText('History & forecast')).toBeInTheDocument();
    expect(wealthApi.getOverview).toHaveBeenCalledWith('test-token');
    expect(wealthApi.getHistory).toHaveBeenCalledWith('test-token', 12);
  });

  it('renders auto rows read-only — edit and delete exist only for manual pots', async () => {
    renderWithProviders(<WealthPage />);
    await screen.findAllByText('Sparen');

    // One manual bucket rendered twice (mobile + table) → exactly two of each action.
    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Remove bucket' })).toHaveLength(2);
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

  it('does not show the bucket empty state when only auto rows exist', async () => {
    const overview = wealthOverview();
    vi.mocked(wealthApi.getOverview).mockResolvedValue(
      wealthOverview({ buckets: overview.buckets.filter((bucket) => bucket.auto) }),
    );
    renderWithProviders(<WealthPage />);

    expect(await screen.findAllByText('Portfolio')).toHaveLength(4);
    expect(
      screen.queryByText('No buckets yet — create your first wealth bucket'),
    ).not.toBeInTheDocument();
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
    await screen.findAllByText('Sparen');

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
    await screen.findAllByText('Sparen');

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
    await screen.findAllByText('Sparen');

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
    await screen.findAllByText('Sparen');

    await user.click(screen.getAllByRole('button', { name: 'Remove bucket' })[0]);

    expect(wealthApi.deleteBucket).not.toHaveBeenCalled();
  });
});
