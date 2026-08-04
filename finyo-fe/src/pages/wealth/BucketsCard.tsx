import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/auth/useAuth';
import { wealthApi } from '@/api/wealth';
import type { WealthBucket, WealthOverview, WealthSource } from '@/api/wealth';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';
import { BucketFormDialog } from './BucketFormDialog';
import { bucketDisplayName } from './bucketDisplayName';

function SourceBadge({ source }: Readonly<{ source: WealthSource }>) {
  const { t } = useTranslation();

  if (source === 'PORTFOLIO') {
    return <Badge variant="secondary">{t('wealth.buckets.sourcePortfolio')}</Badge>;
  }
  if (source === 'PILLAR3') {
    return <Badge variant="secondary">{t('wealth.buckets.sourcePillar3')}</Badge>;
  }
  return <Badge variant="outline">{t('wealth.buckets.sourceManual')}</Badge>;
}

/** Subtle marker for the read-only rows mirrored live from their module. */
function AutoBadge() {
  const { t } = useTranslation();

  return (
    <Badge
      variant="outline"
      className="text-muted-foreground"
      title={t('wealth.buckets.autoBadgeHint')}
    >
      {t('wealth.buckets.autoBadge')}
    </Badge>
  );
}

interface BucketItemProps {
  bucket: WealthBucket;
  index: number;
  onEdit: (bucket: WealthBucket) => void;
  onRemove: (bucket: WealthBucket) => void;
  removing: boolean;
}

/** Edit/delete actions, shared between the desktop table row and the mobile list row. */
function BucketActions({ bucket, onEdit, onRemove, removing }: Readonly<Omit<BucketItemProps, 'index'>>) {
  const { t } = useTranslation();

  return (
    <span className="inline-flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-muted-foreground"
        aria-label={t('common.edit')}
        onClick={() => onEdit(bucket)}
      >
        <Pencil className="h-3.5 w-3.5" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-muted-foreground hover:text-red-500"
        aria-label={t('wealth.buckets.deleteLabel')}
        disabled={removing}
        onClick={() => onRemove(bucket)}
      >
        <X className="h-3.5 w-3.5" />
      </Button>
    </span>
  );
}

/** Mobile representation of a bucket: name/value on top, source, rate and forecast below. */
function BucketListItem({ bucket, index, onEdit, onRemove, removing }: Readonly<BucketItemProps>) {
  const { t } = useTranslation();

  return (
    <li className="border-b border-border py-3 first:pt-1 last:border-b-0">
      <div className="flex items-center gap-3">
        <span
          className="h-8 w-1.5 shrink-0 rounded-full"
          style={{ backgroundColor: CHART_COLOURS[index % CHART_COLOURS.length] }}
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{bucketDisplayName(bucket, t)}</p>
          {bucket.note && (
            <p className="truncate text-xs text-muted-foreground">{bucket.note}</p>
          )}
        </div>
        <div className="shrink-0 text-right tabular-nums">
          <p className="text-sm font-medium">{formatCHF(bucket.balance)}</p>
          <p className="text-xs text-muted-foreground">{formatPercent(bucket.sharePct)}</p>
        </div>
      </div>
      <div className="mt-1 flex items-center gap-2 pl-4 text-xs text-muted-foreground">
        <SourceBadge source={bucket.source} />
        {bucket.auto && <AutoBadge />}
        <span className="shrink-0 tabular-nums">
          {!bucket.monthlyRate
            ? '—'
            : t('wealth.buckets.monthlyRateShort', { amount: `+${formatCHF(bucket.monthlyRate)}` })}
        </span>
        <span className="min-w-0 truncate tabular-nums">
          {t('wealth.buckets.forecastShort', { amount: formatCHF(bucket.forecastYearEnd) })}
        </span>
        {!bucket.auto && (
          <span className="ml-auto shrink-0">
            <BucketActions bucket={bucket} onEdit={onEdit} onRemove={onRemove} removing={removing} />
          </span>
        )}
      </div>
    </li>
  );
}

function BucketRow({ bucket, index, onEdit, onRemove, removing }: Readonly<BucketItemProps>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border">
      <td className="py-2 pr-4">
        <div className="flex items-center gap-2">
          <span
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: CHART_COLOURS[index % CHART_COLOURS.length] }}
            aria-hidden="true"
          />
          <div className="min-w-0">
            <p className="truncate font-medium">{bucketDisplayName(bucket, t)}</p>
            {bucket.note && (
              <p className="truncate text-xs text-muted-foreground">{bucket.note}</p>
            )}
          </div>
        </div>
      </td>
      <td className="py-2 pr-4">
        <span className="inline-flex items-center gap-1">
          <SourceBadge source={bucket.source} />
          {bucket.auto && <AutoBadge />}
        </span>
      </td>
      <td className="py-2 pr-4 text-right font-medium tabular-nums">{formatCHF(bucket.balance)}</td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(bucket.sharePct)}</td>
      <td className="py-2 pr-4 text-right tabular-nums">
        {!bucket.monthlyRate ? '—' : formatCHF(bucket.monthlyRate)}
      </td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(bucket.forecastYearEnd)}</td>
      <td className="py-2 text-right">
        {!bucket.auto && (
          <BucketActions bucket={bucket} onEdit={onEdit} onRemove={onRemove} removing={removing} />
        )}
      </td>
    </tr>
  );
}

export function BucketsCard({ overview }: Readonly<{ overview: WealthOverview }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<WealthBucket | null>(null);

  const deleteBucket = useMutation({
    mutationFn: (id: string) => wealthApi.deleteBucket(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['wealth'] }),
  });

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (bucket: WealthBucket) => {
    setEditing(bucket);
    setFormOpen(true);
  };

  const confirmRemove = (bucket: WealthBucket) => {
    if (globalThis.confirm(t('wealth.buckets.confirmDelete', { name: bucket.name }))) {
      deleteBucket.mutate(bucket.id);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-col gap-3 space-y-0 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <CardTitle className="text-base">{t('wealth.buckets.title')}</CardTitle>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('wealth.buckets.subtitle')}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={openCreate}>
          <Plus className="mr-1 h-4 w-4" />
          {t('wealth.buckets.add')}
        </Button>
      </CardHeader>
      <CardContent>
        {overview.buckets.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('wealth.buckets.empty')}
          </p>
        ) : (
          <>
            {/* Mobile: buckets as list rows with a total line, the table needs md width. */}
            <div className="md:hidden">
              <ul>
                {overview.buckets.map((bucket, index) => (
                  <BucketListItem
                    key={bucket.id}
                    bucket={bucket}
                    index={index}
                    onEdit={openEdit}
                    onRemove={confirmRemove}
                    removing={deleteBucket.isPending}
                  />
                ))}
              </ul>
              <div className="flex items-baseline justify-between gap-3 border-t border-border pt-3 text-sm font-bold tabular-nums">
                <span>{t('wealth.buckets.total')}</span>
                <span className="min-w-0 truncate text-right text-xs font-medium text-muted-foreground">
                  {t('wealth.buckets.monthlyRateShort', {
                    amount: `+${formatCHF(overview.totalMonthlyRate)}`,
                  })}
                  {' · '}
                  {t('wealth.buckets.forecastShort', {
                    amount: formatCHF(overview.totalForecastYearEnd),
                  })}
                </span>
                <span>{formatCHF(overview.total)}</span>
              </div>
            </div>

            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs text-muted-foreground">
                    <th className="py-2 pr-4 font-medium">{t('wealth.buckets.bucket')}</th>
                    <th className="py-2 pr-4 font-medium">{t('wealth.buckets.source')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('wealth.buckets.balance')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('wealth.buckets.share')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('wealth.buckets.monthlyRate')}</th>
                    <th className="py-2 pr-4 text-right font-medium">{t('wealth.buckets.forecast')}</th>
                    <th className="py-2" aria-hidden="true" />
                  </tr>
                </thead>
                <tbody>
                  {overview.buckets.map((bucket, index) => (
                    <BucketRow
                      key={bucket.id}
                      bucket={bucket}
                      index={index}
                      onEdit={openEdit}
                      onRemove={confirmRemove}
                      removing={deleteBucket.isPending}
                    />
                  ))}
                  <tr className="font-bold">
                    <td className="py-2 pr-4">{t('wealth.buckets.total')}</td>
                    <td className="py-2 pr-4" />
                    <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(overview.total)}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(100)}</td>
                    <td className="py-2 pr-4 text-right tabular-nums">
                      {formatCHF(overview.totalMonthlyRate)}
                    </td>
                    <td className="py-2 pr-4 text-right tabular-nums">
                      {formatCHF(overview.totalForecastYearEnd)}
                    </td>
                    <td className="py-2" />
                  </tr>
                </tbody>
              </table>
            </div>
          </>
        )}
      </CardContent>

      {formOpen && (
        <BucketFormDialog
          key={editing?.id ?? 'create'}
          bucket={editing}
          onClose={() => setFormOpen(false)}
        />
      )}
    </Card>
  );
}
