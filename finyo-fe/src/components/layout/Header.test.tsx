import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Header } from './Header';
import { profileApi } from '@/api/profile';
import { renderWithProviders } from '@/test/test-utils';
import { userProfile } from '@/test/fixtures/profile';
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

vi.mock('@/api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/profile')>()),
  profileApi: { get: vi.fn(), update: vi.fn(), updatePreferences: vi.fn() },
}));

describe('Header', () => {
  beforeEach(() => {
    vi.mocked(profileApi.update).mockReset();
    vi.mocked(profileApi.updatePreferences).mockReset();
    vi.mocked(profileApi.updatePreferences).mockResolvedValue(userProfile());
  });

  it('shows the finyo wordmark', () => {
    renderWithProviders(<Header onMenuClick={() => {}} />, { route: '/investments' });

    expect(screen.getByText('finyo')).toBeInTheDocument();
  });

  it('shows the user initials in the avatar', () => {
    renderWithProviders(<Header onMenuClick={() => {}} />);

    expect(screen.getByText('AN')).toBeInTheDocument();
  });

  it('switches the language with the EN/DE buttons and persists it as a partial patch', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={() => {}} />, { route: '/dashboard' });

    await user.click(screen.getByRole('button', { name: 'DE' }));
    expect(i18n.language).toBe('de');
    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', {
      preferredLanguage: 'de',
    });

    await user.click(screen.getByRole('button', { name: 'EN' }));
    expect(i18n.language).toBe('en');
    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', {
      preferredLanguage: 'en',
    });

    // the full-replace PUT would wipe the master data — it must not be used here
    expect(profileApi.update).not.toHaveBeenCalled();
  });

  it('toggles the theme between light and dark and persists it as a partial patch', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Header onMenuClick={() => {}} />);

    await user.click(screen.getByRole('button', { name: 'Dark Mode' }));

    expect(document.documentElement).toHaveClass('dark');
    expect(localStorage.getItem('finyo-theme')).toBe('dark');
    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', { theme: 'DARK' });
    expect(profileApi.update).not.toHaveBeenCalled();
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
    await user.click(await screen.findByRole('menuitem', { name: 'Log out' }));

    expect(logout).toHaveBeenCalledTimes(1);
  });
});
