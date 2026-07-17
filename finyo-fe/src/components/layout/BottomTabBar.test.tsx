import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BottomTabBar } from './BottomTabBar';
import { renderWithProviders } from '@/test/test-utils';

const authState = vi.hoisted(() => ({ isAdmin: false }));

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: authState.isAdmin ? ['user', 'admin'] : ['user'],
    hasRole: (role: string) => role === 'admin' && authState.isAdmin,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

beforeEach(() => {
  authState.isAdmin = false;
});

describe('BottomTabBar', () => {
  it('renders the four primary tabs and the more button', () => {
    renderWithProviders(<BottomTabBar />, { route: '/dashboard' });

    for (const label of ['Dashboard', 'Wealth', 'Budget', 'Taxes']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(screen.getByRole('button', { name: 'More' })).toBeInTheDocument();
  });

  it('opens the more sheet with the secondary destinations', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BottomTabBar />, { route: '/dashboard' });

    await user.click(screen.getByRole('button', { name: 'More' }));

    for (const label of [
      'Investments',
      'Pillar 3a',
      'Accounts',
      'Documents',
      'Insurance',
      'Settings',
    ]) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
    // No admin entry for regular users
    expect(screen.queryByRole('link', { name: '3a Products' })).not.toBeInTheDocument();
  });

  it('shows the admin entry in the more sheet for admins', async () => {
    authState.isAdmin = true;
    const user = userEvent.setup();
    renderWithProviders(<BottomTabBar />, { route: '/dashboard' });

    await user.click(screen.getByRole('button', { name: 'More' }));

    expect(screen.getByRole('link', { name: '3a Products' })).toBeInTheDocument();
  });

  it('highlights the more tab when a secondary route is active', () => {
    renderWithProviders(<BottomTabBar />, { route: '/investments' });

    expect(screen.getByRole('button', { name: 'More' })).toHaveClass('text-primary');
  });
});
