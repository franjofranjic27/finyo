import { useTranslation } from 'react-i18next';
import type { PriceSource } from '@/api/portfolio';
import { formatDate } from '@/lib/formatters';

/**
 * The price-provenance fields every priced position carries — the portfolio row
 * and the position detail both satisfy this shape.
 */
export interface PricedPosition {
  readonly priceSource: PriceSource;
  readonly priceAsOf: string | null;
  readonly stale: boolean;
}

/**
 * True when the price needs no comment: a fresh market price is exactly what the
 * user expects to see. Everything else — a stale price, a hand-typed one, or no
 * price at all — has to say so.
 */
export function isUnremarkablePrice(position: PricedPosition): boolean {
  return position.priceSource === 'MARKET' && !position.stale;
}

/** Full provenance sentence for a price — used as tooltip and screen-reader text. */
export function usePriceProvenanceText(position: PricedPosition): string {
  const { t, i18n } = useTranslation();

  const date = position.priceAsOf ? formatDate(position.priceAsOf, i18n.language) : null;
  const asOf = date ? t('investments.price.asOf', { date }) : t('investments.price.asOfUnknown');

  switch (position.priceSource) {
    case 'MARKET': {
      const hint = position.stale
        ? t('investments.price.staleHint')
        : t('investments.price.marketHint');
      return `${asOf} · ${hint}`;
    }
    case 'MANUAL':
      return date ? `${t('investments.price.manualHint')} · ${asOf}` : t('investments.price.manualHint');
    case 'PURCHASE':
      return t('investments.price.purchaseHint');
  }
}
