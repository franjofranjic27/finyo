import { useAuth as useOidcAuth } from 'react-oidc-context';
import { getRealmRoles } from './roles';

export function useAuth() {
  const auth = useOidcAuth();
  const roles = getRealmRoles(auth.user?.access_token);
  return {
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    user: auth.user,
    accessToken: auth.user?.access_token,
    roles,
    hasRole: (role: string) => roles.includes(role),
    login: () => auth.signinRedirect(),
    logout: () => auth.signoutRedirect(),
  };
}
