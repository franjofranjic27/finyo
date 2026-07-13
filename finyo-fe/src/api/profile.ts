import { apiRequest } from './client';

/** Stored theme preference (mirrors the backend enum). */
export type Theme = 'LIGHT' | 'DARK' | 'SYSTEM';

export type ProfileCivilStatus = 'SINGLE' | 'MARRIED' | 'SINGLE_PARENT';

export type ProfileChurchAffiliation = 'NONE' | 'PROTESTANT' | 'ROMAN_CATHOLIC';

export type PreferredLanguage = 'de' | 'en';

export interface UserProfile {
  birthDate: string | null;
  civilStatus: ProfileCivilStatus | null;
  churchAffiliation: ProfileChurchAffiliation | null;
  preferredLanguage: PreferredLanguage | null;
  theme: Theme;
  onboardingCompleted: boolean;
  /** Derived by the backend from birthDate; null without a birth date. */
  age: number | null;
  yearsToRetirement: number | null;
  retirementYear: number | null;
}

export interface UserProfileInput {
  birthDate?: string | null;
  civilStatus?: ProfileCivilStatus | null;
  churchAffiliation?: ProfileChurchAffiliation | null;
  preferredLanguage?: PreferredLanguage | null;
  theme?: Theme;
  /** null/omitted preserves the stored value. */
  onboardingCompleted?: boolean;
}

/** Query key for the shared `['profile']` cache entry. */
export const PROFILE_QUERY_KEY = ['profile'] as const;

export const profileApi = {
  get: (token: string) => apiRequest<UserProfile>('/profile', {}, token),

  update: (token: string, data: UserProfileInput) =>
    apiRequest<UserProfile>('/profile', { method: 'PUT', body: JSON.stringify(data) }, token),
};
