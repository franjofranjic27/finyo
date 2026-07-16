import { apiRequest } from './client';
import type { AssetClass, PriceSource } from './portfolio';

const BASE_URL = '/api/v1';

export interface FactsheetInfo {
  filename: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface PositionDetail {
  positionId: string;
  instrumentId: string;
  name: string;
  isin: string | null;
  valor: string | null;
  assetClass: AssetClass;
  ter: number | null;
  /** Trading currency — null when nobody established it. Never assume CHF. */
  currency: string | null;
  quantity: number;
  avgPurchasePrice: number;
  purchaseDate: string | null;
  currentPrice: number;
  priceSource: PriceSource;
  /** The trading day the price belongs to (ISO date), not the day it was fetched. */
  priceAsOf: string | null;
  /** The price is older than a market price should be (> 4 days). */
  stale: boolean;
  value: number;
  gainLoss: number;
  returnPercent: number;
  portfolioShare: number;
  factsheetUrl: string | null;
  factsheet: FactsheetInfo | null;
}

/** A single trading day's closing price. */
export interface PriceHistoryPoint {
  /** ISO date (YYYY-MM-DD) the close belongs to. */
  date: string;
  close: number;
}

export interface PriceHistory {
  isin: string | null;
  /** Quote currency — null when unknown. Never assume CHF. */
  currency: string | null;
  /** Daily closes, oldest first. Empty when the instrument is unlisted or not yet backfilled. */
  points: PriceHistoryPoint[];
}

export interface UpdateInstrumentRequest {
  name?: string;
  assetClass?: AssetClass;
  valor?: string | null;
  ter?: number | null;
  factsheetUrl?: string | null;
}

/**
 * Holding patch: quantity/purchasePrice/currentPrice are applied only when
 * present; purchaseDate is always applied — null is an explicit clear
 * (requires at least one other field, an all-null patch is rejected).
 */
export interface UpdatePositionRequest {
  quantity?: number;
  purchasePrice?: number;
  purchaseDate?: string | null;
  currentPrice?: number;
}

/** Extracts the RFC-7807 `detail` from an error response and throws it. */
async function throwProblem(response: Response): Promise<never> {
  const error = await response.json().catch(() => ({ detail: response.statusText }));
  throw new Error((error as { detail?: string }).detail ?? `Request failed: ${response.status}`);
}

export const positionDetailApi = {
  getPosition: (token: string, positionId: string) =>
    apiRequest<PositionDetail>(`/positions/${positionId}`, {}, token),

  getPriceHistory: (positionId: string, token: string) =>
    apiRequest<PriceHistory>(`/positions/${positionId}/price-history`, {}, token),

  updatePosition: (token: string, positionId: string, data: UpdatePositionRequest) =>
    apiRequest<PositionDetail>(
      `/positions/${positionId}`,
      { method: 'PATCH', body: JSON.stringify(data) },
      token,
    ),

  updateInstrument: (token: string, positionId: string, data: UpdateInstrumentRequest) =>
    apiRequest<PositionDetail>(
      `/positions/${positionId}/instrument`,
      { method: 'PATCH', body: JSON.stringify(data) },
      token,
    ),

  /**
   * Multipart upload — deliberately bypasses `apiRequest`, whose hardcoded
   * JSON content type would break the multipart boundary. The browser sets
   * the correct Content-Type for FormData itself.
   */
  uploadFactsheet: async (
    token: string,
    positionId: string,
    file: File,
  ): Promise<PositionDetail> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fetch(`${BASE_URL}/positions/${positionId}/factsheet`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!response.ok) return throwProblem(response);
    return response.json() as Promise<PositionDetail>;
  },

  /**
   * Fetches the factsheet PDF as a blob. The endpoint requires the bearer
   * token, so a plain <a href> cannot be used — callers open the blob URL.
   */
  fetchFactsheet: async (token: string, positionId: string): Promise<Blob> => {
    const response = await fetch(`${BASE_URL}/positions/${positionId}/factsheet`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) return throwProblem(response);
    return response.blob();
  },

  deleteFactsheet: (token: string, positionId: string) =>
    apiRequest<void>(`/positions/${positionId}/factsheet`, { method: 'DELETE' }, token),
};
