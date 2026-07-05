import { Menu, Sun, Moon, LogOut, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useTheme } from '@/hooks/useTheme';
import { useAuth } from '@/auth/useAuth';

const ROUTE_LABELS: Record<string, string> = {
  '/dashboard': 'nav.dashboard',
  '/investments': 'nav.investments',
  '/tax': 'nav.tax',
  '/pillar3': 'nav.pillar3',
  '/insurance': 'nav.insurance',
  '/settings': 'nav.settings',
};

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const { t, i18n } = useTranslation();
  const { pathname } = useLocation();
  const { isDark, toggle } = useTheme();
  const { user, logout } = useAuth();

  // Match on the first path segment so nested routes like /tax/2025 resolve correctly.
  const pageTitle = t(ROUTE_LABELS[`/${pathname.split('/')[1]}`] ?? 'nav.dashboard');
  const username =
    (user?.profile?.preferred_username as string | undefined) ??
    (user?.profile?.name as string | undefined) ??
    'User';
  const initials = username.slice(0, 2).toUpperCase();

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'en' ? 'de' : 'en');
  };

  return (
    <header className="flex h-16 items-center gap-4 border-b bg-background px-4 lg:px-6">
      {/* Mobile hamburger */}
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenuClick}>
        <Menu className="h-5 w-5" />
        <span className="sr-only">Toggle menu</span>
      </Button>

      {/* Page title */}
      <h1 className="flex-1 text-lg font-semibold text-foreground">{pageTitle}</h1>

      {/* Right side controls */}
      <div className="flex items-center gap-2">
        {/* Language toggle */}
        <div className="flex items-center rounded-md border bg-background">
          <Button
            variant="ghost"
            size="sm"
            className={`h-8 rounded-r-none px-2 text-xs ${i18n.language === 'en' ? 'bg-primary text-primary-foreground' : ''}`}
            onClick={() => i18n.changeLanguage('en')}
          >
            EN
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className={`h-8 rounded-l-none px-2 text-xs ${i18n.language === 'de' ? 'bg-primary text-primary-foreground' : ''}`}
            onClick={() => i18n.changeLanguage('de')}
          >
            DE
          </Button>
        </div>

        {/* Dark/light toggle */}
        <Button
          variant="ghost"
          size="icon"
          onClick={toggle}
          aria-label={isDark ? t('common.lightMode') : t('common.darkMode')}
        >
          {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
        </Button>

        {/* User avatar dropdown */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="rounded-full">
              <Avatar className="h-8 w-8">
                <AvatarFallback>{initials}</AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuLabel className="font-normal">
              <div className="flex flex-col space-y-1">
                <p className="text-sm font-medium leading-none">{username}</p>
                <p className="text-xs leading-none text-muted-foreground">
                  {(user?.profile?.email as string | undefined) ?? ''}
                </p>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={toggleLanguage}>
              <User className="mr-2 h-4 w-4" />
              {t('common.language')}: {i18n.language.toUpperCase()}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout} className="text-destructive focus:text-destructive">
              <LogOut className="mr-2 h-4 w-4" />
              Logout
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
