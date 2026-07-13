import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ThemeProvider, useTheme } from './useTheme';
import { createMatchMedia } from '@/test/setup';

function wrapper({ children }: Readonly<{ children: ReactNode }>) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

function renderTheme() {
  return renderHook(() => useTheme(), { wrapper });
}

/** matchMedia stub whose change events can be fired from the test. */
function installControllableMatchMedia(initialDark: boolean) {
  const listeners: Array<(event: MediaQueryListEvent) => void> = [];
  const mediaQueryList = {
    matches: initialDark,
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) => {
      listeners.push(listener);
    },
    removeEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) => {
      const index = listeners.indexOf(listener);
      if (index >= 0) listeners.splice(index, 1);
    },
    dispatchEvent: () => true,
  };
  globalThis.matchMedia = () => mediaQueryList as unknown as MediaQueryList;

  return {
    setDark(dark: boolean) {
      mediaQueryList.matches = dark;
      for (const listener of [...listeners]) {
        listener({ matches: dark } as MediaQueryListEvent);
      }
    },
  };
}

describe('useTheme', () => {
  beforeEach(() => {
    document.documentElement.classList.remove('dark', 'light');
  });

  it('throws without a ThemeProvider', () => {
    expect(() => renderHook(() => useTheme())).toThrow(/ThemeProvider/);
  });

  it('defaults to the system preference (light system)', () => {
    const { result } = renderTheme();

    expect(result.current.theme).toBe('system');
    expect(result.current.resolvedTheme).toBe('light');
    expect(result.current.isDark).toBe(false);
    expect(document.documentElement).toHaveClass('light');
  });

  it('resolves system to dark when the OS prefers dark', () => {
    globalThis.matchMedia = createMatchMedia(true);

    const { result } = renderTheme();

    expect(result.current.theme).toBe('system');
    expect(result.current.resolvedTheme).toBe('dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('prefers the stored preference over the system preference', () => {
    globalThis.matchMedia = createMatchMedia(true);
    localStorage.setItem('finyo-theme', 'light');

    const { result } = renderTheme();

    expect(result.current.theme).toBe('light');
    expect(result.current.resolvedTheme).toBe('light');
    expect(document.documentElement).toHaveClass('light');
  });

  it('setTheme applies the root class and persists the preference', () => {
    const { result } = renderTheme();

    act(() => result.current.setTheme('dark'));

    expect(result.current.theme).toBe('dark');
    expect(result.current.isDark).toBe(true);
    expect(document.documentElement).toHaveClass('dark');
    expect(document.documentElement).not.toHaveClass('light');
    expect(localStorage.getItem('finyo-theme')).toBe('dark');

    act(() => result.current.setTheme('light'));

    expect(result.current.resolvedTheme).toBe('light');
    expect(localStorage.getItem('finyo-theme')).toBe('light');
  });

  it('persists "system" as preference while applying the resolved class', () => {
    globalThis.matchMedia = createMatchMedia(true);
    const { result } = renderTheme();

    act(() => result.current.setTheme('system'));

    expect(localStorage.getItem('finyo-theme')).toBe('system');
    expect(result.current.resolvedTheme).toBe('dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('re-resolves the system theme when the OS preference changes', () => {
    const media = installControllableMatchMedia(false);
    const { result } = renderTheme();

    expect(result.current.resolvedTheme).toBe('light');

    act(() => media.setDark(true));

    expect(result.current.resolvedTheme).toBe('dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('ignores OS preference changes for an explicit preference', () => {
    const media = installControllableMatchMedia(false);
    const { result } = renderTheme();

    act(() => result.current.setTheme('light'));
    act(() => media.setDark(true));

    expect(result.current.resolvedTheme).toBe('light');
    expect(document.documentElement).toHaveClass('light');
  });
});
