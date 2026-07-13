import type React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from './useAuth';
import { profileApi, PROFILE_QUERY_KEY } from '@/api/profile';

const ONBOARDING_PATH = '/onboarding';

/**
 * Redirects users without a completed onboarding to the wizard. While the
 * profile is loading the children render as-is to avoid a loading flash.
 */
export function OnboardingGate({ children }: Readonly<{ children: React.ReactNode }>) {
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const location = useLocation();

  const { data: profile } = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: () => profileApi.get(token),
    enabled: !!token,
  });

  const needsOnboarding = profile?.onboardingCompleted === false;
  if (needsOnboarding && location.pathname !== ONBOARDING_PATH) {
    return <Navigate to={ONBOARDING_PATH} replace />;
  }

  return <>{children}</>;
}
