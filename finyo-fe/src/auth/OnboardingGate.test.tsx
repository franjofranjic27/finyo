import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { OnboardingGate } from './OnboardingGate';
import { profileApi } from '@/api/profile';
import { renderWithProviders } from '@/test/test-utils';
import { userProfile } from '@/test/fixtures/profile';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/profile')>()),
  profileApi: { get: vi.fn(), update: vi.fn() },
}));

function renderGate(route: string) {
  return renderWithProviders(
    <OnboardingGate>
      <Routes>
        <Route path="/onboarding" element={<div>onboarding wizard</div>} />
        <Route path="/dashboard" element={<div>dashboard page</div>} />
      </Routes>
    </OnboardingGate>,
    { route },
  );
}

describe('OnboardingGate', () => {
  beforeEach(() => {
    vi.mocked(profileApi.get).mockReset();
  });

  it('renders the children while the profile is still loading', () => {
    vi.mocked(profileApi.get).mockReturnValue(new Promise(() => {}));

    renderGate('/dashboard');

    expect(screen.getByText('dashboard page')).toBeInTheDocument();
  });

  it('renders the children when the onboarding is completed', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile({ onboardingCompleted: true }));

    renderGate('/dashboard');

    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });

  it('redirects to /onboarding when the onboarding is not completed', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile({ onboardingCompleted: false }));

    renderGate('/dashboard');

    expect(await screen.findByText('onboarding wizard')).toBeInTheDocument();
    expect(screen.queryByText('dashboard page')).not.toBeInTheDocument();
  });

  it('does not redirect while already on /onboarding', async () => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile({ onboardingCompleted: false }));

    renderGate('/onboarding');

    expect(await screen.findByText('onboarding wizard')).toBeInTheDocument();
  });
});
