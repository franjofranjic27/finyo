import type { Portfolio, PortfolioPosition } from '@/api/portfolio';
import type { PositionDetail } from '@/api/positionDetail';

export function portfolioPosition(
  overrides: Partial<PortfolioPosition> & { id: string },
): PortfolioPosition {
  return {
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
    gainLoss: 100,
    returnPct: 11.1,
    allocationPct: 100,
    ...overrides,
  };
}

export function portfolio(overrides: Partial<Portfolio> = {}): Portfolio {
  return {
    positions: [portfolioPosition({ id: 'p1' })],
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
    gainLoss: 100,
    returnPercent: 11.1,
    portfolioShare: 60,
    factsheetUrl: null,
    factsheet: null,
    ...overrides,
  };
}
