import type { WealthBucket, WealthHistory, WealthOverview } from '@/api/wealth';

export function wealthBucket(
  overrides: Partial<WealthBucket> & { id: string },
): WealthBucket {
  return {
    name: 'Sparen',
    note: 'Raiffeisen Sparkonto',
    source: 'MANUAL',
    assetClasses: [],
    balance: 4500,
    sharePct: 4.5,
    monthlyRate: 500,
    forecastYearEnd: 7000,
    sortOrder: 0,
    auto: false,
    ...overrides,
  };
}

/** The read-only portfolio row the backend synthesizes from the investments module. */
export function autoPortfolioBucket(overrides: Partial<WealthBucket> = {}): WealthBucket {
  return wealthBucket({
    id: 'auto-portfolio',
    name: 'Portfolio',
    note: null,
    source: 'PORTFOLIO',
    auto: true,
    balance: 77_000,
    sharePct: 77.2,
    monthlyRate: null,
    forecastYearEnd: 77_000,
    sortOrder: -2,
    ...overrides,
  });
}

/** The read-only pillar 3a row derived from the default scenario. */
export function autoPillar3Bucket(overrides: Partial<WealthBucket> = {}): WealthBucket {
  return wealthBucket({
    id: 'auto-pillar3',
    name: 'Säule 3a',
    note: null,
    source: 'PILLAR3',
    auto: true,
    balance: 18_219,
    sharePct: 18.3,
    monthlyRate: 604.83,
    forecastYearEnd: 21_243.15,
    sortOrder: -1,
    ...overrides,
  });
}

export function wealthOverview(overrides: Partial<WealthOverview> = {}): WealthOverview {
  return {
    buckets: [
      autoPortfolioBucket(),
      autoPillar3Bucket(),
      wealthBucket({ id: 'b1' }),
    ],
    total: 99_719,
    totalMonthlyRate: 1104.83,
    totalForecastYearEnd: 105_243.15,
    ytdChange: 12_340,
    ytdChangePct: 14.1,
    asOf: '2026-07-11',
    ...overrides,
  };
}

export function wealthHistory(overrides: Partial<WealthHistory> = {}): WealthHistory {
  return {
    points: [
      { date: '2026-01-31', total: 87_400 },
      { date: '2026-02-28', total: 89_100 },
      { date: '2026-03-31', total: 90_800 },
      { date: '2026-04-30', total: 91_500 },
      { date: '2026-05-31', total: 95_200 },
      { date: '2026-06-30', total: 98_000 },
      { date: '2026-07-11', total: 99_719 },
    ],
    ...overrides,
  };
}
