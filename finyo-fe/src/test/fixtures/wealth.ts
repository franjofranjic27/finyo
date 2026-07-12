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
    ...overrides,
  };
}

export function wealthOverview(overrides: Partial<WealthOverview> = {}): WealthOverview {
  return {
    buckets: [
      wealthBucket({ id: 'b1' }),
      wealthBucket({
        id: 'b2',
        name: 'Wertschriftendepot',
        note: 'Raiffeisen · ETF & Aktien',
        source: 'PORTFOLIO',
        assetClasses: ['ETF', 'FUND', 'STOCK', 'BOND'],
        balance: 77_000,
        sharePct: 77.2,
        monthlyRate: 600,
        forecastYearEnd: 80_000,
        sortOrder: 1,
      }),
      wealthBucket({
        id: 'b3',
        name: 'Krypto',
        note: 'Swissquote · BTC',
        source: 'PORTFOLIO',
        assetClasses: ['CRYPTO'],
        balance: 5277.38,
        sharePct: 5.3,
        monthlyRate: 0,
        forecastYearEnd: 5277.38,
        sortOrder: 2,
      }),
    ],
    total: 99_719,
    totalMonthlyRate: 1600,
    totalForecastYearEnd: 107_719,
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
