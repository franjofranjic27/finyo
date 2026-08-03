/**
 * The 26 Swiss canton abbreviations — the single source for every canton
 * dropdown (tax calculator, 3a calculator, profile). Matches the backend's
 * two-letter cantonCode convention.
 */
export const CANTONS = [
  'AG', 'AI', 'AR', 'BE', 'BL', 'BS', 'FR', 'GE', 'GL', 'GR',
  'JU', 'LU', 'NE', 'NW', 'OW', 'SG', 'SH', 'SO', 'SZ', 'TG',
  'TI', 'UR', 'VD', 'VS', 'ZG', 'ZH',
] as const;

export type CantonCode = (typeof CANTONS)[number];
