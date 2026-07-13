import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { useAuth } from '@/auth/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';
import type { PreferredLanguage, Theme, UserProfile } from '@/api/profile';
import { salaryApi } from '@/api/salary';
import { StepBasics } from './StepBasics';
import { StepIncome } from './StepIncome';
import { StepPreferences } from './StepPreferences';
import type { IncomeValues } from './StepIncome';
import type { ProfileBasicsValues } from '@/components/profile/ProfileBasicsFields';

const STEPS = ['basics', 'income', 'preferences'] as const;

const roundToCents = (value: number) => Math.round(value * 100) / 100;

export function OnboardingPage() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { theme } = useTheme();

  const [step, setStep] = useState(0);
  const [basics, setBasics] = useState<ProfileBasicsValues>({
    birthDate: '',
    civilStatus: 'SINGLE',
    churchAffiliation: 'NONE',
  });
  const [income, setIncome] = useState<IncomeValues>({
    inputMode: 'MONTHLY',
    amount: '',
    thirteenthSalary: false,
  });

  // Language and theme are applied live in StepPreferences; read them back here.
  const language: PreferredLanguage = i18n.language === 'de' ? 'de' : 'en';

  const goToDashboard = (profile: UserProfile) => {
    // Seed the cache with the PUT response BEFORE navigating — otherwise the
    // OnboardingGate still sees the stale onboardingCompleted=false and loops.
    queryClient.setQueryData(PROFILE_QUERY_KEY, profile);
    void queryClient.invalidateQueries({ queryKey: PROFILE_QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: ['salary'] });
    navigate('/dashboard');
  };

  // PUT is a full replace — the live preferences must ride along, otherwise
  // skipping would reset a theme or language the user already picked.
  const skip = useMutation({
    mutationFn: () =>
      profileApi.update(token, {
        preferredLanguage: language,
        theme: theme.toUpperCase() as Theme,
        onboardingCompleted: true,
      }),
    onSuccess: goToDashboard,
  });

  const finish = useMutation({
    mutationFn: async () => {
      const profile = await profileApi.update(token, {
        birthDate: basics.birthDate || null,
        civilStatus: basics.civilStatus,
        churchAffiliation: basics.churchAffiliation,
        preferredLanguage: language,
        theme: theme.toUpperCase() as Theme,
        onboardingCompleted: true,
      });

      const amount = Number.parseFloat(income.amount) || 0;
      if (amount > 0) {
        // Preserve the stored deduction rates — the wizard only sets the gross.
        const current = await salaryApi.get(token);
        const monthsPerYear = income.thirteenthSalary ? 13 : 12;
        const isYearly = income.inputMode === 'YEARLY';
        await salaryApi.update(token, {
          grossMonthly: isYearly ? roundToCents(amount / monthsPerYear) : amount,
          grossYearly: isYearly ? amount : roundToCents(amount * monthsPerYear),
          inputMode: income.inputMode,
          thirteenthSalary: income.thirteenthSalary,
          ahvPct: current.ahvPct,
          alvPct: current.alvPct,
          nbuPct: current.nbuPct,
          ktgPct: current.ktgPct,
          pensionFixed: current.pensionFixed,
          otherFixed: current.otherFixed,
        });
      }

      return profile;
    },
    onSuccess: goToDashboard,
  });

  const pending = skip.isPending || finish.isPending;
  const error = skip.error ?? finish.error;
  const isLastStep = step === STEPS.length - 1;

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-8">
      <div className="w-full max-w-lg space-y-6">
        <div className="space-y-1 text-center">
          <p className="text-2xl font-bold tracking-tight">finyo</p>
          <h1 className="text-lg font-semibold">{t('onboarding.title')}</h1>
          <p className="text-sm text-muted-foreground">{t('onboarding.subtitle')}</p>
        </div>

        <Card>
          <CardHeader className="space-y-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">{t(`onboarding.steps.${STEPS[step]}`)}</CardTitle>
              <span className="text-xs text-muted-foreground">
                {t('onboarding.stepIndicator', { current: step + 1, total: STEPS.length })}
              </span>
            </div>
            <Progress value={((step + 1) / STEPS.length) * 100} />
          </CardHeader>
          <CardContent className="space-y-4">
            {step === 0 && (
              <StepBasics
                values={basics}
                onChange={(patch) => setBasics((prev) => ({ ...prev, ...patch }))}
              />
            )}
            {step === 1 && (
              <StepIncome
                values={income}
                onChange={(patch) => setIncome((prev) => ({ ...prev, ...patch }))}
              />
            )}
            {step === 2 && <StepPreferences />}

            {error && <p className="text-sm text-destructive">{error.message}</p>}

            <div className="flex items-center justify-between border-t border-border pt-4">
              <Button variant="ghost" size="sm" onClick={() => skip.mutate()} disabled={pending}>
                {t('onboarding.skip')}
              </Button>
              <div className="flex gap-2">
                {step > 0 && (
                  <Button
                    variant="outline"
                    onClick={() => setStep((current) => current - 1)}
                    disabled={pending}
                  >
                    {t('onboarding.back')}
                  </Button>
                )}
                {isLastStep ? (
                  <Button onClick={() => finish.mutate()} disabled={pending}>
                    {finish.isPending ? t('common.loading') : t('onboarding.finish')}
                  </Button>
                ) : (
                  <Button onClick={() => setStep((current) => current + 1)}>
                    {t('onboarding.next')}
                  </Button>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
