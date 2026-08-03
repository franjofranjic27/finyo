import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { useTheme } from '@/hooks/useTheme';
import type { ThemePreference } from '@/hooks/useTheme';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';
import type {
  PreferencesInput, PreferredLanguage, Salutation, Theme, UserProfile,
} from '@/api/profile';
import { CANTONS } from '@/lib/cantons';
import type { CantonCode } from '@/lib/cantons';
import { LanguageToggle, ThemeToggle } from '@/components/profile/PreferenceToggles';
import { ProfileBasicsFields } from '@/components/profile/ProfileBasicsFields';
import type { ProfileBasicsValues } from '@/components/profile/ProfileBasicsFields';

const SALUTATION_OPTIONS: { value: Salutation; labelKey: string }[] = [
  { value: 'NONE', labelKey: 'profile.salutation.none' },
  { value: 'MR', labelKey: 'profile.salutation.mr' },
  { value: 'MS', labelKey: 'profile.salutation.ms' },
];

/** The currencies offered as default; CHF is the backend fallback. */
const DEFAULT_CURRENCIES = ['CHF', 'EUR', 'USD'] as const;

const toThemeEnum = (theme: ThemePreference): Theme => theme.toUpperCase() as Theme;

export function ProfileTab() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const query = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: () => profileApi.get(token),
    enabled: !!token,
  });

  if (query.isLoading) {
    return <Skeleton className="h-72 w-full max-w-2xl" />;
  }

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('profile.loadError')}</p>;
  }

  if (!query.data) return null;

  return <ProfileForm profile={query.data} />;
}

interface ProfileFormValues extends ProfileBasicsValues {
  salutation: Salutation;
  firstName: string;
  lastName: string;
  nationality: string;
  street: string;
  postalCode: string;
  city: string;
  municipality: string;
  /** '' while no canton is selected. */
  cantonCode: CantonCode | '';
  phone: string;
}

function ProfileForm({ profile }: Readonly<{ profile: UserProfile }>) {
  const { t, i18n } = useTranslation();
  const { theme, setTheme } = useTheme();
  const { accessToken, user } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  // The email lives in Keycloak, not in the profile — display only.
  const email = user?.profile?.email ?? '';

  // Mounted only once data is available, so the form can seed from the server
  // state; the name falls back to the Keycloak token claims for new profiles.
  const [values, setValues] = useState<ProfileFormValues>({
    salutation: profile.salutation ?? 'NONE',
    firstName: profile.firstName ?? user?.profile?.given_name ?? '',
    lastName: profile.lastName ?? user?.profile?.family_name ?? '',
    birthDate: profile.birthDate ?? '',
    civilStatus: profile.civilStatus ?? 'SINGLE',
    churchAffiliation: profile.churchAffiliation ?? 'NONE',
    nationality: profile.nationality ?? '',
    street: profile.street ?? '',
    postalCode: profile.postalCode ?? '',
    city: profile.city ?? '',
    municipality: profile.municipality ?? '',
    cantonCode: profile.cantonCode ?? '',
    phone: profile.phone ?? '',
  });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!saved) return undefined;
    const timer = setTimeout(() => setSaved(false), 2000);
    return () => clearTimeout(timer);
  }, [saved]);

  const setField = (patch: Partial<ProfileFormValues>) =>
    setValues((prev) => ({ ...prev, ...patch }));

  // PUT replaces the whole profile — the preferences (language, theme,
  // default currency) must be resent from the (PATCH-updated) cache,
  // otherwise saving the master data would reset them.
  const save = useMutation({
    mutationFn: () =>
      profileApi.update(token, {
        salutation: values.salutation,
        firstName: values.firstName.trim() || null,
        lastName: values.lastName.trim() || null,
        birthDate: values.birthDate || null,
        civilStatus: values.civilStatus,
        churchAffiliation: values.churchAffiliation,
        nationality: values.nationality.trim() || null,
        street: values.street.trim() || null,
        postalCode: values.postalCode.trim() || null,
        city: values.city.trim() || null,
        municipality: values.municipality.trim() || null,
        cantonCode: values.cantonCode || null,
        phone: values.phone.trim() || null,
        preferredLanguage: profile.preferredLanguage,
        theme: profile.theme,
        defaultCurrency: profile.defaultCurrency,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
      setSaved(true);
    },
  });

  // Preferences apply immediately via PATCH (not PUT) — a full replace would
  // wipe every master-data field the caller does not resend.
  const persistPreference = useMutation({
    mutationFn: (input: PreferencesInput) => profileApi.updatePreferences(token, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
    },
  });

  const language: PreferredLanguage = i18n.language === 'de' ? 'de' : 'en';

  const changeLanguage = (next: PreferredLanguage) => {
    void i18n.changeLanguage(next);
    persistPreference.mutate({ preferredLanguage: next });
  };

  const changeTheme = (next: ThemePreference) => {
    setTheme(next);
    persistPreference.mutate({ theme: toThemeEnum(next) });
  };

  const changeCurrency = (next: string) => {
    persistPreference.mutate({ defaultCurrency: next });
  };

  const namesMissing = !values.firstName.trim() || !values.lastName.trim();

  return (
    <Card className="max-w-2xl">
      <CardContent className="space-y-6 pt-6">
        <section className="space-y-4">
          <h3 className="text-sm font-medium">{t('profile.sections.person')}</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="profile-salutation">{t('profile.salutation.label')}</Label>
              <Select
                value={values.salutation}
                onValueChange={(value) => setField({ salutation: value as Salutation })}
              >
                <SelectTrigger id="profile-salutation"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {SALUTATION_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {t(option.labelKey)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-firstName">{t('profile.firstName')}</Label>
              <Input
                id="profile-firstName"
                required
                value={values.firstName}
                onChange={(e) => setField({ firstName: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-lastName">{t('profile.lastName')}</Label>
              <Input
                id="profile-lastName"
                required
                value={values.lastName}
                onChange={(e) => setField({ lastName: e.target.value })}
              />
            </div>
            <ProfileBasicsFields
              className="contents"
              values={values}
              onChange={(patch) => setField(patch)}
            />
            <div className="space-y-2">
              <Label htmlFor="profile-nationality">{t('profile.nationality')}</Label>
              <Input
                id="profile-nationality"
                value={values.nationality}
                onChange={(e) => setField({ nationality: e.target.value })}
              />
            </div>
          </div>

          {/* Derived figures come from the backend response, not the form state. */}
          {profile.age != null && profile.yearsToRetirement != null && (
            <p className="text-sm text-muted-foreground">
              {t('profile.derivedInfo', {
                age: profile.age,
                years: profile.yearsToRetirement,
              })}
              {profile.retirementYear != null && (
                <> · {t('profile.retirementYear', { year: profile.retirementYear })}</>
              )}
            </p>
          )}
        </section>

        <Separator />

        <section className="space-y-4">
          <h3 className="text-sm font-medium">{t('profile.sections.address')}</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="profile-street">{t('profile.street')}</Label>
              <Input
                id="profile-street"
                value={values.street}
                onChange={(e) => setField({ street: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-[6rem_1fr] gap-4">
              <div className="space-y-2">
                <Label htmlFor="profile-postalCode">{t('profile.postalCode')}</Label>
                <Input
                  id="profile-postalCode"
                  value={values.postalCode}
                  onChange={(e) => setField({ postalCode: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="profile-city">{t('profile.city')}</Label>
                <Input
                  id="profile-city"
                  value={values.city}
                  onChange={(e) => setField({ city: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-municipality">{t('profile.municipality')}</Label>
              <Input
                id="profile-municipality"
                value={values.municipality}
                onChange={(e) => setField({ municipality: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-canton">{t('tax.canton')}</Label>
              <Select
                value={values.cantonCode || undefined}
                onValueChange={(value) => setField({ cantonCode: value as CantonCode })}
              >
                <SelectTrigger id="profile-canton"><SelectValue placeholder="—" /></SelectTrigger>
                <SelectContent>
                  {CANTONS.map((canton) => (
                    <SelectItem key={canton} value={canton}>{canton}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </section>

        <Separator />

        <section className="space-y-4">
          <h3 className="text-sm font-medium">{t('profile.sections.contact')}</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="profile-email">{t('profile.email')}</Label>
              <Input id="profile-email" value={email} readOnly disabled />
              <p className="text-xs text-muted-foreground">{t('profile.emailManagedHint')}</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-phone">{t('profile.phone')}</Label>
              <Input
                id="profile-phone"
                type="tel"
                value={values.phone}
                onChange={(e) => setField({ phone: e.target.value })}
              />
            </div>
          </div>
        </section>

        <Separator />

        <section className="space-y-4">
          <h3 className="text-sm font-medium">{t('profile.sections.preferences')}</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <LanguageToggle value={language} onChange={changeLanguage} />
            <ThemeToggle value={theme} onChange={changeTheme} />
            <div className="space-y-2">
              <Label htmlFor="profile-defaultCurrency">{t('profile.defaultCurrency')}</Label>
              <Select value={profile.defaultCurrency} onValueChange={changeCurrency}>
                <SelectTrigger id="profile-defaultCurrency"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {DEFAULT_CURRENCIES.map((currency) => (
                    <SelectItem key={currency} value={currency}>{currency}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </section>

        {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        {persistPreference.error && (
          <p className="text-sm text-destructive">{persistPreference.error.message}</p>
        )}

        <Button onClick={() => save.mutate()} disabled={save.isPending || namesMissing}>
          {saved ? (
            <>
              <Check className="mr-1 h-4 w-4 text-emerald-500" />
              {t('profile.saved')}
            </>
          ) : (
            t('common.save')
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
