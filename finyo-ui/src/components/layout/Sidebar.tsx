import type React from 'react';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  LayoutDashboard,
  Receipt,
  Target,
  PiggyBank,
  TrendingUp,
  Calculator,
  Shield,
  Newspaper,
  Settings,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

export function Sidebar({ onClose }: { onClose?: () => void }) {
  const { t } = useTranslation();

  const navItems: NavItem[] = [
    { to: '/dashboard', label: t('nav.dashboard'), icon: LayoutDashboard },
    { to: '/transactions', label: t('nav.transactions'), icon: Receipt },
    { to: '/budget', label: t('nav.budget'), icon: Target },
    { to: '/savings', label: t('nav.savings'), icon: PiggyBank },
    { to: '/investments', label: t('nav.investments'), icon: TrendingUp },
    { to: '/tax', label: t('nav.tax'), icon: Calculator },
    { to: '/insurance', label: t('nav.insurance'), icon: Shield },
    { to: '/news', label: t('nav.news'), icon: Newspaper },
    { to: '/settings', label: t('nav.settings'), icon: Settings },
  ];

  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      {/* Logo */}
      <div className="flex h-16 items-center border-b border-sidebar-border px-6">
        <span className="text-xl font-bold tracking-tight text-sidebar-foreground">finyo</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 px-3 py-4 overflow-y-auto">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onClose}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-sidebar-primary text-sidebar-primary-foreground'
                  : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
              )
            }
          >
            <Icon className="h-4 w-4 shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="border-t border-sidebar-border px-6 py-4">
        <p className="text-xs text-sidebar-foreground/50">finyo v0.1.0</p>
      </div>
    </div>
  );
}
