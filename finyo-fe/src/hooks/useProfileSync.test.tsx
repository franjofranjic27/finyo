import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { useProfileSync } from './useProfileSync';
import { useTheme } from './useTheme';
import { profileApi } from '@/api/profile';
import { renderWithProviders } from '@/test/test-utils';
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
  profileApi: { get: vi.fn(), update: vi.fn() },
}));

function SyncProbe() {
  useProfileSync();
  const { theme } = useTheme();
  return <div data-testid="theme-probe">{theme}</div>;
}

describe('useProfileSync', () => {
  beforeEach(() => {
    vi.mocked(profileApi.get).mockReset();
  });

  it('applies the persisted theme and language once the profile resolves', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(
      userProfile({ theme: 'DARK', preferredLanguage: 'de' }),
    );

    renderWithProviders(<SyncProbe />);

    // The document class lands in a passive effect, so it may trail the rendered
    // preference by a tick — both have to be awaited, not asserted afterwards.
    await waitFor(() => {
      expect(document.documentElement).toHaveClass('dark');
      expect(i18n.language).toBe('de');
    });
    expect(screen.getByTestId('theme-probe')).toHaveTextContent('dark');
  });

  it('keeps the current language when the profile has none stored', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(
      userProfile({ theme: 'LIGHT', preferredLanguage: null }),
    );

    renderWithProviders(<SyncProbe />);

    // The theme change proves the sync ran; the language must stay untouched.
    await waitFor(() => expect(screen.getByTestId('theme-probe')).toHaveTextContent('light'));
    expect(i18n.language).toBe('en');
  });
});
