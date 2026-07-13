import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';
import type { UserProfile } from '@/api/profile';
import { ProfileBasicsFields } from '@/components/profile/ProfileBasicsFields';
import type { ProfileBasicsValues } from '@/components/profile/ProfileBasicsFields';

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
    return <Skeleton className="h-72 w-full max-w-md" />;
  }

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('profile.loadError')}</p>;
  }

  if (!query.data) return null;

  return <ProfileForm profile={query.data} />;
}

function ProfileForm({ profile }: Readonly<{ profile: UserProfile }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  // Mounted only once data is available, so the form can seed from the server state.
  const [values, setValues] = useState<ProfileBasicsValues>({
    birthDate: profile.birthDate ?? '',
    civilStatus: profile.civilStatus ?? 'SINGLE',
    churchAffiliation: profile.churchAffiliation ?? 'NONE',
  });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!saved) return undefined;
    const timer = setTimeout(() => setSaved(false), 2000);
    return () => clearTimeout(timer);
  }, [saved]);

  const save = useMutation({
    mutationFn: () =>
      profileApi.update(token, {
        birthDate: values.birthDate || null,
        civilStatus: values.civilStatus,
        churchAffiliation: values.churchAffiliation,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
      setSaved(true);
    },
  });

  return (
    <Card className="max-w-md">
      <CardContent className="space-y-4 pt-6">
        <ProfileBasicsFields
          values={values}
          onChange={(patch) => setValues((prev) => ({ ...prev, ...patch }))}
        />

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

        {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}

        <Button onClick={() => save.mutate()} disabled={save.isPending}>
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
