import { describe, expect, it } from 'vitest';
import { formatCHF, formatDateDE } from './formatters';

describe('formatCHF', () => {
  it('formats a number with Swiss thousands separator', () => {
    expect(formatCHF(1234.5)).toBe("CHF 1'234.50");
  });

  it('parses numeric strings', () => {
    expect(formatCHF('99.9')).toBe('CHF 99.90');
  });

  it('falls back to CHF 0.00 for invalid input', () => {
    expect(formatCHF('not-a-number')).toBe('CHF 0.00');
  });
});

describe('formatDateDE', () => {
  it('formats an ISO date as DD.MM.YYYY', () => {
    expect(formatDateDE('2026-07-05')).toBe('05.07.2026');
  });

  it('returns the input unchanged when it is not a date', () => {
    expect(formatDateDE('garbage')).toBe('garbage');
  });
});
