import type React from 'react';
import { useTranslation } from 'react-i18next';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from './useAuth';

interface RequireRoleProps {
  role: string;
  children: React.ReactNode;
}

/**
 * Route guard for role-gated pages: renders the access-denied view
 * (same pattern as RequireAuth) when the realm role is missing.
 * Rendered inside the app layout, so it fills the content area only.
 */
export function RequireRole({ role, children }: Readonly<RequireRoleProps>) {
  const { hasRole } = useAuth();
  const { t } = useTranslation();

  if (!hasRole(role)) {
    return (
      <div className="flex flex-col items-center gap-4 px-4 py-16 text-center">
        <ShieldAlert className="h-10 w-10 text-destructive" />
        <h2 className="text-lg font-semibold">{t('auth.accessDeniedTitle')}</h2>
        <p className="max-w-sm text-sm text-muted-foreground">{t('auth.accessDeniedText')}</p>
      </div>
    );
  }

  return <>{children}</>;
}
