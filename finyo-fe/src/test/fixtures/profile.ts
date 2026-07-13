import type { UserProfile } from '@/api/profile';

export function userProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    birthDate: '1990-05-01',
    civilStatus: 'SINGLE',
    churchAffiliation: 'NONE',
    preferredLanguage: 'en',
    theme: 'SYSTEM',
    onboardingCompleted: true,
    age: 36,
    yearsToRetirement: 29,
    retirementYear: 2055,
    ...overrides,
  };
}

/** Backend defaults for a user without a stored profile row. */
export function emptyUserProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    birthDate: null,
    civilStatus: null,
    churchAffiliation: null,
    preferredLanguage: null,
    theme: 'SYSTEM',
    onboardingCompleted: false,
    age: null,
    yearsToRetirement: null,
    retirementYear: null,
    ...overrides,
  };
}
