import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PreferencesTab } from './PreferencesTab';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';
import { renderWithProviders, createTestQueryClient } from '@/test/test-utils';
import { userProfile } from '@/test/fixtures/profile';
import i18n from '@/i18n';

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

vi.mock('@/api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/profile')>()),
  profileApi: { get: vi.fn(), update: vi.fn(), updatePreferences: vi.fn() },
}));

describe('PreferencesTab', () => {
  beforeEach(() => {
    vi.mocked(profileApi.update).mockReset();
    vi.mocked(profileApi.updatePreferences).mockReset();
    vi.mocked(profileApi.updatePreferences).mockResolvedValue(userProfile());
  });

  afterEach(async () => {
    // i18n is a global singleton — restore the default for the other specs.
    await i18n.changeLanguage('en');
  });

  it('persists a theme change as a partial patch, never as a full-replace PUT', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PreferencesTab />);

    await user.click(screen.getByRole('button', { name: 'Dark' }));

    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', { theme: 'DARK' });
    expect(profileApi.update).not.toHaveBeenCalled();
    expect(document.documentElement).toHaveClass('dark');
    expect(await screen.findByText('Saved')).toBeInTheDocument();
  });

  it('persists a language change as a partial patch', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PreferencesTab />);

    await user.click(screen.getByRole('button', { name: 'DE' }));

    expect(i18n.language).toBe('de');
    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', {
      preferredLanguage: 'de',
    });
    expect(profileApi.update).not.toHaveBeenCalled();
  });

  it('seeds the shared profile cache with the patched profile', async () => {
    const patched = userProfile({ theme: 'DARK' });
    vi.mocked(profileApi.updatePreferences).mockResolvedValue(patched);
    const queryClient = createTestQueryClient();
    const user = userEvent.setup();
    renderWithProviders(<PreferencesTab />, { queryClient });

    await user.click(screen.getByRole('button', { name: 'Dark' }));
    await screen.findByText('Saved');

    expect(queryClient.getQueryData(PROFILE_QUERY_KEY)).toEqual(patched);
  });
});
