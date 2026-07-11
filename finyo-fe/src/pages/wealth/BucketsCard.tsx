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

function SourceBadge({ source }: Readonly<{ source: WealthSource }>) {
  const { t } = useTranslation();

  return source === 'PORTFOLIO' ? (
    <Badge variant="secondary">{t('wealth.buckets.sourcePortfolio')}</Badge>
  ) : (
    <Badge variant="outline">{t('wealth.buckets.sourceManual')}</Badge>
  );
}

function BucketRow({ bucket, index, onEdit, onRemove, removing }: Readonly<{
  bucket: WealthBucket;
  index: number;
  onEdit: (bucket: WealthBucket) => void;
  onRemove: (bucket: WealthBucket) => void;
  removing: boolean;
}>) {
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
            <p className="truncate font-medium">{bucket.name}</p>
            {bucket.note && (
              <p className="truncate text-xs text-muted-foreground">{bucket.note}</p>
            )}
          </div>
        </div>
      </td>
      <td className="py-2 pr-4">
        <SourceBadge source={bucket.source} />
      </td>
      <td className="py-2 pr-4 text-right font-medium tabular-nums">{formatCHF(bucket.balance)}</td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatPercent(bucket.sharePct)}</td>
      <td className="py-2 pr-4 text-right tabular-nums">
        {bucket.monthlyRate === 0 ? '—' : formatCHF(bucket.monthlyRate)}
      </td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(bucket.forecastYearEnd)}</td>
      <td className="py-2 text-right">
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
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
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
          <div className="overflow-x-auto">
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
