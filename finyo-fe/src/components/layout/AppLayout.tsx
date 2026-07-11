import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { BreadcrumbProvider } from './BreadcrumbContext';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { cn } from '@/lib/utils';

const SIDEBAR_COLLAPSED_KEY = 'finyo.sidebar.collapsed';

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
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

        {/* Mobile sidebar — slide-in sheet (never collapsed) */}
        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetContent side="left" className="w-60 p-0">
            <Sidebar onClose={() => setMobileOpen(false)} />
          </SheetContent>
        </Sheet>

        {/* Main area */}
        <div className="flex flex-1 flex-col overflow-hidden print:overflow-visible">
          <div className="print:hidden">
            <Header onMenuClick={() => setMobileOpen(true)} />
          </div>

          <main className="flex-1 overflow-y-auto print:overflow-visible">
            <div className="mx-auto max-w-[1400px] p-4 lg:p-6">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </BreadcrumbProvider>
  );
}
