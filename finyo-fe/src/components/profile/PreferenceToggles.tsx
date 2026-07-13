import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import type { PreferredLanguage } from '@/api/profile';
import type { ThemePreference } from '@/hooks/useTheme';

const LANGUAGES: readonly PreferredLanguage[] = ['en', 'de'];

const THEMES = [
  { value: 'light', labelKey: 'profile.theme.light' },
  { value: 'dark', labelKey: 'profile.theme.dark' },
  { value: 'system', labelKey: 'profile.theme.system' },
] as const;

interface LanguageToggleProps {
  value: PreferredLanguage;
  onChange: (language: PreferredLanguage) => void;
}

/** EN/DE button toggle — shared by onboarding and settings. */
export function LanguageToggle({ value, onChange }: Readonly<LanguageToggleProps>) {
  const { t } = useTranslation();

  return (
    <div className="space-y-2">
      <Label>{t('common.language')}</Label>
      <div className="flex gap-1">
        {LANGUAGES.map((language) => (
          <Button
            key={language}
            variant={value === language ? 'default' : 'outline'}
            size="sm"
            onClick={() => onChange(language)}
          >
            {language.toUpperCase()}
          </Button>
        ))}
      </div>
    </div>
  );
}

interface ThemeToggleProps {
  value: ThemePreference;
  onChange: (theme: ThemePreference) => void;
}

/** Light/Dark/System button toggle — shared by onboarding and settings. */
export function ThemeToggle({ value, onChange }: Readonly<ThemeToggleProps>) {
  const { t } = useTranslation();

  return (
    <div className="space-y-2">
      <Label>{t('profile.theme.label')}</Label>
      <div className="flex gap-1">
        {THEMES.map((theme) => (
          <Button
            key={theme.value}
            variant={value === theme.value ? 'default' : 'outline'}
            size="sm"
            onClick={() => onChange(theme.value)}
          >
            {t(theme.labelKey)}
          </Button>
        ))}
      </div>
    </div>
  );
}
