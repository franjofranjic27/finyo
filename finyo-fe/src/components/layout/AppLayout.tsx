import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { BottomTabBar } from './BottomTabBar';
import { PageBreadcrumb } from './Breadcrumb';
import { BreadcrumbProvider } from './BreadcrumbContext';
import { useProfileSync } from '@/hooks/useProfileSync';
import { cn } from '@/lib/utils';

const SIDEBAR_COLLAPSED_KEY = 'finyo.sidebar.collapsed';

export function AppLayout() {
  useProfileSync();

  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
  );

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(!prev));
      return !prev;
    });
  };

  return (
    <BreadcrumbProvider>
      <div className="flex h-screen overflow-hidden bg-background print:block print:h-auto print:overflow-visible">
        {/* Desktop sidebar — collapsible to an icon-only rail */}
        <aside
          className={cn(
            'hidden lg:flex lg:flex-col lg:shrink-0 lg:border-r transition-[width] duration-200 print:hidden',
            collapsed ? 'lg:w-16' : 'lg:w-60'
          )}
        >
          <Sidebar collapsed={collapsed} onToggleCollapse={toggleCollapsed} />
        </aside>

        {/* Main area */}
        <div className="flex flex-1 flex-col overflow-hidden print:overflow-visible">
          <div className="print:hidden">
            <Header />
          </div>

          <main className="flex-1 overflow-y-auto print:overflow-visible">
            {/* Extra bottom padding on mobile keeps content clear of the tab bar */}
            <div className="mx-auto max-w-[1400px] p-4 pb-24 lg:p-6 print:pb-4">
              <div className="mb-4 print:hidden">
                <PageBreadcrumb />
              </div>
              <Outlet />
            </div>
          </main>
        </div>

        {/* Mobile bottom navigation — replaces the sidebar below lg */}
        <BottomTabBar />
      </div>
    </BreadcrumbProvider>
  );
}
