import type { Account, CreateAccountRequest, PaymentCard, PaymentCardInput } from '@/types';
import { apiRequest } from './client';

export const accountsApi = {
  getAll: (token: string) => apiRequest<Account[]>('/accounts', {}, token),

  getById: (token: string, id: string) => apiRequest<Account>(`/accounts/${id}`, {}, token),

  create: (token: string, data: CreateAccountRequest) =>
    apiRequest<Account>('/accounts', { method: 'POST', body: JSON.stringify(data) }, token),

  update: (token: string, id: string, data: Partial<CreateAccountRequest>) =>
    apiRequest<Account>(`/accounts/${id}`, { method: 'PUT', body: JSON.stringify(data) }, token),

  delete: (token: string, id: string) =>
    apiRequest<void>(`/accounts/${id}`, { method: 'DELETE' }, token),

  getCards: (token: string) => apiRequest<PaymentCard[]>('/cards', {}, token),

  createCard: (token: string, data: PaymentCardInput) =>
    apiRequest<PaymentCard>('/cards', { method: 'POST', body: JSON.stringify(data) }, token),

  updateCard: (token: string, id: string, data: PaymentCardInput) =>
    apiRequest<PaymentCard>(`/cards/${id}`, { method: 'PUT', body: JSON.stringify(data) }, token),

  deleteCard: (token: string, id: string) =>
    apiRequest<void>(`/cards/${id}`, { method: 'DELETE' }, token),
};

/** Formats a normalized IBAN in groups of four for display. */
export function formatIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim();
}
