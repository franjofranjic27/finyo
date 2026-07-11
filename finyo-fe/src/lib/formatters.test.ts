import { describe, expect, it } from 'vitest';
import {
  amountColour,
  formatCHF,
  formatDate,
  formatDateDE,
  formatDateEN,
  formatFileSize,
  formatPercent,
} from './formatters';

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

describe('formatDateEN', () => {
  it('formats an ISO date as DD MMM YYYY', () => {
    expect(formatDateEN('2026-07-05')).toBe('05 Jul 2026');
  });

  it('returns the input unchanged when it is not a date', () => {
    expect(formatDateEN('garbage')).toBe('garbage');
  });
});

describe('formatDate', () => {
  it('uses the German format for lang "de"', () => {
    expect(formatDate('2026-07-05', 'de')).toBe('05.07.2026');
  });

  it('uses the English format for lang "en"', () => {
    expect(formatDate('2026-07-05', 'en')).toBe('05 Jul 2026');
  });

  it('defaults to English when no lang is given', () => {
    expect(formatDate('2026-07-05')).toBe('05 Jul 2026');
  });
});

describe('formatPercent', () => {
  it('formats with one decimal place', () => {
    expect(formatPercent(14.55)).toBe('14.6%');
  });

  it('keeps zero as 0.0%', () => {
    expect(formatPercent(0)).toBe('0.0%');
  });
});

describe('formatFileSize', () => {
  it('formats sub-kilobyte sizes as bytes', () => {
    expect(formatFileSize(512)).toBe('512 B');
  });

  it('formats kilobyte sizes without decimals', () => {
    expect(formatFileSize(340 * 1024)).toBe('340 KB');
  });

  it('formats megabyte sizes with one decimal', () => {
    expect(formatFileSize(2.5 * 1024 * 1024)).toBe('2.5 MB');
  });
});

describe('amountColour', () => {
  it('returns green for positive amounts', () => {
    expect(amountColour(10)).toBe('text-emerald-500');
  });

  it('returns red for negative amounts', () => {
    expect(amountColour(-0.01)).toBe('text-red-500');
  });

  it('returns muted for zero', () => {
    expect(amountColour(0)).toBe('text-muted-foreground');
  });

  it('parses numeric strings', () => {
    expect(amountColour('-42.50')).toBe('text-red-500');
  });
});
