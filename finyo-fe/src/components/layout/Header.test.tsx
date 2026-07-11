import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Header } from './Header';
import { renderWithProviders } from '@/test/test-utils';
import i18n from '@/i18n';

const logout = vi.fn();

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: { preferred_username: 'anna', email: 'anna@example.ch' } },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: () => logout(),
  }),
}));

describe('Header', () => {
  it('shows the finyo wordmark', () => {
    renderWithProviders(<Header onMenuClick={() => {}} />, { route: '/investments' });

    expect(screen.getByText('finyo')).toBeInTheDocument();
  });

  it('shows the user initials in the avatar', () => {
    renderWithProviders(<Header onMenuClick={() => {}} />);

    expect(screen.getByText('AN')).toBeInTheDocument();
  });

  it('switches the language with the EN/DE buttons', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={() => {}} />, { route: '/dashboard' });

    await user.click(screen.getByRole('button', { name: 'DE' }));
    expect(i18n.language).toBe('de');

    await user.click(screen.getByRole('button', { name: 'EN' }));
    expect(i18n.language).toBe('en');
  });

  it('toggles the theme', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={() => {}} />);

    await user.click(screen.getByRole('button', { name: 'Dark Mode' }));

    expect(document.documentElement).toHaveClass('dark');
    expect(screen.getByRole('button', { name: 'Light Mode' })).toBeInTheDocument();
  });

  it('invokes the menu callback from the hamburger button', async () => {
    const onMenuClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={onMenuClick} />);

    await user.click(screen.getByRole('button', { name: 'Toggle menu' }));

    expect(onMenuClick).toHaveBeenCalledTimes(1);
  });

  it('logs out via the avatar dropdown', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={() => {}} />);

    await user.click(screen.getByText('AN'));
    await user.click(await screen.findByRole('menuitem', { name: /Logout/ }));

    expect(logout).toHaveBeenCalledTimes(1);
  });
});
