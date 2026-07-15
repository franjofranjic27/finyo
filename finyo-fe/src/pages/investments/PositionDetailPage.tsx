import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useBreadcrumb } from '@/components/layout/BreadcrumbContext';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import { positionDetailApi } from '@/api/positionDetail';
import type { PositionDetail } from '@/api/positionDetail';
import { formatCHF, formatDate, formatPercent, amountColour } from '@/lib/formatters';
import { EditInstrumentDialog } from './EditInstrumentDialog';
import { EditPositionDialog } from './EditPositionDialog';
import { FactsheetCard } from './FactsheetCard';
import { PriceSourceBadge } from './PriceSourceBadge';

function StatTile({ label, value, valueClass }: Readonly<{
  label: string;
  value: string;
  valueClass?: string;
}>) {
  return (
    <Card>
      <CardContent className="pt-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`mt-1 text-xl font-bold tabular-nums ${valueClass ?? 'text-foreground'}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  );
}

function InfoRow({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border py-2 text-sm last:border-0">
      <dt className="shrink-0 text-muted-foreground">{label}</dt>
      <dd className="min-w-0 truncate text-right font-medium">{value}</dd>
    </div>
  );
}

function ProductInfoCard({ position, onEdit }: Readonly<{
  position: PositionDetail;
  onEdit: () => void;
}>) {
  const { t, i18n } = useTranslation();

  // An unknown currency stays unknown — defaulting to CHF would invent a fact.
  const currency = position.currency ?? t('investments.price.unknownCurrency');
  const priceAsOf = position.priceAsOf
    ? formatDate(position.priceAsOf, i18n.language)
    : t('investments.price.asOfUnknown');

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">{t('investments.position.product.title')}</CardTitle>
        <Button variant="ghost" size="sm" onClick={onEdit}>
          {t('common.edit')}
        </Button>
      </CardHeader>
      <CardContent>
        <dl>
          <InfoRow label={t('investments.position.product.name')} value={position.name} />
          <InfoRow
            label={t('investments.position.product.assetClass')}
            value={t(`assetClass.${position.assetClass}`)}
          />
          <InfoRow label={t('investments.position.product.valor')} value={position.valor ?? '—'} />
          <InfoRow label={t('investments.position.product.isin')} value={position.isin ?? '—'} />
          <InfoRow label={t('investments.position.product.currency')} value={currency} />
          <InfoRow
            label={t('investments.position.product.ter')}
            value={position.ter != null ? `${position.ter.toFixed(2)} %` : '—'}
          />
          <InfoRow
            label={t('investments.position.product.priceSource')}
            value={t(`investments.position.priceSourceValue.${position.priceSource}`)}
          />
          {position.priceSource !== 'PURCHASE' && (
            <InfoRow label={t('investments.position.product.priceAsOf')} value={priceAsOf} />
          )}
          <InfoRow
            label={t('investments.position.product.portfolioShare')}
            value={formatPercent(position.portfolioShare)}
          />
        </dl>
      </CardContent>
    </Card>
  );
}

function PositionDetailSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-16 w-full" />
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-5">
        {['value', 'quantity', 'avg', 'price', 'gain'].map((key) => (
          <Skeleton key={key} className="h-20 w-full" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-72 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    </div>
  );
}

export function PositionDetailPage() {
  const { t } = useTranslation();
  const { positionId = '' } = useParams();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);
  const [editPositionOpen, setEditPositionOpen] = useState(false);

  const { data: position, isLoading, isError } = useQuery({
    queryKey: ['position', positionId],
    queryFn: () => positionDetailApi.getPosition(token, positionId),
    enabled: !!token && !!positionId,
  });

  useBreadcrumb([
    { label: t('breadcrumb.portfolio'), to: '/investments' },
    { label: t('breadcrumb.position', { name: position?.name ?? '…' }) },
  ]);

  const deletePosition = useMutation({
    mutationFn: () => portfolioApi.deletePosition(token, positionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      navigate('/investments');
    },
  });

  const confirmDelete = () => {
    if (!position) return;
    if (globalThis.confirm(t('investments.position.confirmDelete', { name: position.name }))) {
      deletePosition.mutate();
    }
  };

  if (isLoading) return <PositionDetailSkeleton />;
  if (isError || !position) {
    return <p className="text-sm text-destructive">{t('investments.position.loadError')}</p>;
  }

  const gainLossValue = `${formatCHF(position.gainLoss)} · ${formatPercent(position.returnPercent)}`;

  return (
    <div className="space-y-6">
      {/* Instrument header */}
      <div className="space-y-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-semibold">{position.name}</h2>
            <Badge variant="secondary">{t(`assetClass.${position.assetClass}`)}</Badge>
            <PriceSourceBadge position={position} />
          </div>
          <Button variant="ghost" size="sm" onClick={() => setEditPositionOpen(true)}>
            <Pencil className="mr-1 h-4 w-4" aria-hidden="true" />
            {t('investments.position.editPositionDialog.title')}
          </Button>
        </div>
        <p className="text-sm text-muted-foreground">
          {[position.isin, position.currency].filter(Boolean).join(' · ')}
        </p>
      </div>

      {/* Stat tiles */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-5">
        <StatTile label={t('investments.position.stats.value')} value={formatCHF(position.value)} />
        <StatTile
          label={t('investments.position.stats.quantity')}
          value={String(position.quantity)}
        />
        <StatTile
          label={t('investments.position.stats.avgPurchasePrice')}
          value={formatCHF(position.avgPurchasePrice)}
        />
        <StatTile
          label={t('investments.position.stats.currentPrice')}
          value={formatCHF(position.currentPrice)}
        />
        <StatTile
          label={t('investments.position.stats.gainLossReturn')}
          value={gainLossValue}
          valueClass={amountColour(position.gainLoss)}
        />
      </div>

      {/* Product info + factsheet */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ProductInfoCard position={position} onEdit={() => setEditOpen(true)} />
        <FactsheetCard position={position} />
      </div>

      {/* Danger zone */}
      <div className="flex justify-end">
        <Button
          variant="ghost"
          className="text-destructive hover:text-destructive"
          onClick={confirmDelete}
          disabled={deletePosition.isPending}
        >
          {t('investments.position.deletePosition')}
        </Button>
      </div>

      {editOpen && <EditInstrumentDialog position={position} onClose={() => setEditOpen(false)} />}
      {editPositionOpen && (
        <EditPositionDialog
          positionId={position.positionId}
          initial={{
            quantity: position.quantity,
            purchasePrice: position.avgPurchasePrice,
            purchaseDate: position.purchaseDate,
            currentPrice: position.currentPrice,
          }}
          onClose={() => setEditPositionOpen(false)}
        />
      )}
    </div>
  );
}
