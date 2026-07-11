/**
 * Format a number as CHF currency.
 * Uses Swiss locale for consistent formatting: 1'234.56
 */
export function formatCHF(value: number | string): string {
  const num = typeof value === 'string' ? Number.parseFloat(value) : value;
  if (Number.isNaN(num)) return 'CHF 0.00';
  return `CHF ${num.toLocaleString('de-CH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/**
 * Format an ISO date string as DD.MM.YYYY (German/Swiss format).
 */
export function formatDateDE(isoDate: string): string {
  const date = new Date(isoDate);
  if (Number.isNaN(date.getTime())) return isoDate;
  return date.toLocaleDateString('de-CH', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

/**
 * Format an ISO date string as English readable format.
 */
export function formatDateEN(isoDate: string): string {
  const date = new Date(isoDate);
  if (Number.isNaN(date.getTime())) return isoDate;
  return date.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

/**
 * Return a locale-aware date formatter based on current language.
 */
export function formatDate(isoDate: string, lang: string = 'en'): string {
  return lang === 'de' ? formatDateDE(isoDate) : formatDateEN(isoDate);
}

/**
 * Format a percentage value with one decimal place.
 */
export function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

/**
 * Format a byte count as a human-readable size (B, KB or MB).
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Return a colour class for positive/negative amounts.
 */
export function amountColour(value: number | string): string {
  const num = typeof value === 'string' ? Number.parseFloat(value) : value;
  if (num > 0) return 'text-emerald-500';
  if (num < 0) return 'text-red-500';
  return 'text-muted-foreground';
}
