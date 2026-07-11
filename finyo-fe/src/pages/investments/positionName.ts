import type { PortfolioPosition } from '@/api/portfolio';

/** Human-readable label of a position: name, else ISIN, else valor. */
export function displayName(position: PortfolioPosition): string {
  return position.name ?? position.isin ?? position.valor ?? '—';
}
