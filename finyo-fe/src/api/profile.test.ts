import { beforeEach, describe, expect, it, vi } from 'vitest';
import { profileApi } from './profile';
import type { PreferencesInput, UserProfileInput } from './profile';
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

  it('updatePreferences PATCHes only the changed theme', () => {
    profileApi.updatePreferences(TOKEN, { theme: 'DARK' });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/profile/preferences',
      { method: 'PATCH', body: JSON.stringify({ theme: 'DARK' }) },
      TOKEN,
    );
  });

  it('updatePreferences PATCHes only the changed language', () => {
    profileApi.updatePreferences(TOKEN, { preferredLanguage: 'de' });

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/profile/preferences',
      { method: 'PATCH', body: JSON.stringify({ preferredLanguage: 'de' }) },
      TOKEN,
    );
  });

  it('updatePreferences sends both preferences at once', () => {
    const input: PreferencesInput = { theme: 'SYSTEM', preferredLanguage: 'en' };

    profileApi.updatePreferences(TOKEN, input);

    expect(apiRequestMock).toHaveBeenCalledWith(
      '/profile/preferences',
      { method: 'PATCH', body: JSON.stringify(input) },
      TOKEN,
    );
  });
});
