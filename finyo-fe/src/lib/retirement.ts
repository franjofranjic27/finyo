/** Swiss reference retirement age used for the derived profile figures. */
export const RETIREMENT_AGE = 65;

export interface AgeInfo {
  age: number;
  yearsToRetirement: number;
  retirementYear: number;
}

/**
 * Derives age and retirement figures from an ISO birth date (yyyy-mm-dd).
 * Returns null for empty or unparsable input, e.g. a half-typed date field.
 */
export function deriveAgeInfo(birthDate: string, today: Date = new Date()): AgeInfo | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) return null;

  const birth = new Date(`${birthDate}T00:00:00`);
  if (Number.isNaN(birth.getTime()) || birth > today) return null;

  let age = today.getFullYear() - birth.getFullYear();
  const hadBirthdayThisYear =
    today.getMonth() > birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() >= birth.getDate());
  if (!hadBirthdayThisYear) age -= 1;

  return {
    age,
    yearsToRetirement: Math.max(0, RETIREMENT_AGE - age),
    retirementYear: birth.getFullYear() + RETIREMENT_AGE,
  };
}
