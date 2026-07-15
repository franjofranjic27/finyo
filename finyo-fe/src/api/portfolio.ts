import { apiRequest } from './client';

/**
 * Where the current price of a portfolio position comes from (mirrors the backend enum).
 *
 * - `MARKET` — a real provider price (15 min delayed, read from the DB, not fetched live)
 * - `MANUAL` — a price the user entered; the only option for unlisted 3a funds
 * - `PURCHASE` — no price is known at all, the position is carried at its purchase cost
 */
export type PriceSource = 'MARKET' | 'MANUAL' | 'PURCHASE';

/** Asset class of an instrument (mirrors the backend enum). */
export type AssetClass = 'ETF' | 'FUND' | 'STOCK' | 'CRYPTO' | 'BOND';

/** Display order of asset classes — used for table grouping and selects. */
export const ASSET_CLASSES: readonly AssetClass[] = ['ETF', 'FUND', 'STOCK', 'CRYPTO', 'BOND'];

export interface PortfolioPosition {
  id: string;
  positionId: string;
  instrumentId: string;
  assetClass: AssetClass;
  name: string | null;
  isin: string | null;
  valor: string | null;
  /** Trading currency — null when nobody established it. Never assume CHF. */
  currency: string | null;
  quantity: number;
  purchasePrice: number;
  purchaseDate: string | null;
  currentPrice: number;
  priceSource: PriceSource;
  /** The trading day the price belongs to (ISO date), not the day it was fetched. */
  priceAsOf: string | null;
  /** The price is older than a market price should be (> 4 days). */
  stale: boolean;
  value: number;
  gainLoss: number;
  returnPct: number;
  allocationPct: number;
}

export interface Portfolio {
  positions: PortfolioPosition[];
  totalValue: number;
  totalCost: number;
  gainLoss: number;
  returnPct: number;
  asOf: string;
}

export interface PortfolioHistoryPoint {
  date: string;
  totalValue: number;
  totalCost: number;
}

export interface PortfolioHistory {
  points: PortfolioHistoryPoint[];
}

export interface CreatePositionRequest {
  name?: string;
  isin?: string;
  valor?: string;
  quantity: number;
  purchasePrice: number;
  currentPrice?: number;
}

/** Slim response of POST /positions — the aggregated view comes from GET /portfolio. */
export interface PositionCreated {
  id: string;
  instrumentId: string;
  name: string | null;
  isin: string | null;
  valor: string | null;
  quantity: number;
  purchasePrice: number;
  createdAt: string;
}

export interface BulkImportResult {
  imported: number;
  failed: number;
  errors: string[];
}

export const portfolioApi = {
  getPortfolio: (token: string) => apiRequest<Portfolio>('/portfolio', {}, token),

  getHistory: (token: string, months: number) =>
    apiRequest<PortfolioHistory>(`/portfolio/history?months=${months}`, {}, token),

  createPosition: (token: string, data: CreatePositionRequest) =>
    apiRequest<PositionCreated>(
      '/positions',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  createPositionsBulk: (token: string, positions: CreatePositionRequest[]) =>
    apiRequest<BulkImportResult>(
      '/positions/bulk',
      { method: 'POST', body: JSON.stringify({ positions }) },
      token,
    ),

  deletePosition: (token: string, id: string) =>
    apiRequest<void>(`/positions/${id}`, { method: 'DELETE' }, token),
};
