import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Product } from '@/api/pillar3';
import { formatPercent } from '@/lib/formatters';
import { PriceHistoryContent } from '@/pages/investments/PriceHistoryChart';
import { KeyValueRow } from './KeyValueRow';

interface ProductDetailDialogProps {
  product: Pillar3Product;
  onClose: () => void;
}

/**
 * Read-only detail view of a pillar 3a catalog product: master data plus the
 * fund's stored price history. Mount fresh per open. An empty chart is normal
 * for unlisted 3a funds; for listed ones the first view triggers a background
 * backfill, so the next open shows data.
 */
export function ProductDetailDialog({ product, onClose }: Readonly<ProductDetailDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data, isLoading } = useQuery({
    queryKey: ['pillar3-products', product.id, 'price-history'],
    queryFn: () => pillar3Api.getProductPriceHistory(token, product.id),
    enabled: !!token,
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{product.name}</DialogTitle>
        </DialogHeader>
        <dl className="space-y-1.5">
          <KeyValueRow label={t('pillar3.productDetail.provider')} value={product.provider} />
          <KeyValueRow label={t('pillar3.productDetail.isin')} value={product.isin} />
          {product.valor && (
            <KeyValueRow label={t('pillar3.productDetail.valor')} value={product.valor} />
          )}
          <KeyValueRow
            label={t('pillar3.productDetail.equity')}
            value={formatPercent(product.equityPct)}
          />
          <KeyValueRow
            label={t('pillar3.productDetail.ter')}
            value={`${product.terPct.toFixed(2)}%`}
          />
          <KeyValueRow
            label={t('pillar3.productDetail.expectedReturn')}
            value={formatPercent(product.expectedReturnPct)}
          />
          <KeyValueRow
            label={t('pillar3.productDetail.netReturn')}
            emphasized
            value={formatPercent(product.netReturnPct)}
          />
        </dl>
        <div className="space-y-2">
          <h3 className="text-sm font-medium">{t('investments.priceHistory.title')}</h3>
          <PriceHistoryContent
            points={data?.points ?? []}
            currency={data?.currency ?? null}
            isLoading={isLoading}
          />
        </div>
      </DialogContent>
    </Dialog>
  );
}
