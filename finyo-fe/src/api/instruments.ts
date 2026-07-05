import type { Instrument, MarketData } from '@/types';
import { apiRequest } from './client';

export interface CreateInstrumentRequest {
  valor?: string;
  isin?: string;
  ticker?: string;
  name?: string;
  instrumentType?: string;
}

export const instrumentsApi = {
  getAll: (token: string) => apiRequest<Instrument[]>('/investments/instruments', {}, token),

  getMarketData: (token: string, id: string) =>
    apiRequest<MarketData>(`/investments/instruments/${id}/market-data`, {}, token),

  getAllMarketData: (token: string) =>
    apiRequest<MarketData[]>('/investments/market-data', {}, token),

  create: (token: string, data: CreateInstrumentRequest) =>
    apiRequest<Instrument>(
      '/investments/instruments',
      { method: 'POST', body: JSON.stringify(data) },
      token
    ),

  update: (token: string, id: string, data: Partial<CreateInstrumentRequest>) =>
    apiRequest<Instrument>(
      `/investments/instruments/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token
    ),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/investments/instruments/${id}`, { method: 'DELETE' }, token),
};
