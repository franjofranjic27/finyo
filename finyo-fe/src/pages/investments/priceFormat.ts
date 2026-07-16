/** Pure price/date formatters for the price-history chart, split out so the
 * component module stays a component-only export (react-refresh) and the
 * formatting rules can be unit-tested in isolation. */

/**
 * Compact day/month axis tick. Built from the ISO date parts on purpose:
 * `new Date('2023-07-15')` is parsed as UTC midnight and slips to the previous
 * day in time zones west of UTC. Splitting the string sidesteps that entirely.
 */
export function formatTick(date: string): string {
  const [, month, day] = date.split('-');
  return `${Number(day)}.${Number(month)}.`;
}

/**
 * Formats a quote in its own currency. Falls back to a plain decimal when the
 * currency is unknown — assuming CHF would invent a fact. Guarded because an
 * unexpected currency code makes `Intl.NumberFormat` throw.
 */
export function formatPrice(value: number, currency: string | null, lang: string): string {
  if (currency) {
    try {
      return new Intl.NumberFormat(lang, { style: 'currency', currency }).format(value);
    } catch {
      // Unknown ISO code — fall through to a bare number.
    }
  }
  return new Intl.NumberFormat(lang, {
    style: 'decimal',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

/** Locale-aware full date for the tooltip, without the UTC-midnight shift. */
export function formatTooltipDate(date: string, lang: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(lang === 'de' ? 'de-CH' : 'en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}
