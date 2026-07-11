import type { FixedCostInput, PaymentInterval } from '@/api/budget';

const MONTHLY_VARIANTS = new Set(['monatl.', 'monatlich', 'monthly', 'm']);
const YEARLY_VARIANTS = new Set(['jährl.', 'jaehrlich', 'jährlich', 'yearly', 'j', 'y']);

/**
 * Normalises Swiss-formatted numbers: strips apostrophe (' and ’) and space
 * thousand separators; a single comma without a dot is treated as the
 * decimal separator, otherwise commas are thousand separators.
 */
function parseSwissNumber(raw: string): number {
  let cleaned = raw.replaceAll(/['’\s]/g, '');
  if (cleaned.includes(',')) {
    const hasSingleComma = cleaned.indexOf(',') === cleaned.lastIndexOf(',');
    cleaned = hasSingleComma && !cleaned.includes('.')
      ? cleaned.replace(',', '.')
      : cleaned.replaceAll(',', '');
  }
  if (!/^-?(\d+\.?\d*|\.\d+)$/.test(cleaned)) return Number.NaN;
  return Number.parseFloat(cleaned);
}

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
 * Detects the delimiter (`;` wins over `,`), skips an optional header row
 * and silently drops invalid rows — the backend reports upsert failures
 * per row via its bulk result.
 */
export function parseFixedCostsCsv(text: string): FixedCostInput[] {
  const delimiter = text.includes(';') ? ';' : ',';
  const items: FixedCostInput[] = [];
  let firstRowSeen = false;

  text.split(/\r?\n/).forEach((line) => {
    if (!line.trim()) return;

    const cells = line.split(delimiter).map((cell) => cell.trim());
    if (!firstRowSeen) {
      firstRowSeen = true;
      if (isHeaderRow(cells)) return;
    }

    const item = parseRow(cells);
    if (item) items.push(item);
  });

  return items;
}
