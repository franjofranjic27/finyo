import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/auth/useAuth';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';
import { useTheme } from '@/hooks/useTheme';
import type { ThemePreference } from '@/hooks/useTheme';

/**
 * Applies the persisted profile preferences (theme + language) exactly once
 * after the profile query first resolves. Later local changes (Header toggles,
 * Settings) are not overridden.
 */
export function useProfileSync(): void {
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const { setTheme } = useTheme();
  const { i18n } = useTranslation();
  const applied = useRef(false);

  const { data: profile } = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: () => profileApi.get(token),
    enabled: !!token,
  });

  useEffect(() => {
    if (!profile || applied.current) return;
    applied.current = true;

    setTheme(profile.theme.toLowerCase() as ThemePreference);
    if (profile.preferredLanguage && profile.preferredLanguage !== i18n.language) {
      void i18n.changeLanguage(profile.preferredLanguage);
    }
  }, [profile, setTheme, i18n]);
}
