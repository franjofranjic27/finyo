import type { FixedCostInput, PaymentInterval } from '@/api/budget';
import { csvRows, parseSwissNumber } from '@/lib/csv';

const MONTHLY_VARIANTS = new Set(['monatl.', 'monatlich', 'monthly', 'm']);
const YEARLY_VARIANTS = new Set(['jährl.', 'jaehrlich', 'jährlich', 'yearly', 'j', 'y']);

function parsePaymentInterval(raw: string): PaymentInterval | undefined {
  const normalized = raw.toLowerCase();
  if (MONTHLY_VARIANTS.has(normalized)) return 'MONTHLY';
  if (YEARLY_VARIANTS.has(normalized)) return 'YEARLY';
  return undefined;
}

/**
 * A first row is a header when its name column carries a column label
 * ("name"/"bezeichnung") or its amount column does not parse as a number.
 */
function isHeaderRow(cells: string[]): boolean {
  const name = (cells[0] ?? '').toLowerCase();
  if (name.includes('name') || name.includes('bezeichnung')) return true;
  const amountCell = cells[3] ?? '';
  return amountCell !== '' && Number.isNaN(parseSwissNumber(amountCell));
}

function parseRow(cells: string[]): FixedCostInput | undefined {
  if (cells.length < 4) return undefined;

  const [name, category, billingRaw, amountRaw] = cells;
  if (!name) return undefined;

  const paymentInterval = parsePaymentInterval(billingRaw);
  if (!paymentInterval) return undefined;

  const amount = parseSwissNumber(amountRaw);
  if (Number.isNaN(amount) || amount < 0) return undefined;

  return { name, category: category || undefined, paymentInterval, amount };
}

/**
 * Parses a fixed-costs CSV with the columns
 * `Name; Category (optional); Billing (monthly/yearly); Amount`.
 * Invalid rows are silently dropped — the backend reports upsert failures
 * per row via its bulk result.
 */
export function parseFixedCostsCsv(text: string): FixedCostInput[] {
  const items: FixedCostInput[] = [];
  for (const { cells } of csvRows(text, isHeaderRow)) {
    const item = parseRow(cells);
    if (item) items.push(item);
  }
  return items;
}
