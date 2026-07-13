import { useTranslation } from 'react-i18next';
import { LanguageToggle, ThemeToggle } from '@/components/profile/PreferenceToggles';
import { useTheme } from '@/hooks/useTheme';
import type { PreferredLanguage } from '@/api/profile';

/**
 * Language and theme selection. Both apply live — i18n and the ThemeProvider
 * are the single source of truth; the wizard reads them back on finish.
 */
export function StepPreferences() {
  const { t, i18n } = useTranslation();
  const { theme, setTheme } = useTheme();

  const language: PreferredLanguage = i18n.language === 'de' ? 'de' : 'en';

  return (
    <div className="space-y-4">
      <LanguageToggle value={language} onChange={(next) => void i18n.changeLanguage(next)} />
      <ThemeToggle value={theme} onChange={setTheme} />
      <p className="text-xs text-muted-foreground">{t('onboarding.preferences.hint')}</p>
    </div>
  );
}
