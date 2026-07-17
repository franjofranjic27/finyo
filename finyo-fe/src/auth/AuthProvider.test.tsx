import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WebStorageStateStore } from 'oidc-client-ts';
import { AuthProvider } from './AuthProvider';

interface CapturedOidcProps {
  authority?: string;
  client_id?: string;
  scope?: string;
  onSigninCallback?: () => void;
  userStore?: WebStorageStateStore;
}

let capturedProps: CapturedOidcProps;

vi.mock('react-oidc-context', () => ({
  AuthProvider: ({ children, ...props }: React.PropsWithChildren<CapturedOidcProps>) => {
    capturedProps = props;
    return <div>{children}</div>;
  },
}));

describe('AuthProvider', () => {
  beforeEach(() => {
    capturedProps = {};
  });

  it('renders its children inside the OIDC provider', () => {
    render(
      <AuthProvider>
        <span>app</span>
      </AuthProvider>,
    );

    expect(screen.getByText('app')).toBeInTheDocument();
  });

  it('configures the Keycloak realm defaults', () => {
    render(<AuthProvider>x</AuthProvider>);

    expect(capturedProps.authority).toBe('http://localhost:8081/realms/finyo');
    expect(capturedProps.client_id).toBe('finyo-ui');
    expect(capturedProps.scope).toBe('openid profile email');
  });

  it('persists the session in localStorage, not sessionStorage', () => {
    render(<AuthProvider>x</AuthProvider>);

    // The store backs the whole "stay logged in across browser restarts" feature;
    // oidc-client-ts silently defaults to sessionStorage, which loses it.
    expect(capturedProps.userStore).toBeInstanceOf(WebStorageStateStore);
    expect((capturedProps.userStore as unknown as { _store: Storage })._store).toBe(
      globalThis.localStorage,
    );
  });

  it('prefers the runtime config over build-time defaults', async () => {
    globalThis.window.__FINYO_CONFIG__ = {
      keycloakUrl: 'https://auth.example.com/realms/finyo',
      keycloakClientId: 'finyo-prod',
    };
    vi.resetModules();
    try {
      const { AuthProvider: FreshAuthProvider } = await import('./AuthProvider');

      render(<FreshAuthProvider>x</FreshAuthProvider>);

      expect(capturedProps.authority).toBe('https://auth.example.com/realms/finyo');
      expect(capturedProps.client_id).toBe('finyo-prod');
    } finally {
      delete globalThis.window.__FINYO_CONFIG__;
      vi.resetModules();
    }
  });

  it('ignores empty runtime config values', async () => {
    globalThis.window.__FINYO_CONFIG__ = { keycloakUrl: '', keycloakClientId: '' };
    vi.resetModules();
    try {
      const { AuthProvider: FreshAuthProvider } = await import('./AuthProvider');

      render(<FreshAuthProvider>x</FreshAuthProvider>);

      expect(capturedProps.authority).toBe('http://localhost:8081/realms/finyo');
      expect(capturedProps.client_id).toBe('finyo-ui');
    } finally {
      delete globalThis.window.__FINYO_CONFIG__;
      vi.resetModules();
    }
  });

  it('strips the OIDC query params from the URL after the signin callback', () => {
    render(<AuthProvider>x</AuthProvider>);
    const replaceState = vi.spyOn(globalThis.history, 'replaceState');

    capturedProps.onSigninCallback?.();

    expect(replaceState).toHaveBeenCalledWith({}, document.title, globalThis.location.pathname);
  });
});
