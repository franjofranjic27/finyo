import { beforeEach, describe, expect, it, vi } from 'vitest';
import { profileApi } from './profile';
import type { UserProfileInput } from './profile';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('profileApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('get requests /profile with the token', () => {
    profileApi.get(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/profile', {}, TOKEN);
  });

  it('update PUTs the serialised profile input', () => {
    const input: UserProfileInput = {
      birthDate: '1990-05-01',
      civilStatus: 'MARRIED',
      churchAffiliation: 'NONE',
      preferredLanguage: 'de',
      theme: 'SYSTEM',
      onboardingCompleted: true,
    };

    profileApi.update(TOKEN, input);

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/profile',
      { method: 'PUT', body: JSON.stringify(input) },
      TOKEN,
    );
  });

  it('update supports partial inputs, e.g. only completing the onboarding', () => {
    profileApi.update(TOKEN, { onboardingCompleted: true });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/profile',
      { method: 'PUT', body: JSON.stringify({ onboardingCompleted: true }) },
      TOKEN,
    );
  });
});
