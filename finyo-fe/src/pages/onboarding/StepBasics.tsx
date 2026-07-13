import { useTranslation } from 'react-i18next';
import { ProfileBasicsFields } from '@/components/profile/ProfileBasicsFields';
import type { ProfileBasicsValues } from '@/components/profile/ProfileBasicsFields';
import { deriveAgeInfo } from '@/lib/retirement';

interface StepBasicsProps {
  values: ProfileBasicsValues;
  onChange: (patch: Partial<ProfileBasicsValues>) => void;
}

export function StepBasics({ values, onChange }: Readonly<StepBasicsProps>) {
  const { t } = useTranslation();
  const derived = deriveAgeInfo(values.birthDate);

  return (
    <div className="space-y-4">
      <ProfileBasicsFields values={values} onChange={onChange} />
      {derived && (
        <p className="text-sm text-muted-foreground">
          {t('profile.derivedInfo', { age: derived.age, years: derived.yearsToRetirement })}
        </p>
      )}
      <p className="text-xs text-muted-foreground">{t('onboarding.basics.hint')}</p>
    </div>
  );
}
