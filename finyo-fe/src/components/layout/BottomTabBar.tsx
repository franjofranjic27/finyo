import type React from 'react';
import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Calculator,
  ChevronRight,
  Inbox,
  Landmark,
  LayoutDashboard,
  MoreHorizontal,
  Package,
  PiggyBank,
  ReceiptText,
  Settings,
  Shield,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useAuth } from '@/auth/useAuth';
import { cn } from '@/lib/utils';

interface TabItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

/**
 * Mobile-only bottom navigation: four primary destinations plus a "More"
 * sheet holding everything else from the desktop sidebar. Hidden on lg+
 * where the sidebar takes over.
 */
export function BottomTabBar() {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const { pathname } = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);

  const primaryTabs: TabItem[] = [
    { to: '/dashboard', label: t('nav.dashboard'), icon: LayoutDashboard },
    { to: '/wealth', label: t('nav.wealth'), icon: Wallet },
    { to: '/budget', label: t('nav.budget'), icon: ReceiptText },
    { to: '/tax', label: t('nav.tax'), icon: Calculator },
  ];

  const moreItems: TabItem[] = [
    { to: '/investments', label: t('nav.investments'), icon: TrendingUp },
    { to: '/pillar3', label: t('nav.pillar3'), icon: PiggyBank },
    { to: '/accounts', label: t('nav.accounts'), icon: Landmark },
    { to: '/documents', label: t('nav.documents'), icon: Inbox },
    { to: '/insurance', label: t('nav.insurance'), icon: Shield },
    { to: '/settings', label: t('nav.settings'), icon: Settings },
    ...(hasRole('admin')
      ? [{ to: '/admin/pillar3-products', label: t('nav.adminPillar3Products'), icon: Package }]
      : []),
  ];

  const moreActive = moreItems.some(({ to }) => pathname.startsWith(to));

  const tabClassName = (active: boolean) =>
    cn(
      'flex min-h-12 flex-1 flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition-colors',
      active ? 'text-primary' : 'text-muted-foreground'
    );

  return (
    <>
      <nav
        aria-label={t('nav.mobileNavigation')}
        className="fixed inset-x-0 bottom-0 z-40 flex border-t bg-background pb-[env(safe-area-inset-bottom)] lg:hidden print:hidden"
      >
        {primaryTabs.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={({ isActive }) => tabClassName(isActive)}>
            <Icon className="h-5 w-5" />
            {label}
          </NavLink>
        ))}
        <button type="button" onClick={() => setMoreOpen(true)} className={tabClassName(moreActive)}>
          <MoreHorizontal className="h-5 w-5" />
          {t('nav.more')}
        </button>
      </nav>

      {/* "More" sheet — the sidebar's secondary destinations as list rows */}
      <Sheet open={moreOpen} onOpenChange={setMoreOpen}>
        <SheetContent side="bottom" className="rounded-t-2xl pb-[env(safe-area-inset-bottom)]">
          <SheetHeader className="text-left">
            <SheetTitle>{t('nav.more')}</SheetTitle>
          </SheetHeader>
          <nav className="mt-2 flex flex-col">
            {moreItems.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                onClick={() => setMoreOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-12 items-center gap-3 rounded-md px-2 text-sm font-medium transition-colors',
                    isActive ? 'bg-secondary text-foreground' : 'text-foreground hover:bg-secondary/60'
                  )
                }
              >
                <Icon className="h-5 w-5 shrink-0 text-muted-foreground" />
                <span className="flex-1">{label}</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </NavLink>
            ))}
          </nav>
        </SheetContent>
      </Sheet>
    </>
  );
}
