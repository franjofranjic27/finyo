import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import type { Pillar3ProductComparison } from '@/api/pillar3';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { KeyValueRow } from './KeyValueRow';

interface ComparisonTableProps {
  /** Sorted by finalCapital descending — index 0 gets the «Top» badge. */
  products: Pillar3ProductComparison[];
  colorByProductId: ReadonlyMap<string, string>;
  onRemove: (productId: string) => void;
}

export function ComparisonTable({
  products,
  colorByProductId,
  onRemove,
}: Readonly<ComparisonTableProps>) {
  const { t } = useTranslation();

  return (
    <>
      {/* Mobile: one stacked card block per product instead of the wide table. */}
      <ul className="divide-y md:hidden">
        {products.map((product, index) => (
          <li key={product.productId} className="py-4 first:pt-0 last:pb-0">
            <div className="flex items-start gap-2.5">
              <span
                className="mt-0.5 h-7 w-2.5 shrink-0 rounded-sm"
                style={{ backgroundColor: colorByProductId.get(product.productId) }}
                aria-hidden
              />
              <div className="min-w-0 flex-1">
                <p className="font-semibold leading-snug">{product.name}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {product.provider} · {product.isin}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon"
                className="-mr-2 -mt-1.5 h-9 w-9 shrink-0 text-muted-foreground hover:text-destructive"
                aria-label={t('pillar3.compare.remove', { name: product.name })}
                onClick={() => onRemove(product.productId)}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
            <dl className="mt-2.5 space-y-1.5 pl-5">
              <KeyValueRow
                label={t('pillar3.compare.equity')}
                value={formatPercent(product.equityPct)}
              />
              <KeyValueRow label={t('pillar3.compare.ter')} value={`${product.terPct.toFixed(2)}%`} />
              <KeyValueRow
                label={t('pillar3.compare.avgReturn')}
                value={formatPercent(product.avgReturnPct)}
              />
              <KeyValueRow label={t('pillar3.compare.fees')} value={formatCHF(product.totalFees)} />
              <KeyValueRow
                label={t('pillar3.compare.finalCapital')}
                emphasized
                value={
                  <span className="inline-flex items-center gap-2">
                    {index === 0 && (
                      <Badge className="bg-emerald-500 hover:bg-emerald-500">
                        {t('pillar3.compare.topBadge')}
                      </Badge>
                    )}
                    {formatCHF(product.finalCapital)}
                  </span>
                }
              />
            </dl>
          </li>
        ))}
      </ul>

      {/* Desktop: unchanged comparison table. */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('pillar3.compare.product')}</TableHead>
              <TableHead className="text-right">{t('pillar3.compare.equity')}</TableHead>
              <TableHead className="text-right">{t('pillar3.compare.ter')}</TableHead>
              <TableHead className="text-right">{t('pillar3.compare.avgReturn')}</TableHead>
              <TableHead className="text-right">{t('pillar3.compare.fees')}</TableHead>
              <TableHead className="text-right">{t('pillar3.compare.finalCapital')}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {products.map((product, index) => (
              <TableRow key={product.productId}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <span
                      className="h-3 w-3 shrink-0 rounded-full"
                      style={{ backgroundColor: colorByProductId.get(product.productId) }}
                      aria-hidden
                    />
                    <div className="min-w-0">
                      <p className="truncate font-medium">{product.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {product.provider} · {product.isin}
                      </p>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="text-right">{formatPercent(product.equityPct)}</TableCell>
                <TableCell className="text-right">{product.terPct.toFixed(2)}%</TableCell>
                <TableCell className="text-right">{formatPercent(product.avgReturnPct)}</TableCell>
                <TableCell className="text-right">{formatCHF(product.totalFees)}</TableCell>
                <TableCell className="text-right">
                  <span className="inline-flex items-center gap-2 font-semibold">
                    {formatCHF(product.finalCapital)}
                    {index === 0 && (
                      <Badge className="bg-emerald-500 hover:bg-emerald-500">
                        {t('pillar3.compare.topBadge')}
                      </Badge>
                    )}
                  </span>
                </TableCell>
                <TableCell className="w-10 text-right">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-muted-foreground hover:text-destructive"
                    aria-label={t('pillar3.compare.remove', { name: product.name })}
                    onClick={() => onRemove(product.productId)}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </>
  );
}
