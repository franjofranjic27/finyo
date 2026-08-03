import type { UserProfile } from '@/api/profile';

export function userProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    salutation: 'MS',
    firstName: 'Anna',
    lastName: 'Muster',
    birthDate: '1990-05-01',
    civilStatus: 'SINGLE',
    churchAffiliation: 'NONE',
    nationality: 'Schweiz',
    street: 'Musterstrasse 12',
    postalCode: '9000',
    city: 'St. Gallen',
    municipality: 'St. Gallen',
    cantonCode: 'SG',
    phone: '+41 79 123 45 67',
    preferredLanguage: 'en',
    theme: 'SYSTEM',
    defaultCurrency: 'CHF',
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
    salutation: null,
    firstName: null,
    lastName: null,
    birthDate: null,
    civilStatus: null,
    churchAffiliation: null,
    nationality: null,
    street: null,
    postalCode: null,
    city: null,
    municipality: null,
    cantonCode: null,
    phone: null,
    preferredLanguage: null,
    theme: 'SYSTEM',
    defaultCurrency: 'CHF',
    onboardingCompleted: false,
    age: null,
    yearsToRetirement: null,
    retirementYear: null,
    ...overrides,
  };
}
