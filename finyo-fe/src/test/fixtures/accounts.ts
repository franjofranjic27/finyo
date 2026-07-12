import type { Account, PaymentCard } from '@/types';

export function bankAccount(overrides: Partial<Account> & { id: string }): Account {
  return {
    name: 'Privatkonto',
    type: 'CHECKING',
    currency: 'CHF',
    initialBalance: '0',
    iban: 'CH9300762011623852957',
    bic: 'RAIFCH22',
    contractNumber: '81375-0005',
    feeNote: null,
    scope: 'PRIVATE',
    toClose: false,
    createdAt: '2026-07-10T12:00:00Z',
    updatedAt: '2026-07-10T12:00:00Z',
    ...overrides,
  };
}

export function paymentCard(overrides: Partial<PaymentCard> & { id: string }): PaymentCard {
  return {
    name: 'Debit Mastercard',
    provider: 'Mastercard',
    accountId: 'a1',
    accountName: 'Kontokorrent',
    currency: 'CHF',
    feeNote: 'CHF 50 / Jahr',
    scope: 'BUSINESS',
    ...overrides,
  };
}
