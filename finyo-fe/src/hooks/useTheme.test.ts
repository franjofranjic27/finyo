import { beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useTheme } from './useTheme';
import { createMatchMedia } from '@/test/setup';

describe('useTheme', () => {
  beforeEach(() => {
    document.documentElement.classList.remove('dark', 'light');
  });

  it('defaults to light when the system does not prefer dark', () => {
    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('light');
    expect(result.current.isDark).toBe(false);
    expect(document.documentElement).toHaveClass('light');
  });

  it('defaults to dark when the system prefers dark', () => {
    globalThis.matchMedia = createMatchMedia(true);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('prefers the stored theme over the system preference', () => {
    globalThis.matchMedia = createMatchMedia(true);
    localStorage.setItem('finyo-theme', 'light');

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('light');
  });

  it('toggle switches the theme, the root class and the stored value', () => {
    const { result } = renderHook(() => useTheme());

    act(() => result.current.toggle());

    expect(result.current.theme).toBe('dark');
    expect(result.current.isDark).toBe(true);
    expect(document.documentElement).toHaveClass('dark');
    expect(document.documentElement).not.toHaveClass('light');
    expect(localStorage.getItem('finyo-theme')).toBe('dark');

    act(() => result.current.toggle());

    expect(result.current.theme).toBe('light');
    expect(localStorage.getItem('finyo-theme')).toBe('light');
  });
});
