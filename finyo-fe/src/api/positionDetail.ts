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
  currency: string;
  quantity: number;
  avgPurchasePrice: number;
  currentPrice: number;
  priceSource: PriceSource;
  priceUpdatedAt: string | null;
  value: number;
  gainLoss: number;
  returnPercent: number;
  portfolioShare: number;
  factsheetUrl: string | null;
  factsheet: FactsheetInfo | null;
}

export interface UpdateInstrumentRequest {
  name?: string;
  assetClass?: AssetClass;
  valor?: string | null;
  ter?: number | null;
  factsheetUrl?: string | null;
}

/** Extracts the RFC-7807 `detail` from an error response and throws it. */
async function throwProblem(response: Response): Promise<never> {
  const error = await response.json().catch(() => ({ detail: response.statusText }));
  throw new Error((error as { detail?: string }).detail ?? `Request failed: ${response.status}`);
}

export const positionDetailApi = {
  getPosition: (token: string, positionId: string) =>
    apiRequest<PositionDetail>(`/positions/${positionId}`, {}, token),

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
