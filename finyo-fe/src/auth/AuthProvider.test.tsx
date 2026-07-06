import type React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthProvider } from './AuthProvider';

interface CapturedOidcProps {
  authority?: string;
  client_id?: string;
  scope?: string;
  onSigninCallback?: () => void;
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

  it('strips the OIDC query params from the URL after the signin callback', () => {
    render(<AuthProvider>x</AuthProvider>);
    const replaceState = vi.spyOn(globalThis.history, 'replaceState');

    capturedProps.onSigninCallback?.();

    expect(replaceState).toHaveBeenCalledWith({}, document.title, globalThis.location.pathname);
  });
});
