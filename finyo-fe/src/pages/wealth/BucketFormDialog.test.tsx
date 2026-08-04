import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BucketFormDialog } from './BucketFormDialog';
import { wealthApi } from '@/api/wealth';
import { renderWithProviders } from '@/test/test-utils';
import { wealthBucket } from '@/test/fixtures/wealth';

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

vi.mock('@/api/wealth', () => ({
  wealthApi: {
    createBucket: vi.fn(),
    updateBucket: vi.fn(),
  },
}));

describe('BucketFormDialog', () => {
  it('shows only the manual fields — sources are no longer selectable', () => {
    renderWithProviders(<BucketFormDialog bucket={null} onClose={vi.fn()} />);

    expect(screen.getByLabelText('Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Note (e.g. bank/depot)')).toBeInTheDocument();
    expect(screen.getByLabelText('Balance')).toBeInTheDocument();
    expect(screen.getByLabelText('Monthly deposit')).toBeInTheDocument();
    // The source picker is gone: portfolio and pillar 3a rows appear automatically.
    expect(screen.queryByText('Source')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Maintained manually' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Asset classes')).not.toBeInTheDocument();
  });

  it('creates a manual pot with balance and monthly deposit', async () => {
    vi.mocked(wealthApi.createBucket).mockResolvedValue({
      id: 'b1',
      name: 'Cash',
      note: null,
      source: 'MANUAL',
      assetClasses: [],
      manualBalance: 5000,
      monthlyRate: 200,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<BucketFormDialog bucket={null} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Name'), 'Cash');
    await user.type(screen.getByLabelText('Balance'), '5000');
    await user.type(screen.getByLabelText('Monthly deposit'), '200');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(wealthApi.createBucket).toHaveBeenCalledWith('test-token', {
        name: 'Cash',
        source: 'MANUAL',
        manualBalance: 5000,
        monthlyRate: 200,
      }),
    );
  });

  it('prefills the fields from the edited pot and submits an update', async () => {
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
    renderWithProviders(
      <BucketFormDialog bucket={wealthBucket({ id: 'b1' })} onClose={vi.fn()} />,
    );

    expect(screen.getByLabelText('Name')).toHaveValue('Sparen');
    expect(screen.getByLabelText('Balance')).toHaveValue(4500);
    expect(screen.getByLabelText('Monthly deposit')).toHaveValue(500);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(wealthApi.updateBucket).toHaveBeenCalledWith('test-token', 'b1', {
        name: 'Sparen',
        note: 'Raiffeisen Sparkonto',
        source: 'MANUAL',
        manualBalance: 4500,
        monthlyRate: 500,
        sortOrder: 0,
      }),
    );
  });
});
