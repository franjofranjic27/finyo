import React from 'react';
import { AuthProvider as OidcAuthProvider } from 'react-oidc-context';

const oidcConfig = {
  authority: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081/realms/finyo',
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'finyo-ui',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  automaticSilentRenew: true,
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
  return <OidcAuthProvider {...oidcConfig}>{children}</OidcAuthProvider>;
}
