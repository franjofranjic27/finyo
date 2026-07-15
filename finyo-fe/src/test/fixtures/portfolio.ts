import type { Portfolio, PortfolioPosition } from '@/api/portfolio';
import type { PositionDetail, PriceHistory } from '@/api/positionDetail';

export function portfolioPosition(
  overrides: Partial<PortfolioPosition> & { id: string },
): PortfolioPosition {
  const merged: PortfolioPosition = {
    positionId: overrides.id,
    instrumentId: `instrument-${overrides.id}`,
    assetClass: 'STOCK',
    name: 'Nestlé SA',
    isin: 'CH0038863350',
    valor: null,
    currency: 'CHF',
    quantity: 10,
    purchasePrice: 90,
    purchaseDate: null,
    currentPrice: 100,
    priceSource: 'MARKET',
    priceAsOf: '2026-07-10',
    stale: false,
    value: 1000,
    valueChf: 1000,
    gainLoss: 100,
    returnPct: 11.1,
    allocationPct: 100,
    fxRate: null,
    fxRateDate: null,
    fxRateType: null,
    ...overrides,
  };
  // valueChf follows value unless a test sets it explicitly — these fixtures are CHF holdings, so
  // the converted value equals the native one, and a test that overrides only `value` still balances.
  if (overrides.valueChf === undefined) {
    merged.valueChf = merged.value;
  }
  return merged;
}

export function portfolio(overrides: Partial<Portfolio> = {}): Portfolio {
  return {
    positions: [portfolioPosition({ id: 'p1' })],
    totalCurrency: 'CHF',
    hasUnconverted: false,
    totalValue: 1000,
    totalCost: 900,
    gainLoss: 100,
    returnPct: 11.1,
    asOf: '2026-07-10T12:00:00Z',
    ...overrides,
  };
}

export function positionDetail(
  overrides: Partial<PositionDetail> & { positionId: string },
): PositionDetail {
  return {
    instrumentId: `instrument-${overrides.positionId}`,
    name: 'Nestlé SA',
    isin: 'CH0038863350',
    valor: '3886335',
    assetClass: 'STOCK',
    ter: null,
    currency: 'CHF',
    quantity: 10,
    avgPurchasePrice: 90,
    purchaseDate: null,
    currentPrice: 100,
    priceSource: 'MARKET',
    priceAsOf: '2026-07-10',
    stale: false,
    value: 1000,
    valueChf: 1000,
    gainLoss: 100,
    returnPercent: 11.1,
    portfolioShare: 60,
    fxRate: null,
    fxRateDate: null,
    fxRateType: null,
    factsheetUrl: null,
    factsheet: null,
    ...overrides,
  };
}

export function priceHistory(overrides: Partial<PriceHistory> = {}): PriceHistory {
  return {
    isin: 'CH0038863350',
    currency: 'CHF',
    points: [
      { date: '2026-07-08', close: 96.5 },
      { date: '2026-07-09', close: 98.2 },
      { date: '2026-07-10', close: 100.0 },
    ],
    ...overrides,
  };
}
