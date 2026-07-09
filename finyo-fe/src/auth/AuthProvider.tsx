import React from 'react';
import { AuthProvider as OidcAuthProvider } from 'react-oidc-context';
import { getRuntimeConfig } from '../runtime-config';

const runtimeConfig = getRuntimeConfig();

const oidcConfig = {
  authority:
    runtimeConfig.keycloakUrl ??
    import.meta.env.VITE_KEYCLOAK_URL ??
    'http://localhost:8081/realms/finyo',
  client_id: runtimeConfig.keycloakClientId ?? import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'finyo-ui',
  redirect_uri: globalThis.location.origin,
  post_logout_redirect_uri: globalThis.location.origin,
  scope: 'openid profile email',
  automaticSilentRenew: true,
  // strip ?code=…&state=… from the URL after the redirect from Keycloak
  onSigninCallback: () => {
    globalThis.history.replaceState({}, document.title, globalThis.location.pathname);
  },
};

export function AuthProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  return <OidcAuthProvider {...oidcConfig}>{children}</OidcAuthProvider>;
}
