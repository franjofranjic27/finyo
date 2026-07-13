import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

/** User-facing theme preference; 'system' follows the OS setting. */
export type ThemePreference = 'light' | 'dark' | 'system';

export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'finyo-theme';
const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)';

interface ThemeContextValue {
  /** The stored preference (may be 'system'). */
  theme: ThemePreference;
  /** The theme actually applied to the document. */
  resolvedTheme: ResolvedTheme;
  isDark: boolean;
  setTheme: (theme: ThemePreference) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStoredPreference(): ThemePreference {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
}

export function ThemeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [theme, setThemeState] = useState<ThemePreference>(readStoredPreference);
  const [systemDark, setSystemDark] = useState(
    () => globalThis.matchMedia(DARK_SCHEME_QUERY).matches,
  );

  useEffect(() => {
    const mediaQuery = globalThis.matchMedia(DARK_SCHEME_QUERY);
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    mediaQuery.addEventListener('change', onChange);
    return () => mediaQuery.removeEventListener('change', onChange);
  }, []);

  const systemTheme: ResolvedTheme = systemDark ? 'dark' : 'light';
  const resolvedTheme: ResolvedTheme = theme === 'system' ? systemTheme : theme;

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('dark', 'light');
    root.classList.add(resolvedTheme);
  }, [resolvedTheme]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      resolvedTheme,
      isDark: resolvedTheme === 'dark',
      setTheme: (next: ThemePreference) => {
        localStorage.setItem(STORAGE_KEY, next);
        setThemeState(next);
      },
    }),
    [theme, resolvedTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}
