import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useAuth } from './useAuth';
import { makeAccessToken } from '@/test/test-utils';

interface OidcAuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user?: { access_token: string };
  signinRedirect: ReturnType<typeof vi.fn>;
  signoutRedirect: ReturnType<typeof vi.fn>;
}

let oidcAuth: OidcAuthState;

vi.mock('react-oidc-context', () => ({
  useAuth: () => oidcAuth,
}));

describe('useAuth', () => {
  beforeEach(() => {
    oidcAuth = {
      isAuthenticated: true,
      isLoading: false,
      user: { access_token: makeAccessToken({ realm_access: { roles: ['user'] } }) },
      signinRedirect: vi.fn(),
      signoutRedirect: vi.fn(),
    };
  });

  it('exposes the access token and authentication state', () => {
    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.accessToken).toBe(oidcAuth.user?.access_token);
  });

  it('extracts realm roles from the access token', () => {
    const { result } = renderHook(() => useAuth());

    expect(result.current.roles).toEqual(['user']);
    expect(result.current.hasRole('user')).toBe(true);
    expect(result.current.hasRole('admin')).toBe(false);
  });

  it('returns no roles when there is no user', () => {
    oidcAuth.user = undefined;
    oidcAuth.isAuthenticated = false;

    const { result } = renderHook(() => useAuth());

    expect(result.current.roles).toEqual([]);
    expect(result.current.accessToken).toBeUndefined();
  });

  it('delegates login and logout to the OIDC redirects', () => {
    const { result } = renderHook(() => useAuth());

    result.current.login();
    result.current.logout();

    expect(oidcAuth.signinRedirect).toHaveBeenCalledTimes(1);
    expect(oidcAuth.signoutRedirect).toHaveBeenCalledTimes(1);
  });
});
