import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import type { ProfileChurchAffiliation, ProfileCivilStatus } from '@/api/profile';

// Reuses the option labels of the tax calculator — same enums, same wording.
const CIVIL_STATUS_OPTIONS = [
  { value: 'SINGLE', labelKey: 'tax.civilStatus.single' },
  { value: 'MARRIED', labelKey: 'tax.civilStatus.married' },
  { value: 'SINGLE_PARENT', labelKey: 'tax.civilStatus.singleParent' },
] as const;

const CHURCH_OPTIONS = [
  { value: 'NONE', labelKey: 'tax.church.none' },
  { value: 'PROTESTANT', labelKey: 'tax.church.protestant' },
  { value: 'ROMAN_CATHOLIC', labelKey: 'tax.church.catholic' },
] as const;

export interface ProfileBasicsValues {
  /** ISO date (yyyy-mm-dd) or '' while empty. */
  birthDate: string;
  civilStatus: ProfileCivilStatus;
  churchAffiliation: ProfileChurchAffiliation;
}

interface ProfileBasicsFieldsProps {
  values: ProfileBasicsValues;
  onChange: (patch: Partial<ProfileBasicsValues>) => void;
}

/** Birth date, civil status and church affiliation — shared by onboarding and settings. */
export function ProfileBasicsFields({ values, onChange }: Readonly<ProfileBasicsFieldsProps>) {
  const { t } = useTranslation();

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="profile-birthDate">{t('profile.birthDate')}</Label>
        <Input
          id="profile-birthDate"
          type="date"
          value={values.birthDate}
          onChange={(e) => onChange({ birthDate: e.target.value })}
        />
      </div>
      <div className="space-y-2">
        <Label>{t('tax.civilStatus.label')}</Label>
        <Select
          value={values.civilStatus}
          onValueChange={(value) => onChange({ civilStatus: value as ProfileCivilStatus })}
        >
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {CIVIL_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {t(option.labelKey)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label>{t('tax.churchAffiliation')}</Label>
        <Select
          value={values.churchAffiliation}
          onValueChange={(value) => onChange({ churchAffiliation: value as ProfileChurchAffiliation })}
        >
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {CHURCH_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {t(option.labelKey)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
