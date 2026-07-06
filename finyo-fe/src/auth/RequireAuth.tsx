import { useEffect } from 'react';
import type React from 'react';
import { useAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getRealmRoles } from './roles';

/** Realm roles that grant access to the application. */
const ALLOWED_ROLES = ['user', 'admin'];

function CenteredScreen({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-4 text-center px-4">{children}</div>
    </div>
  );
}

/**
 * Route guard: redirects unauthenticated visitors to the Keycloak login and
 * blocks authenticated users that do not carry one of the required realm
 * roles ("user" or "admin").
 */
export function RequireAuth({ children }: Readonly<{ children: React.ReactNode }>) {
  const auth = useAuth();
  const { t } = useTranslation();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error) {
      void auth.signinRedirect();
    }
  }, [auth]);

  if (auth.error) {
    return (
      <CenteredScreen>
        <ShieldAlert className="h-10 w-10 text-destructive" />
        <p className="text-sm text-muted-foreground">{t('auth.loginFailed')}</p>
        <Button onClick={() => void auth.signinRedirect()}>{t('auth.retryLogin')}</Button>
      </CenteredScreen>
    );
  }

  if (auth.isLoading || !auth.isAuthenticated) {
    return (
      <CenteredScreen>
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        <p className="text-sm text-muted-foreground">{t('auth.redirecting')}</p>
      </CenteredScreen>
    );
  }

  const roles = getRealmRoles(auth.user?.access_token);
  const hasAccess = ALLOWED_ROLES.some((role) => roles.includes(role));

  if (!hasAccess) {
    return (
      <CenteredScreen>
        <ShieldAlert className="h-10 w-10 text-destructive" />
        <h2 className="text-lg font-semibold">{t('auth.accessDeniedTitle')}</h2>
        <p className="max-w-sm text-sm text-muted-foreground">{t('auth.accessDeniedText')}</p>
        <Button variant="outline" onClick={() => void auth.signoutRedirect()}>
          {t('auth.logout')}
        </Button>
      </CenteredScreen>
    );
  }

  return <>{children}</>;
}
