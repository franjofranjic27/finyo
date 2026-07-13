import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BucketFormDialog } from './BucketFormDialog';
import { wealthApi } from '@/api/wealth';
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

vi.mock('@/api/wealth', () => ({
  wealthApi: {
    createBucket: vi.fn(),
    updateBucket: vi.fn(),
  },
}));

describe('BucketFormDialog', () => {
  it('offers all three sources and shows the live hint for pillar 3a', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BucketFormDialog bucket={null} onClose={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Maintained manually' })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'From portfolio (asset classes)' }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Pillar 3a (default scenario)' }));

    expect(
      screen.getByText('The balance is taken live from your default pillar 3a scenario'),
    ).toBeInTheDocument();
    // no balance or asset-class inputs for a pillar 3a bucket
    expect(screen.queryByLabelText('Balance')).not.toBeInTheDocument();
    expect(screen.queryByText('Asset classes')).not.toBeInTheDocument();
  });

  it('sends neither manualBalance nor assetClasses for a pillar 3a bucket', async () => {
    vi.mocked(wealthApi.createBucket).mockResolvedValue({
      id: 'b1',
      name: 'Säule 3a',
      note: null,
      source: 'PILLAR3',
      assetClasses: [],
      manualBalance: null,
      monthlyRate: 0,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<BucketFormDialog bucket={null} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Name'), 'Säule 3a');
    await user.click(screen.getByRole('button', { name: 'Pillar 3a (default scenario)' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() => expect(wealthApi.createBucket).toHaveBeenCalledTimes(1));
    const [token, payload] = vi.mocked(wealthApi.createBucket).mock.calls[0];
    expect(token).toBe('test-token');
    expect(payload).toMatchObject({ name: 'Säule 3a', source: 'PILLAR3' });
    expect(payload.manualBalance).toBeUndefined();
    expect(payload.assetClasses).toBeUndefined();
  });

  it('keeps the manual balance in the payload for a manual bucket', async () => {
    vi.mocked(wealthApi.createBucket).mockResolvedValue({
      id: 'b1',
      name: 'Cash',
      note: null,
      source: 'MANUAL',
      assetClasses: [],
      manualBalance: 5000,
      monthlyRate: 0,
      sortOrder: 0,
    });
    const user = userEvent.setup();
    renderWithProviders(<BucketFormDialog bucket={null} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Name'), 'Cash');
    await user.type(screen.getByLabelText('Balance'), '5000');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(wealthApi.createBucket).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({ name: 'Cash', source: 'MANUAL', manualBalance: 5000 }),
      ),
    );
  });
});
