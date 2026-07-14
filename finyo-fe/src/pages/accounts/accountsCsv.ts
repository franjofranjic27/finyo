import type { AccountScope, AccountType, CreateAccountRequest } from '@/types';
import { csvRows } from '@/lib/csv';

const TYPE_SYNONYMS: Readonly<Record<string, AccountType | undefined>> = {
  kontokorrent: 'CHECKING',
  girokonto: 'CHECKING',
  privatkonto: 'CHECKING',
  checking: 'CHECKING',
  sparkonto: 'SAVINGS',
  sparen: 'SAVINGS',
  savings: 'SAVINGS',
  kreditkarte: 'CREDIT_CARD',
  'credit card': 'CREDIT_CARD',
  depot: 'INVESTMENT',
  investment: 'INVESTMENT',
  broker: 'INVESTMENT',
  bargeld: 'CASH',
  cash: 'CASH',
  vorsorge: 'VORSORGE',
  vorsorgekonto: 'VORSORGE',
  '3a': 'VORSORGE',
  'säule 3a': 'VORSORGE',
  sonstiges: 'OTHER',
  other: 'OTHER',
};

const SCOPE_SYNONYMS: Readonly<Record<string, AccountScope | undefined>> = {
  privat: 'PRIVATE',
  'geschäft': 'BUSINESS',
  geschaeft: 'BUSINESS',
  business: 'BUSINESS',
};

const TO_CLOSE_VARIANTS = new Set(['ja', 'x', 'true', '1']);

/** A first row is a header when its name column carries the column label. */
function isHeaderRow(cells: string[]): boolean {
  return (cells[0] ?? '').toLowerCase().includes('name');
}

function parseRow(cells: string[]): CreateAccountRequest | undefined {
  const [
    name,
    typeRaw = '',
    scopeRaw = '',
    currencyRaw = '',
    iban = '',
    bic = '',
    contractNumber = '',
    feeNote = '',
    toCloseRaw = '',
  ] = cells;
  if (!name) return undefined;

  const type = TYPE_SYNONYMS[typeRaw.toLowerCase()];
  if (!type) return undefined;

  const currency = currencyRaw.toUpperCase();
  // A malformed currency would 400 the whole cascade-validated batch — drop the row instead.
  if (currency && !/^[A-Z]{3}$/.test(currency)) return undefined;

  return {
    name,
    type,
    // the backend rejects a blank currency (@NotBlank), so apply its create default client-side
    currency: currency || 'CHF',
    scope: SCOPE_SYNONYMS[scopeRaw.toLowerCase()],
    // the backend normalizes IBANs (strips spaces, uppercases) — pass through as entered
    iban: iban || undefined,
    bic: bic || undefined,
    contractNumber: contractNumber || undefined,
    feeNote: feeNote || undefined,
    toClose: TO_CLOSE_VARIANTS.has(toCloseRaw.toLowerCase()) || undefined,
  };
}

/**
 * Parses an accounts CSV with the columns
 * `Name; Typ; Bereich; Währung; IBAN; BIC; Vertragsnummer; Gebühren; Auflösen`.
 * Only name and a recognised type are required — invalid rows are silently
 * dropped, matching the fixed-costs importer; the backend reports upsert
 * failures per row via its bulk result.
 */
export function parseAccountsCsv(text: string): CreateAccountRequest[] {
  const items: CreateAccountRequest[] = [];
  for (const { cells } of csvRows(text, isHeaderRow)) {
    const item = parseRow(cells);
    if (item) items.push(item);
  }
  return items;
}
