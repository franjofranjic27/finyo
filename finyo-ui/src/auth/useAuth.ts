import { useAuth as useOidcAuth } from 'react-oidc-context';

export function useAuth() {
  const auth = useOidcAuth();
  return {
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    user: auth.user,
    accessToken: auth.user?.access_token,
    login: () => auth.signinRedirect(),
    logout: () => auth.signoutRedirect(),
  };
}
