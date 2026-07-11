import type React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  LayoutDashboard,
  TrendingUp,
  Calculator,
  PiggyBank,
  Shield,
  ShieldCheck,
  Settings,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Package,
} from 'lucide-react';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { useAuth } from '@/auth/useAuth';
import { cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface SidebarProps {
  /** true = icon-only rail (desktop) */
  collapsed?: boolean;
  /** shown only when provided — the mobile sheet has no collapse button */
  onToggleCollapse?: () => void;
  onClose?: () => void;
}

export function Sidebar({ collapsed = false, onToggleCollapse, onClose }: Readonly<SidebarProps>) {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const { pathname } = useLocation();

  const navItems: NavItem[] = [
    { to: '/dashboard', label: t('nav.dashboard'), icon: LayoutDashboard },
    { to: '/investments', label: t('nav.investments'), icon: TrendingUp },
    { to: '/tax', label: t('nav.tax'), icon: Calculator },
    { to: '/pillar3', label: t('nav.pillar3'), icon: PiggyBank },
    { to: '/insurance', label: t('nav.insurance'), icon: Shield },
  ];

  const adminItems: NavItem[] = [
    { to: '/admin/pillar3-products', label: t('nav.adminPillar3Products'), icon: Package },
  ];
  const isAdmin = hasRole('admin');

  const linkClassName = ({ isActive }: { isActive: boolean }) =>
    cn(
      'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
      collapsed && 'justify-center px-2',
      isActive
        ? 'bg-sidebar-primary text-sidebar-primary-foreground'
        : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
    );

  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      {/* Logo + collapse toggle */}
      <div
        className={cn(
          'flex h-16 items-center border-b border-sidebar-border',
          collapsed ? 'justify-center px-2' : 'justify-between px-4'
        )}
      >
        {!collapsed && (
          <span className="text-xl font-bold tracking-tight text-sidebar-foreground">finyo</span>
        )}
        {onToggleCollapse && (
          <button
            type="button"
            onClick={onToggleCollapse}
            aria-label={collapsed ? t('nav.expandSidebar') : t('nav.collapseSidebar')}
            className="rounded-md p-1.5 text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          >
            {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </button>
        )}
      </div>

      {/* Main navigation */}
      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onClose}
            title={collapsed ? label : undefined}
            className={linkClassName}
          >
            <Icon className="h-4 w-4 shrink-0" />
            {!collapsed && label}
          </NavLink>
        ))}

        {/* Admin group — realm role "admin" only */}
        {isAdmin && !collapsed && (
          <Collapsible defaultOpen={pathname.startsWith('/admin')} className="pt-2">
            <CollapsibleTrigger className="group flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-sidebar-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground">
              <ShieldCheck className="h-4 w-4 shrink-0" />
              <span className="flex-1 text-left">{t('nav.admin')}</span>
              <ChevronDown className="h-4 w-4 shrink-0 transition-transform group-data-[state=open]:rotate-180" />
            </CollapsibleTrigger>
            <CollapsibleContent className="mt-1 space-y-1 pl-4">
              {adminItems.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} onClick={onClose} className={linkClassName}>
                  <Icon className="h-4 w-4 shrink-0" />
                  {label}
                </NavLink>
              ))}
            </CollapsibleContent>
          </Collapsible>
        )}
        {/* Collapsed rail: no group header, icon-only admin links with tooltips */}
        {isAdmin && collapsed && (
          <div className="space-y-1 pt-2">
            {adminItems.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} onClick={onClose} title={label} className={linkClassName}>
                <Icon className="h-4 w-4 shrink-0" />
              </NavLink>
            ))}
          </div>
        )}
      </nav>

      {/* Settings — pinned at the bottom, separate from the main navigation */}
      <div className="border-t border-sidebar-border px-3 py-4">
        <NavLink
          to="/settings"
          onClick={onClose}
          title={collapsed ? t('nav.settings') : undefined}
          className={linkClassName}
        >
          <Settings className="h-4 w-4 shrink-0" />
          {!collapsed && t('nav.settings')}
        </NavLink>
        {!collapsed && (
          <p className="mt-3 px-3 text-xs text-sidebar-foreground/50">finyo v0.1.0</p>
        )}
      </div>
    </div>
  );
}
