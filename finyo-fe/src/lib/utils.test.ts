import { describe, expect, it } from 'vitest';
import { cn } from './utils';

describe('cn', () => {
  it('joins class names', () => {
    expect(cn('a', 'b')).toBe('a b');
  });

  it('drops falsy values', () => {
    expect(cn('a', false, undefined, null, '', 'b')).toBe('a b');
  });

  it('resolves conditional class objects', () => {
    expect(cn('base', { active: true, hidden: false })).toBe('base active');
  });

  it('lets the last conflicting Tailwind class win', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4');
  });
});
