import { apiRequest } from './client';
import type { AssetClass } from './portfolio';

/** How a wealth bucket's balance is maintained (mirrors the backend enum). */
export type WealthSource = 'MANUAL' | 'PORTFOLIO' | 'PILLAR3';

export interface WealthBucket {
  /** UUID for manual buckets; 'auto-portfolio' / 'auto-pillar3' for auto rows. */
  id: string;
  name: string;
  note: string | null;
  source: WealthSource;
  assetClasses: AssetClass[];
  balance: number;
  sharePct: number;
  /** null when no deposit can be derived (e.g. the auto portfolio row) */
  monthlyRate: number | null;
  forecastYearEnd: number;
  sortOrder: number;
  /** true for the read-only rows mirrored live from portfolio / pillar 3a */
  auto: boolean;
}

export interface WealthOverview {
  buckets: WealthBucket[];
  total: number;
  totalMonthlyRate: number;
  totalForecastYearEnd: number;
  /** null until a snapshot from an earlier day this year exists */
  ytdChange: number | null;
  ytdChangePct: number | null;
  asOf: string;
}

export interface WealthHistoryPoint {
  date: string;
  total: number;
}

export interface WealthHistory {
  points: WealthHistoryPoint[];
}

/** Only manual pots can be written; the auto rows are synthesized by the backend. */
export interface WealthBucketInput {
  name: string;
  note?: string;
  source: 'MANUAL';
  manualBalance?: number;
  monthlyRate?: number;
  sortOrder?: number;
}

/** Slim response of bucket writes — computed values come from the overview. */
export interface WealthBucketSaved {
  id: string;
  name: string;
  note: string | null;
  source: WealthSource;
  assetClasses: AssetClass[];
  manualBalance: number | null;
  monthlyRate: number;
  sortOrder: number;
}

export const wealthApi = {
  getOverview: (token: string) => apiRequest<WealthOverview>('/wealth', {}, token),

  getHistory: (token: string, months: number) =>
    apiRequest<WealthHistory>(`/wealth/history?months=${months}`, {}, token),

  createBucket: (token: string, data: WealthBucketInput) =>
    apiRequest<WealthBucketSaved>(
      '/wealth/buckets',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),

  updateBucket: (token: string, id: string, data: WealthBucketInput) =>
    apiRequest<WealthBucketSaved>(
      `/wealth/buckets/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token,
    ),

  deleteBucket: (token: string, id: string) =>
    apiRequest<void>(`/wealth/buckets/${id}`, { method: 'DELETE' }, token),
};
