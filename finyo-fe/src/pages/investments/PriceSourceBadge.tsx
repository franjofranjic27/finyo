import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/formatters';
import type { PricedPosition } from './priceProvenance';

/**
 * Names where a price comes from. Always renders — callers that only want to
 * flag the noteworthy cases skip it via `isUnremarkablePrice`.
 */
export function PriceSourceBadge({ position }: Readonly<{ position: PricedPosition }>) {
  const { t, i18n } = useTranslation();

  if (position.priceSource === 'PURCHASE') {
    return (
      <Badge variant="outline" className="gap-1 border-red-500/40 text-red-500">
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
        {t('investments.price.badge.PURCHASE')}
      </Badge>
    );
  }

  if (position.priceSource === 'MANUAL') {
    return <Badge variant="secondary">{t('investments.price.badge.MANUAL')}</Badge>;
  }

  const asOf = position.priceAsOf ? formatDate(position.priceAsOf, i18n.language) : null;

  if (position.stale) {
    return (
      <Badge
        variant="outline"
        className="gap-1 border-amber-500/40 text-amber-600 dark:text-amber-400"
      >
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
        {asOf
          ? t('investments.price.badge.stale', { date: asOf })
          : t('investments.price.badge.staleUnknown')}
      </Badge>
    );
  }

  return (
    <Badge variant="outline" className="text-muted-foreground">
      {t('investments.price.badge.MARKET')}
    </Badge>
  );
}
