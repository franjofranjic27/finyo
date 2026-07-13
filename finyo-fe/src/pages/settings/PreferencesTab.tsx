import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { useAuth } from '@/auth/useAuth';
import { useTheme } from '@/hooks/useTheme';
import type { ThemePreference } from '@/hooks/useTheme';
import { profileApi } from '@/api/profile';
import type { PreferredLanguage, Theme, UserProfileInput } from '@/api/profile';
import { LanguageToggle, ThemeToggle } from '@/components/profile/PreferenceToggles';

const toThemeEnum = (theme: ThemePreference): Theme => theme.toUpperCase() as Theme;

export function PreferencesTab() {
  const { t, i18n } = useTranslation();
  const { theme, setTheme } = useTheme();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!saved) return undefined;
    const timer = setTimeout(() => setSaved(false), 2000);
    return () => clearTimeout(timer);
  }, [saved]);

  const persist = useMutation({
    mutationFn: (input: UserProfileInput) => profileApi.update(token, input),
    onSuccess: () => setSaved(true),
  });

  const language: PreferredLanguage = i18n.language === 'de' ? 'de' : 'en';

  const changeLanguage = (next: PreferredLanguage) => {
    void i18n.changeLanguage(next);
    persist.mutate({ preferredLanguage: next });
  };

  const changeTheme = (next: ThemePreference) => {
    setTheme(next);
    persist.mutate({ theme: toThemeEnum(next) });
  };

  return (
    <Card className="max-w-md">
      <CardContent className="space-y-6 pt-6">
        <LanguageToggle value={language} onChange={changeLanguage} />
        <ThemeToggle value={theme} onChange={changeTheme} />
        {saved && (
          <p className="flex items-center gap-1 text-sm text-muted-foreground">
            <Check className="h-4 w-4 text-emerald-500" />
            {t('profile.saved')}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
