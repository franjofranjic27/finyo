import { describe, expect, it } from 'vitest';
import { deriveAgeInfo } from './retirement';

const TODAY = new Date('2026-07-13T12:00:00');

describe('deriveAgeInfo', () => {
  it('derives age, years to retirement and retirement year', () => {
    expect(deriveAgeInfo('1990-05-01', TODAY)).toEqual({
      age: 36,
      yearsToRetirement: 29,
      retirementYear: 2055,
    });
  });

  it('subtracts a year before the birthday has passed', () => {
    expect(deriveAgeInfo('1990-12-24', TODAY)?.age).toBe(35);
  });

  it('counts the birthday itself as passed', () => {
    expect(deriveAgeInfo('1990-07-13', TODAY)?.age).toBe(36);
  });

  it('clamps years to retirement at zero beyond the retirement age', () => {
    expect(deriveAgeInfo('1950-01-01', TODAY)).toEqual({
      age: 76,
      yearsToRetirement: 0,
      retirementYear: 2015,
    });
  });

  it('returns null for empty, partial or future dates', () => {
    expect(deriveAgeInfo('', TODAY)).toBeNull();
    expect(deriveAgeInfo('1990-05', TODAY)).toBeNull();
    expect(deriveAgeInfo('2030-01-01', TODAY)).toBeNull();
  });
});
