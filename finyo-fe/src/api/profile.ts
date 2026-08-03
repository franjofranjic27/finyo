import { apiRequest } from './client';
import type { CantonCode } from '@/lib/cantons';

/** Stored theme preference (mirrors the backend enum). */
export type Theme = 'LIGHT' | 'DARK' | 'SYSTEM';

export type ProfileCivilStatus = 'SINGLE' | 'MARRIED' | 'SINGLE_PARENT';

export type ProfileChurchAffiliation = 'NONE' | 'PROTESTANT' | 'ROMAN_CATHOLIC';

export type PreferredLanguage = 'de' | 'en';

/** Form of address (mirrors the backend enum); NONE is an explicit "no salutation". */
export type Salutation = 'NONE' | 'MR' | 'MS';

export interface UserProfile {
  salutation: Salutation | null;
  firstName: string | null;
  lastName: string | null;
  birthDate: string | null;
  civilStatus: ProfileCivilStatus | null;
  churchAffiliation: ProfileChurchAffiliation | null;
  nationality: string | null;
  street: string | null;
  postalCode: string | null;
  city: string | null;
  municipality: string | null;
  cantonCode: CantonCode | null;
  phone: string | null;
  preferredLanguage: PreferredLanguage | null;
  theme: Theme;
  /** ISO 4217 code; never null — the backend defaults it to CHF. */
  defaultCurrency: string;
  onboardingCompleted: boolean;
  /** Derived by the backend from birthDate; null without a birth date. */
  age: number | null;
  yearsToRetirement: number | null;
  retirementYear: number | null;
}

/**
 * PUT payload — full replace of the master data: every omitted field is
 * cleared on the server. Only for the onboarding wizard and the profile form,
 * which send the complete field set. Single toggles go through
 * {@link PreferencesInput}.
 */
export interface UserProfileInput {
  salutation?: Salutation | null;
  firstName?: string | null;
  lastName?: string | null;
  birthDate?: string | null;
  civilStatus?: ProfileCivilStatus | null;
  churchAffiliation?: ProfileChurchAffiliation | null;
  nationality?: string | null;
  street?: string | null;
  postalCode?: string | null;
  city?: string | null;
  municipality?: string | null;
  cantonCode?: CantonCode | null;
  phone?: string | null;
  preferredLanguage?: PreferredLanguage | null;
  theme?: Theme;
  /** null/omitted falls back to CHF on the server. */
  defaultCurrency?: string | null;
  /** null/omitted preserves the stored value. */
  onboardingCompleted?: boolean;
}

/** PATCH payload — only the given preference is applied, master data is untouched. */
export interface PreferencesInput {
  theme?: Theme;
  preferredLanguage?: PreferredLanguage;
  defaultCurrency?: string;
}

/**
 * Maps a stored profile to a full PUT payload. The PUT is a full replace, so
 * callers that only want to change some fields must spread this first and
 * override on top — otherwise every field they omit gets wiped on the server.
 */
export function toUserProfileInput(profile: UserProfile): UserProfileInput {
  return {
    salutation: profile.salutation,
    firstName: profile.firstName,
    lastName: profile.lastName,
    birthDate: profile.birthDate,
    civilStatus: profile.civilStatus,
    churchAffiliation: profile.churchAffiliation,
    nationality: profile.nationality,
    street: profile.street,
    postalCode: profile.postalCode,
    city: profile.city,
    municipality: profile.municipality,
    cantonCode: profile.cantonCode,
    phone: profile.phone,
    preferredLanguage: profile.preferredLanguage,
    theme: profile.theme,
    defaultCurrency: profile.defaultCurrency,
  };
}

/** Query key for the shared `['profile']` cache entry. */
export const PROFILE_QUERY_KEY = ['profile'] as const;

export const profileApi = {
  get: (token: string) => apiRequest<UserProfile>('/profile', {}, token),

  update: (token: string, data: UserProfileInput) =>
    apiRequest<UserProfile>('/profile', { method: 'PUT', body: JSON.stringify(data) }, token),

  updatePreferences: (token: string, data: PreferencesInput) =>
    apiRequest<UserProfile>(
      '/profile/preferences',
      { method: 'PATCH', body: JSON.stringify(data) },
      token,
    ),
};
