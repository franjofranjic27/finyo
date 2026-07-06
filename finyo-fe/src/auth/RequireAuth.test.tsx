import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RequireAuth } from './RequireAuth';
import { makeAccessToken } from '@/test/test-utils';

interface OidcAuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  activeNavigator?: string;
  error?: Error;
  user?: { access_token: string };
  signinRedirect: ReturnType<typeof vi.fn>;
  signoutRedirect: ReturnType<typeof vi.fn>;
}

let oidcAuth: OidcAuthState;

vi.mock('react-oidc-context', () => ({
  useAuth: () => oidcAuth,
}));

function renderGuarded() {
  return render(
    <RequireAuth>
      <div>protected content</div>
    </RequireAuth>,
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    oidcAuth = {
      isAuthenticated: false,
      isLoading: false,
      signinRedirect: vi.fn().mockResolvedValue(undefined),
      signoutRedirect: vi.fn().mockResolvedValue(undefined),
    };
  });

  it('shows the redirect screen and starts the login while loading finishes', () => {
    renderGuarded();

    expect(screen.getByText('Signing you in …')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();
    expect(oidcAuth.signinRedirect).toHaveBeenCalledTimes(1);
  });

  it('does not redirect while the auth state is still loading', () => {
    oidcAuth.isLoading = true;

    renderGuarded();

    expect(screen.getByText('Signing you in …')).toBeInTheDocument();
    expect(oidcAuth.signinRedirect).not.toHaveBeenCalled();
  });

  it('shows the error screen with a retry button on auth errors', async () => {
    oidcAuth.error = new Error('token exchange failed');
    const user = userEvent.setup();

    renderGuarded();

    expect(screen.getByText('Login failed. Please try again.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry login' }));
    expect(oidcAuth.signinRedirect).toHaveBeenCalledTimes(1);
  });

  it('renders the children for an authenticated user with the "user" role', () => {
    oidcAuth.isAuthenticated = true;
    oidcAuth.user = { access_token: makeAccessToken({ realm_access: { roles: ['user'] } }) };

    renderGuarded();

    expect(screen.getByText('protected content')).toBeInTheDocument();
  });

  it('blocks an authenticated user without an allowed role and offers logout', async () => {
    oidcAuth.isAuthenticated = true;
    oidcAuth.user = { access_token: makeAccessToken({ realm_access: { roles: ['other'] } }) };
    const user = userEvent.setup();

    renderGuarded();

    expect(screen.getByText('Access denied')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Log out' }));
    expect(oidcAuth.signoutRedirect).toHaveBeenCalledTimes(1);
  });
});
