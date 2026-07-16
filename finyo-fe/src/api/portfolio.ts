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

/** Which kind of exchange rate was applied (mirrors the backend enum). */
export type FxRateType = 'MID' | 'OFFICIAL_CH';

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
  /** Value in the position's own {@link currency}. */
  value: number;
  /**
   * Value converted to CHF, or null when it could not be converted (no rate stored for the
   * currency yet). The CHF figures below are null in the same case — never assume a rate.
   */
  valueChf: number | null;
  /** CHF gain/loss; null when unconvertible. */
  gainLoss: number | null;
  /** CHF return; null when unconvertible. */
  returnPct: number | null;
  /** Share of the CHF total; null when unconvertible. */
  allocationPct: number | null;
  /** CHF-per-unit rate applied to reach valueChf; null for a CHF or unknown-currency position. */
  fxRate: number | null;
  /** The day the applied rate belongs to (ISO date); null when no rate was applied. */
  fxRateDate: string | null;
  fxRateType: FxRateType | null;
}

export interface Portfolio {
  positions: PortfolioPosition[];
  /** All totals are in this currency — always "CHF". */
  totalCurrency: string;
  /** True when a position could not be converted and is therefore excluded from the totals. */
  hasUnconverted: boolean;
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

/** Outcome of the add-position live lookup (mirrors the backend enum). */
export type InstrumentLookupStatus = 'FOUND' | 'NOT_FOUND' | 'UNAVAILABLE';

/**
 * Preview of an ISIN/valor from the provider chain. `FOUND` = a listed security whose price
 * comes automatically; `NOT_FOUND` = unknown (e.g. an unlisted 3a fund — enter price by hand);
 * `UNAVAILABLE` = the providers could not be reached.
 */
export interface InstrumentLookup {
  status: InstrumentLookupStatus;
  name: string | null;
  ticker: string | null;
  currency: string | null;
  assetClass: AssetClass | null;
}

export const portfolioApi = {
  getPortfolio: (token: string) => apiRequest<Portfolio>('/portfolio', {}, token),

  lookupInstrument: (token: string, params: { isin?: string; valor?: string }) => {
    const query = new URLSearchParams();
    if (params.isin) query.set('isin', params.isin);
    if (params.valor) query.set('valor', params.valor);
    return apiRequest<InstrumentLookup>(`/instruments/lookup?${query.toString()}`, {}, token);
  },

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
