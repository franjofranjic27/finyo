import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { YearActionsMenu } from './YearActionsMenu';
import { taxApi } from '@/api/tax';
import { taxYearDetail } from '@/test/fixtures/tax';
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

vi.mock('@/api/tax', () => ({
  taxApi: {
    updateYearStatus: vi.fn(),
    deleteYear: vi.fn(),
  },
}));

describe('YearActionsMenu', () => {
  it('offers only the allowed forward transitions for an OPEN year', async () => {
    const user = userEvent.setup();
    renderWithProviders(<YearActionsMenu year={2024} status="OPEN" />);

    await user.click(screen.getByRole('button', { name: 'Change status' }));

    expect(await screen.findByRole('menuitem', { name: 'Filed' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Assessed' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Paid' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Open' })).not.toBeInTheDocument();
  });

  it('offers one step back and forward for a FILED year', async () => {
    const user = userEvent.setup();
    renderWithProviders(<YearActionsMenu year={2024} status="FILED" />);

    await user.click(screen.getByRole('button', { name: 'Change status' }));

    expect(await screen.findByRole('menuitem', { name: 'Open' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Assessed' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Paid' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Filed' })).not.toBeInTheDocument();
  });

  it('updates the status via the API', async () => {
    vi.mocked(taxApi.updateYearStatus).mockResolvedValue(
      taxYearDetail({ year: 2024, status: 'FILED' }),
    );
    const user = userEvent.setup();
    renderWithProviders(<YearActionsMenu year={2024} status="OPEN" />);

    await user.click(screen.getByRole('button', { name: 'Change status' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Filed' }));

    await waitFor(() =>
      expect(taxApi.updateYearStatus).toHaveBeenCalledWith('test-token', 2024, 'FILED'),
    );
  });

  it('deletes the year after confirmation', async () => {
    vi.mocked(taxApi.deleteYear).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<YearActionsMenu year={2024} status="OPEN" />);

    await user.click(screen.getByRole('button', { name: 'Change status' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete year/ }));

    await waitFor(() => expect(taxApi.deleteYear).toHaveBeenCalledWith('test-token', 2024));
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<YearActionsMenu year={2024} status="OPEN" />);

    await user.click(screen.getByRole('button', { name: 'Change status' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete year/ }));

    expect(taxApi.deleteYear).not.toHaveBeenCalled();
  });
});
