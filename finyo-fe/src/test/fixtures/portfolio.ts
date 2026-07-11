import type { Portfolio, PortfolioPosition } from '@/api/portfolio';

export function portfolioPosition(
  overrides: Partial<PortfolioPosition> & { id: string },
): PortfolioPosition {
  return {
    instrumentId: `instrument-${overrides.id}`,
    name: 'Nestlé SA',
    isin: 'CH0038863350',
    valor: null,
    quantity: 10,
    purchasePrice: 90,
    currentPrice: 100,
    priceSource: 'LIVE',
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
